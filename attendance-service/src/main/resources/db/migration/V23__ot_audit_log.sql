-- Audit log cho thao tác OT (theo chuẩn dự án: audit_log riêng per-service).
-- Gắn các hành động nhạy cảm của OT vào bảng audit_log đã tồn tại.
-- Chúng ta chỉ cần đảm bảo bảng audit_log được scan trong PersistenceConfig (AGENTS §4.19/§4.20).
-- Action types sẽ được khai báo trong ErrorCode/ActionCode enum.

-- (Migration này là placeholder để cập nhật PersistenceConfig scan audit nếu chưa có.)
-- Bảng audit_log đã tồn tại từ V17__audit_log.sql.