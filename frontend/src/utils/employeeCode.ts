/**
 * Sinh gợi ý mã nhân viên từ họ tên — mirror BE EmployeeCodeGenerator:
 * chữ cái đầu mọi từ trước "tên chính" + tên chính (bỏ dấu, thường).
 * Nguyễn Trung Hiếu → nthieu; Nguyễn Thị Thanh Hà → nttha; Trần Dũng → tdung.
 */
export function generateEmployeeCode(fullName: string): string {
  const words = fullName.trim().replace(/\s+/g, ' ').split(' ').filter(Boolean);
  if (words.length === 0) return '';
  const strip = (word: string) =>
    word
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .replace(/đ/g, 'd')
      .replace(/Đ/g, 'D')
      .toLowerCase();
  const initials = words
    .slice(0, -1)
    .map((word) => strip(word).charAt(0) ?? '')
    .join('');
  const base = (initials + strip(words[words.length - 1])).replace(/[^a-z0-9]/g, '');
  return base || 'nv';
}
