package com.hrms.identity.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hrms.common.exception.BusinessRuleException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

class DocumentContentValidatorTest {

  private static final byte[] PDF = "%PDF-1.7\nnội dung".getBytes(StandardCharsets.UTF_8);
  private static final byte[] ZIP = {'P', 'K', 0x03, 0x04, 0x14, 0x00, 0x00, 0x00};
  private static final byte[] OLE = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0,
      (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1, 0x00};

  private static MultipartFile file(String name, String clientType, byte[] content) {
    return new MockMultipartFile("file", name, clientType, content);
  }

  @Test
  void validate_pdf_returnsNormalizedContentType() {
    DocumentContentValidator.ValidatedDocument result =
        DocumentContentValidator.validate(file("cv.PDF", "text/html", PDF));

    assertThat(result.contentType()).isEqualTo("application/pdf");
    assertThat(result.extension()).isEqualTo("pdf");
  }

  @Test
  void validate_docxWithZipMagic_isAccepted() {
    DocumentContentValidator.ValidatedDocument result =
        DocumentContentValidator.validate(file("cv.docx", null, ZIP));

    assertThat(result.contentType())
        .isEqualTo("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
  }

  @Test
  void validate_legacyDocWithOleMagic_isAccepted() {
    assertThat(DocumentContentValidator.validate(file("cv.doc", null, OLE)).contentType())
        .isEqualTo("application/msword");
  }

  @Test
  void validate_textAndCsv_areAccepted() {
    assertThat(DocumentContentValidator.validate(
        file("notes.txt", "text/plain", "xin chào".getBytes(StandardCharsets.UTF_8))).contentType())
        .isEqualTo("text/plain");
    assertThat(DocumentContentValidator.validate(
        file("data.csv", "text/csv", "a,b\n1,2\n".getBytes(StandardCharsets.UTF_8))).contentType())
        .isEqualTo("text/csv");
  }

  @Test
  void validate_rtf_isAccepted() {
    assertThat(DocumentContentValidator.validate(
        file("cv.rtf", "application/rtf", "{\\rtf1 hello}".getBytes(StandardCharsets.US_ASCII)))
        .contentType()).isEqualTo("application/rtf");
  }

  @Test
  void validate_webpIsAccepted() {
    byte[] webp = {'R', 'I', 'F', 'F', 0x24, 0x00, 0x00, 0x00, 'W', 'E', 'B', 'P', 'V', 'P', '8', ' '};
    assertThat(DocumentContentValidator.validate(file("a.webp", "image/webp", webp)).contentType())
        .isEqualTo("image/webp");
  }

  @Test
  void validate_pdfExtensionWithHtmlContent_isRejected() {
    assertThatThrownBy(() -> DocumentContentValidator.validate(
        file("cv.pdf", "application/pdf", "<html><script>alert(1)</script></html>"
            .getBytes(StandardCharsets.UTF_8))))
        .isInstanceOf(BusinessRuleException.class)
        .hasMessageContaining("không khớp định dạng .pdf");
  }

  @Test
  void validate_docxExtensionWithOleContent_isRejected() {
    assertThatThrownBy(() -> DocumentContentValidator.validate(file("cv.docx", null, OLE)))
        .isInstanceOf(BusinessRuleException.class)
        .hasMessageContaining("không khớp định dạng .docx");
  }

  @Test
  void validate_executableExtension_isRejected() {
    assertThatThrownBy(() -> DocumentContentValidator.validate(
        file("virus.exe", "application/octet-stream", new byte[] {'M', 'Z', 0x00})))
        .isInstanceOf(BusinessRuleException.class)
        .hasMessageContaining("không được phép");
  }

  @Test
  void validate_svg_isRejected() {
    assertThatThrownBy(() -> DocumentContentValidator.validate(
        file("x.svg", "image/svg+xml", "<svg onload=alert(1)>".getBytes(StandardCharsets.UTF_8))))
        .isInstanceOf(BusinessRuleException.class)
        .hasMessageContaining("không được phép");
  }

  @Test
  void validate_noExtension_isRejected() {
    assertThatThrownBy(() -> DocumentContentValidator.validate(file("README", "text/plain",
        "hello".getBytes(StandardCharsets.UTF_8))))
        .isInstanceOf(BusinessRuleException.class)
        .hasMessageContaining("không được phép");
  }

  @Test
  void validate_emptyFile_isRejected() {
    assertThatThrownBy(() -> DocumentContentValidator.validate(file("cv.pdf", "application/pdf",
        new byte[0])))
        .isInstanceOf(BusinessRuleException.class)
        .hasMessageContaining("file rỗng");
  }

  @Test
  void validate_binaryText_isRejected() {
    assertThatThrownBy(() -> DocumentContentValidator.validate(
        file("notes.txt", "text/plain", new byte[] {'a', 0x00, 'b'})))
        .isInstanceOf(BusinessRuleException.class)
        .hasMessageContaining("không khớp định dạng .txt");
  }

  @Test
  void validate_oversizeFile_isRejected() {
    byte[] tooLarge = new byte[(int) DocumentContentValidator.MAX_FILE_SIZE_BYTES + 1];
    tooLarge[0] = '%';
    tooLarge[1] = 'P';
    tooLarge[2] = 'D';
    tooLarge[3] = 'F';
    tooLarge[4] = '-';

    assertThatThrownBy(() -> DocumentContentValidator.validate(file("big.pdf", "application/pdf",
        tooLarge)))
        .isInstanceOf(BusinessRuleException.class)
        .hasMessageContaining("10MB");
  }
}
