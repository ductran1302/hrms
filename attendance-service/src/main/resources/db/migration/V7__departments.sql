-- Projection phòng ban trong attendance_db — đồng bộ từ identity qua topic
-- hrms.department.v1 (created/updated). Dùng cho scope DEPT_LEAD (subtree phòng ban).
create table departments (
    department_id varchar(64) primary key,
    code varchar(50) not null,
    name varchar(100) not null,
    parent_id varchar(64),
    manager_id varchar(64),
    status varchar(20) not null default 'ACTIVE',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index idx_departments_parent_id on departments(parent_id);
create index idx_departments_manager_id on departments(manager_id);
