-- Đơn đăng ký OT master + chi tiết từng ngày.
create table ot_requests (
    id              uuid        primary key default gen_random_uuid(),
    request_code    varchar(30) unique not null,     -- OT20261029001
    employee_id     varchar(64) not null references employees(employee_id),
    employee_code   varchar(50) not null,
    full_name       varchar(200),
    department_id   uuid,

    reason          text        not null,
    total_hours     numeric(6,2) not null default 0.00,
    status          varchar(20) not null default 'PENDING',
    approver_id     varchar(64),
    approved_at     timestamptz,
    rejection_reason text,
    cancelled_at    timestamptz,
    cancellation_reason text,

    created_by      varchar(100),
    created_at      timestamptz not null default current_timestamp,
    updated_by      varchar(100),
    updated_at      timestamptz not null default current_timestamp,
    version         bigint      not null default 0
);

create index idx_ot_requests_employee   on ot_requests(employee_id);
create index idx_ot_requests_status     on ot_requests(status);
create index idx_ot_requests_dept       on ot_requests(department_id);
create index idx_ot_requests_approver   on ot_requests(approver_id);

-- Chi tiết từng ngày của đơn OT.
create table ot_request_items (
    id              uuid        primary key default gen_random_uuid(),
    request_id      uuid        not null references ot_requests(id) on delete cascade,
    ot_date         date        not null,

    start_time      timestamptz not null,
    end_time        timestamptz not null,
    day_type        varchar(20) not null,           -- WEEKDAY,WEEKEND,HOLIDAY

    day_hours       numeric(5,2) default 0.00,
    night_hours     numeric(5,2) default 0.00,

    actual_start_time timestamptz,
    actual_end_time   timestamptz,
    approved_hours   numeric(5,2) default 0.00,

    created_at      timestamptz not null default current_timestamp,
    updated_at      timestamptz not null default current_timestamp,
    version         bigint      not null default 0
);

create unique index uq_ot_items_request_date on ot_request_items(request_id, ot_date);