package com.hrms.identity.employee;

import com.hrms.common.exception.BusinessRuleException;
import java.text.Normalizer;
import java.util.Locale;

/**
 * Sinh mã nhân viên từ họ tên — đồng thời là username đăng nhập Keycloak.
 *
 * <pre>
 *.Quy tắc: chữ cái đầu MỌI từ trước "tên chính" + tên chính (bỏ dấu, thường),
 *   Nguyễn Trung Hiếu   → n + t + hieu     = nthieu
 *   Nguyễn Thị Thanh Hà → n + t + t + ha    = nttha
 *   Trần Dũng           → t + dung          = tdung
 * Trùng thì thêm hậu tố: nthieu1, nthieu2, ... (tối đa 20 ký tự gồm hậu tố).
 * </pre>
 */
public final class EmployeeCodeGenerator {

  static final int MAX_LENGTH = 20;

  private EmployeeCodeGenerator() {
  }

  /** Sinh base code (chưa cộng hậu tố) từ họ tên. */
  public static String baseFrom(String fullName) {
    if (fullName == null || fullName.isBlank()) {
      throw new BusinessRuleException("Thiếu họ tên để sinh mã nhân viên");
    }
    String[] words = fullName.trim().replaceAll("\\s+", " ").split(" ");
    StringBuilder initials = new StringBuilder();
    for (int i = 0; i < words.length - 1; i++) {
      String ascii = strip(words[i]);
      if (!ascii.isEmpty()) {
        initials.append(ascii.charAt(0));
      }
    }
    String base = initials + strip(words[words.length - 1]);
    base = base.replaceAll("[^a-z0-9]", "");
    if (base.isEmpty()) {
      base = "nv";
    }
    return base;
  }

  /** Cộng hậu tố thứ tự (attempt ≥ 1), cắt base nếu vượt 20 ký tự. */
  public static String withSuffix(String base, int attempt) {
    String suffix = String.valueOf(attempt);
    String head = base.length() + suffix.length() > MAX_LENGTH
        ? base.substring(0, MAX_LENGTH - suffix.length())
        : base;
    return head + suffix;
  }

  /** Bỏ dấu tiếng Việt + thường hóa ("Đường" → "duong"). */
  private static String strip(String word) {
    String normalized = Normalizer.normalize(word, Normalizer.Form.NFD)
        .replaceAll("\\p{M}+", "")
        .replace('đ', 'd')
        .replace('Đ', 'D');
    return normalized.toLowerCase(Locale.ROOT);
  }
}
