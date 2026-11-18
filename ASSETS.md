# ASSETS — Nguồn gốc tài sản hình ảnh

Tài liệu này ghi nguồn gốc/license của asset hình ảnh được commit trong repo.
Asset phát sinh từ dependency (icon, font của thư viện) theo license của package
tương ứng — xem `THIRD-PARTY-FRONTEND.txt`.

## Asset trong repo

| Asset | Đường dẫn | Kích thước | Nguồn / license | Trạng thái |
|---|---|---|---|---|
| Logo favicon | `keycloak/themes/hrms/login/resources/img/favicon.svg` | SVG 48×48 | Tự tạo trong dự án (hình lá/cánh, màu `#0e2c56`/`#3b82f6`) | OK — tài sản của dự án |
| Ảnh nền trang đăng nhập | `keycloak/themes/hrms/login/resources/img/login-hero.jpg` | JPEG 1024×1024 | **Chưa xác định**; không có metadata nguồn/tác giả | **Cần xử lý trước phát hành thương mại** |

### Hash kiểm tra

```text
SHA256  keycloak/themes/hrms/login/resources/img/favicon.svg
        7846CD2DE081DB8C316096A2EE35D1EF6F1FD8A5000D8CD5D2C4AB6A8EC76A46

SHA256  keycloak/themes/hrms/login/resources/img/login-hero.jpg
        EF45A4A3CDB82328ABF3300E9EF2E860313EB2E29AFA4E8C9C6920FEB68378C0
```

## Việc cần làm với `login-hero.jpg`

Chọn một hướng và ghi lại bằng chứng vào file này:

1. Thay bằng ảnh do dự án tự tạo/chụp → ghi tác giả, ngày, license nội bộ.
2. Thay bằng ảnh AI tạo → ghi công cụ, ngày, điều khoản cho phép dùng thương mại.
3. Mua/được cấp phép → lưu license/hoá đơn trong `LICENSES/` hoặc `docs/assets/`.
4. Không xác định được → **xóa/thay ảnh** trước khi phát hành.

## Quy tắc thêm asset mới

- Chỉ commit ảnh có nguồn rõ ràng; ghi vào bảng trên kèm hash SHA-256.
- Không dùng ảnh/icon/font tải từ CDN hoặc Google Fonts nếu chưa kiểm tra license.
- Icon UI lấy từ `@ant-design/icons` (MIT) thông qua npm — không copy file thủ công.
- Font dùng font hệ điều hành/Keycloak theme, không nhúng font ngoài.
