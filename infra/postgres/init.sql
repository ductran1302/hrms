-- Tạo database riêng cho từng microservice (không share DB).
-- Script này chỉ chạy lần đầu khi volume postgres trống.
CREATE DATABASE identity_db;
CREATE DATABASE attendance_db;
CREATE DATABASE payroll_db;
CREATE DATABASE notification_db;
