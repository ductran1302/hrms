-- ============================================================
-- Bước 9: identity-core — phòng ban, hợp đồng, job history,
-- hồ sơ nhân viên mở rộng, FK org-chart (manager là employee).
-- ============================================================

-- 1. Departments -------------------------------------------------
create table departments
(
    id         uuid         not null primary key,
    code       varchar(20)  not null,
    name       varchar(100) not null,
    parent_id  uuid,
    manager_id uuid,
    status     varchar(20)  not null,
    created_at timestamptz  not null,
    updated_at timestamptz  not null,
    version    bigint       not null default 0,
    constraint uq_departments_code unique (code),
    constraint fk_departments_parent foreign key (parent_id) references departments (id),
    constraint fk_departments_manager foreign key (manager_id) references employees (id)
);

create index idx_departments_parent on departments (parent_id);
create index idx_departments_status on departments (status);

-- 2. Contracts ---------------------------------------------------
create table contracts
(
    id            uuid         not null primary key,
    employee_id   uuid         not null,
    contract_type varchar(30)  not null,
    start_date    date         not null,
    end_date      date,
    position      varchar(100),
    salary        bigint,
    sign_date     date,
    note          varchar(500),
    status        varchar(20)  not null,
    created_at    timestamptz  not null,
    updated_at    timestamptz  not null,
    version       bigint       not null default 0,
    constraint fk_contracts_employee foreign key (employee_id) references employees (id)
);

create index idx_contracts_employee on contracts (employee_id);
create index idx_contracts_status on contracts (status);
create index idx_contracts_end_date on contracts (end_date);

-- 3. Job history (lịch sử thay đổi job info theo SF4C/SAP EC) ----
create table employee_job_history
(
    id             uuid         not null primary key,
    employee_id    uuid         not null,
    effective_date timestamptz  not null,
    field          varchar(50)  not null,
    old_value      varchar(500),
    new_value      varchar(500),
    actor_id       varchar(64),
    created_at     timestamptz  not null,
    updated_at     timestamptz  not null,
    version        bigint       not null default 0,
    constraint fk_job_history_employee foreign key (employee_id) references employees (id)
);

create index idx_job_history_employee on employee_job_history (employee_id, effective_date desc);

-- 4. Employees: hồ sơ mở rộng (personal info) --------------------
alter table employees add column birth_date date;
alter table employees add column gender varchar(10);
alter table employees add column phone varchar(20);
alter table employees add column national_id varchar(20);
alter table employees add column address varchar(300);

-- 5. Employees: department_id varchar(64) -> uuid + FK ----------
--    (dữ liệu hiện tại toàn NULL — mapper ignore khi create)
update employees
set department_id = null
where department_id is not null
  and department_id !~ '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$';

alter table employees alter column department_id type uuid using department_id::uuid;
alter table employees
    add constraint fk_employees_department foreign key (department_id) references departments (id);

-- 6. Employees: manager_id varchar -> uuid self-FK (org chart) ---
--    (data cũ là username Keycloak như "manager1" — không link được → nullify)
update employees
set manager_id = null
where manager_id is not null
  and manager_id !~ '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$';

alter table employees alter column manager_id type uuid using manager_id::uuid;
alter table employees
    add constraint fk_employees_manager foreign key (manager_id) references employees (id);
