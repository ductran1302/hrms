-- Tham số global tính quỹ phép (1 dòng, service self-heal nếu thiếu).
-- A. Năm tài chính: từ fy_start_month/fy_start_day của năm Y đến ngày trước mốc năm sau.
-- A. Kỳ công: kỳ = [period_start_day/M ... period_end_day], period_end_day < period_start_day
--   → thuộc tháng M+1 (vd 26→25); validate liên tục ở service (kỳ sau = ngày sau kỳ trước).
-- A. Ngày làm việc: T2-T6 luôn; CN nghỉ; T7 theo saturday_policy (OFF|HALF|ALTERNATING|ALL).
--   Ngưỡng % ngày công tối thiểu để hưởng phép kỳ (x%, mặc định 50) — global.
create table leave_settings
(
    id                 uuid primary key default gen_random_uuid(),
    fy_start_month     int           not null default 1,  -- 1..12
    fy_start_day       int           not null default 1,  -- 1..28
    period_start_day   int           not null default 1,  -- 1..28
    period_end_day     int           not null default 31, -- 1..31
    saturday_policy    varchar(20)   not null default 'OFF', -- OFF | HALF | ALTERNATING | ALL
    min_work_percent   numeric(5, 2) not null default 50,   -- 0..100
    created_at         timestamptz   not null,
    updated_at         timestamptz   not null
);
