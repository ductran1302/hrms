-- Sửa type cột year: entity dùng int (INTEGER) nhưng V8 tạo smallint → ddl-auto validate fail.
alter table holidays alter column year type integer using year::integer;
