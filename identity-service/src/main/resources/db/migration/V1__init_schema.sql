create table employees
(
    id              uuid         not null primary key,
    employee_code   varchar(20)  not null,
    full_name       varchar(200) not null,
    email           varchar(200),
    department_id   varchar(64),
    department_name varchar(100),
    position        varchar(100),
    manager_id      varchar(64),
    status          varchar(20)  not null,
    hired_at        timestamptz,
    created_at      timestamptz  not null,
    updated_at      timestamptz  not null,
    version         bigint       not null,
    constraint uq_employees_code unique (employee_code)
);

create index idx_employees_status on employees (status);
create index idx_employees_department on employees (department_id);

create table outbox_events
(
    id            uuid         not null primary key,
    aggregate_type varchar(100) not null,
    aggregate_id  varchar(64)  not null,
    event_type    varchar(100) not null,
    topic         varchar(200) not null,
    payload       text         not null,
    partition_key varchar(64),
    status        varchar(20)  not null,
    attempts      integer      not null default 0,
    published_at  timestamptz,
    last_error    varchar(1000),
    created_at    timestamptz  not null,
    updated_at    timestamptz  not null,
    version       bigint       not null default 0
);

create index idx_outbox_status_created on outbox_events (status, created_at);
