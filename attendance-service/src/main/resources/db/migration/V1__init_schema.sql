-- Projection nhân sự đọc từ Kafka (mirror của identity, per-service DB — không join chéo)
create table employees
(
    employee_id        varchar(64)  not null primary key,
    employee_code      varchar(20)  not null,
    full_name          varchar(200) not null,
    email              varchar(200),
    department_id      varchar(64),
    department_name    varchar(100),
    position           varchar(100),
    manager_id         varchar(64),
    status             varchar(20)  not null,
    hired_at           timestamptz,
    terminated_at      timestamptz,
    termination_reason varchar(500),
    created_at         timestamptz  not null,
    updated_at         timestamptz  not null,
    constraint uq_attendance_emp_code unique (employee_code)
);

create index idx_attendance_employees_status on employees (status);

-- Idempotency: PK = eventId — retry/replay cùng event chỉ xử lý 1 lần
create table processed_events
(
    event_id     varchar(64)  not null primary key,
    event_type   varchar(100) not null,
    processed_at timestamptz  not null
);
