-- Ca làm việc (GĐ1: quản lý định nghĩa ca + gán NV theo ngày hiệu lực).
-- Tính công nằm ở module chấm công sau (spec đọc README).
-- working_time/breaking_time lưu PHÚT (int) — chính xác tuyệt đối, không float.
create table work_shifts
(
    id                uuid primary key default gen_random_uuid(),
    name              varchar(100) not null,
    shift_type        varchar(20)  not null, -- FIXED | FLEXIBLE
    start_work_time   time         not null,
    end_work_time     time         not null,
    start_break_time  time,
    end_break_time    time,
    working_time      int          not null, -- phút làm việc để đủ 1 công
    breaking_time     int          not null default 0, -- phút nghỉ giữa ca
    active            boolean      not null default true,
    created_at        timestamptz  not null,
    updated_at        timestamptz  not null,
    constraint uq_work_shifts_name unique (name),
    constraint ck_work_shifts_span check (start_work_time < end_work_time),
    constraint ck_work_shifts_break check (
        (start_break_time is null and end_break_time is null)
        or (start_break_time is not null and end_break_time is not null
            and start_break_time < end_break_time
            and start_break_time >= start_work_time
            and end_break_time <= end_work_time)
    )
);

-- Gán ca cho từng NV — lịch sử theo ngày hiệu lực (mỗi NV 1 bản gán/năm-ngày duy nhất).
create table shift_assignments
(
    id             uuid primary key default gen_random_uuid(),
    employee_id    varchar(64) not null references employees (employee_id),
    work_shift_id  uuid        not null references work_shifts (id),
    effective_from date        not null,
    assigned_by    varchar(100),
    created_at     timestamptz not null,
    updated_at     timestamptz not null,
    constraint uq_shift_assign_emp_date unique (employee_id, effective_from)
);

create index idx_shift_assign_emp_from on shift_assignments (employee_id, effective_from desc);
