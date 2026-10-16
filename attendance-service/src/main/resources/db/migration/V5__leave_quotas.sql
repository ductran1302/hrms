-- Quỹ phép (GĐ1: quota thuần — chính sách + ledger entitlement; chưa có đơn nghỉ phép).
-- Đơn vị: ngày, numeric để hỗ trợ thập phân chính xác (0.1, 0.125...) — không dùng float/double.
create table leave_policies
(
    id                   uuid primary key default gen_random_uuid(),
    year                 int           not null unique,
    base_days            numeric(8, 4) not null default 12,
    seniority_step_years int           not null default 1,
    seniority_bonus_days numeric(8, 4) not null default 1,
    seniority_max_bonus  numeric(8, 4) not null default 5,
    carryover_max        numeric(8, 4) not null default 5,
    created_at           timestamptz   not null,
    updated_at           timestamptz   not null
);

-- Quota theo chức vụ — match employees.position theo tên (position là free-text, không FK).
create table leave_position_quotas
(
    id            uuid primary key default gen_random_uuid(),
    policy_id     uuid          not null references leave_policies (id) on delete cascade,
    position_name varchar(100)  not null,
    bonus_days    numeric(8, 4) not null,
    unique (policy_id, position_name)
);

-- Ledger cấp phép: mỗi nguồn 1 dòng (BASE/SENIORITY/POSITION auto; BONUS/CARRYOVER/ADJUSTMENT do HR).
create table leave_entitlements
(
    id          uuid primary key default gen_random_uuid(),
    employee_id varchar(64)    not null references employees (employee_id),
    year        int            not null,
    source      varchar(20)    not null, -- BASE | SENIORITY | POSITION | BONUS | CARRYOVER | ADJUSTMENT
    days        numeric(8, 4)  not null,
    reason      varchar(300),
    granted_by  varchar(100),
    created_at  timestamptz    not null,
    constraint ck_leave_ent_days check (days <> 0)
);

create index idx_leave_ent_employee_year on leave_entitlements (employee_id, year);

-- Các nguồn auto chỉ có tối đa 1 dòng/(NV, năm, nguồn) — ensure/update tại chỗ khi recalc.
create unique index uk_leave_ent_auto
    on leave_entitlements (employee_id, year, source)
    where source in ('BASE', 'SENIORITY', 'POSITION');
