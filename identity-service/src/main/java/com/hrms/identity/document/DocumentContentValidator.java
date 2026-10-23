package com.hrms.identity.document;

import com.hrms.common.exception.BusinessRuleException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.web.multipart.MultipartFile;

/**
 * Validate nội dung upload phía server (P0 — FE validation có thể bị bypass).
 *
 * <p>Whitelist extension + kiểm tra magic bytes để chặn file giả mạo (vd {@code .pdf} nhưng
 * nội dung HTML/script) và file thực thi. Content-Type lưu DB được chuẩn hóa theo extension,
 * KHÔNG tin header {@code Content-Type} do client gửi.
 */
final class DocumentContentValidator {

  static final long MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024;

  private static final int HEADER_BYTES = 512;

  private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
  private static final byte[] ZIP = {'P', 'K', 0x03, 0x04};
  private static final byte[] OLE = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0,
      (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1};

  private static final Set<String> ZIP_EXTENSIONS = Set.of("docx", "xlsx", "pptx", "zip");
  private static final Set<String> OLE_EXTENSIONS = Set.of("doc", "xls", "ppt");
  private static final Set<String> TEXT_EXTENSIONS = Set.of("txt", "csv");

  private static final Map<String, String> CONTENT_TYPES = Map.ofEntries(
      Map.entry("pdf", "application/pdf"),
      Map.entry("jpg", "image/jpeg"),
      Map.entry("jpeg", "image/jpeg"),
      Map.entry("png", "image/png"),
      Map.entry("gif", "image/gif"),
      Map.entry("webp", "image/webp"),
      Map.entry("bmp", "image/bmp"),
      Map.entry("doc", "application/msword"),
      Map.entry("docx",
          "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
      Map.entry("xls", "application/vnd.ms-excel"),
      Map.entry("xlsx",
          "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
      Map.entry("ppt", "application/vnd.ms-powerpoint"),
      Map.entry("pptx",
          "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
      Map.entry("txt", "text/plain"),
      Map.entry("csv", "text/csv"),
      Map.entry("rtf", "application/rtf"),
      Map.entry("zip", "application/zip"));

  private DocumentContentValidator() {
  }

  static ValidatedDocument validate(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new BusinessRuleException("Chưa chọn file hoặc file rỗng");
    }
    if (file.getSize() > MAX_FILE_SIZE_BYTES) {
      throw new BusinessRuleException("File vượt quá giới hạn 10MB");
    }
    String extension = extensionOf(file.getOriginalFilename());
    String contentType = CONTENT_TYPES.get(extension);
    if (contentType == null) {
      throw new BusinessRuleException(
          "Định dạng file không được phép. Chỉ nhận PDF, ảnh, Word/Excel/PowerPoint, "
              + "TXT/CSV/RTF, ZIP");
    }
    if (!contentMatches(extension, readHeader(file))) {
      throw new BusinessRuleException(
          "Nội dung file không khớp định dạng ." + extension + " — file có thể bị giả mạo");
    }
    return new ValidatedDocument(contentType, extension);
  }

  private static String extensionOf(String originalName) {
    String name = originalName == null ? "" : originalName;
    int dot = name.lastIndexOf('.');
    if (dot < 0 || dot == name.length() - 1) {
      return "";
    }
    return name.substring(dot + 1)
        .replaceAll("[^a-zA-Z0-9]", "")
        .toLowerCase(Locale.ROOT);
  }

  private static byte[] readHeader(MultipartFile file) {
    try (InputStream input = file.getInputStream()) {
      return input.readNBytes(HEADER_BYTES);
    } catch (IOException ex) {
      throw new BusinessRuleException("Không đọc được nội dung file: " + ex.getMessage());
    }
  }

  private static boolean contentMatches(String extension, byte[] header) {
    if (ZIP_EXTENSIONS.contains(extension)) {
      return startsWith(header, ZIP);
    }
    if (OLE_EXTENSIONS.contains(extension)) {
      return startsWith(header, OLE);
    }
    if (TEXT_EXTENSIONS.contains(extension)) {
      return !hasNulByte(header);
    }
    return switch (extension) {
      case "pdf" -> startsWith(header, "%PDF-");
      case "jpg", "jpeg" -> header.length >= 3
          && (header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8
          && (header[2] & 0xFF) == 0xFF;
      case "png" -> startsWith(header, PNG);
      case "gif" -> startsWith(header, "GIF87a") || startsWith(header, "GIF89a");
      case "webp" -> startsWith(header, "RIFF") && matchesAscii(header, 8, "WEBP");
      case "bmp" -> startsWith(header, "BM");
      case "rtf" -> startsWith(header, "{\\rtf");
      default -> false;
    };
  }

  private static boolean startsWith(byte[] data, String prefix) {
    if (data.length < prefix.length()) {
      return false;
    }
    for (int i = 0; i < prefix.length(); i++) {
      if (data[i] != (byte) prefix.charAt(i)) {
        return false;
      }
    }
    return true;
  }

  private static boolean startsWith(byte[] data, byte[] prefix) {
    if (data.length < prefix.length) {
      return false;
    }
    for (int i = 0; i < prefix.length; i++) {
      if (data[i] != prefix[i]) {
        return false;
      }
    }
    return true;
  }

  private static boolean matchesAscii(byte[] data, int offset, String value) {
    if (data.length < offset + value.length()) {
      return false;
    }
    for (int i = 0; i < value.length(); i++) {
      if (data[offset + i] != (byte) value.charAt(i)) {
        return false;
      }
    }
    return true;
  }

  private static boolean hasNulByte(byte[] data) {
    for (byte value : data) {
      if (value == 0) {
        return true;
      }
    }
    return false;
  }

  /** Kết quả validate: Content-Type chuẩn hóa + extension đã kiểm tra. */
  record ValidatedDocument(String contentType, String extension) {
  }
}
