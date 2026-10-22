-- Ghi chú HR theo từng NV/năm trên màn Quỹ phép.
create table leave_employee_notes
(
    id          uuid primary key default gen_random_uuid(),
    employee_id varchar(64)  not null references employees (employee_id),
    year        int          not null,
    note        varchar(500),
    updated_by  varchar(100),
    created_at  timestamptz  not null,
    updated_at  timestamptz  not null,
    unique (employee_id, year)
);

-- Số ngày công thực tế của NV trong từng kỳ công (nguồn điều kiện x%).
-- Mặc định auto-fill từ ngày vào làm/về khi Tính lại (source=DEFAULT, không đè MANUAL).
create table leave_work_days
(
    id            uuid primary key default gen_random_uuid(),
    employee_id   varchar(64)   not null references employees (employee_id),
    period_start  date          not null,
    period_end    date          not null,
    work_days     numeric(6, 2) not null,
    source        varchar(10)   not null default 'DEFAULT', -- DEFAULT | MANUAL
    updated_by    varchar(100),
    created_at    timestamptz   not null,
    updated_at    timestamptz   not null,
    unique (employee_id, period_start)
);
