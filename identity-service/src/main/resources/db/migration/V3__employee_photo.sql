-- Ảnh nhân viên (avatar) — chỉ đọc (select ra); chỉnh sửa hồ sơ sẽ thêm sau.
ALTER TABLE employees ADD COLUMN IF NOT EXISTS photo_url TEXT;
