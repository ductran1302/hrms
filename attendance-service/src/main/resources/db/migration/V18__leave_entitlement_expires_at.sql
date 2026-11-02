-- Thời hạn hiệu lực quỹ phép: ngày hết hạn của từng dòng ledger.
-- Mặc định = cuối năm tài chính của dòng (fy_start + 1 năm - 1 ngày theo settings);
-- BONUS được cấp tùy chỉnh (có thể vượt năm TC → auto-spill sang năm sau).
-- hết hạn = hôm sau ngày này (dùng trọn ngày expires_at).
alter table leave_entitlements
    add column expires_at date;

-- Backfill dòng cũ theo settings hiện tại (không có settings → default FY 1/1).
-- fy_start_day <= 28 → make_date an toàn tháng 2; date + interval '1 year' kẹp 28/2→28/2
-- giống plusYears của Java.
update leave_entitlements
set expires_at = (make_date(year,
                             coalesce((select fy_start_month from leave_settings limit 1), 1),
                             coalesce((select fy_start_day from leave_settings limit 1), 1))
    + interval '1 year' - interval '1 day')::date;
