-- Phase 1 Shift & Calendar Engine.
-- 1) Mở ca qua nửa đêm (end < start) — crosses_midnight suy ra từ giờ ca.
-- 2) 3 tầng lịch: work_schedule_policies -> employee_schedule_assignments
--    -> employee_shift_rosters (work_date = NGÀY BẮT ĐẦU ca, ca đêm không đổi ngày).

-- ---- work_shifts: ca qua đêm -------------------------------------------------

alter table work_shifts add column crosses_midnight boolean not null default false;
update work_shifts set crosses_midnight = (end_work_time < start_work_time);

alter table work_shifts drop constraint ck_work_shifts_span;
alter table work_shifts add constraint ck_work_shifts_span
  check (start_work_time <> end_work_time);
alter table work_shifts add constraint ck_work_shifts_cross
  check (crosses_midnight = (end_work_time < start_work_time));

-- Khung nghỉ chỉ cần đi kèm nhau; vị trí trong ca (kể cả wrap qua 0h) validate ở service.
alter table work_shifts drop constraint ck_work_shifts_break;
alter table work_shifts add constraint ck_work_shifts_break
  check (
    (start_break_time is null and end_break_time is null)
    or (start_break_time is not null and end_break_time is not null
        and start_break_time <> end_break_time)
  );

-- ---- Policy tuần (mẫu lịch) ------------------------------------------------

create table work_schedule_policies
(
    id                     uuid primary key     default gen_random_uuid(),
    name                   varchar(100) not null,
    description            varchar(500),
    mon_shift_id           uuid references work_shifts (id),
    tue_shift_id           uuid references work_shifts (id),
    wed_shift_id           uuid references work_shifts (id),
    thu_shift_id           uuid references work_shifts (id),
    fri_shift_id           uuid references work_shifts (id),
    sat_shift_id           uuid references work_shifts (id),
    -- Tuần lẻ của T7 luân phiên; null = tuần lẻ nghỉ. Chỉ dùng khi rotation_anchor_date có.
    sat_alternate_shift_id uuid references work_shifts (id),
    sun_shift_id           uuid references work_shifts (id),
    -- Anchor parity (phải là Thứ 7): tuần chẵn/lẻ tính từ mốc này.
    rotation_anchor_date   date,
    is_default             boolean     not null default false,
    active                 boolean     not null default true,
    created_at             timestamptz not null,
    updated_at             timestamptz not null,
    version                bigint      not null default 0,
    constraint uq_work_schedule_policies_name unique (name),
    constraint ck_wsp_rotation_anchor
        check (sat_alternate_shift_id is null or rotation_anchor_date is not null)
);

create unique index uq_wsp_default on work_schedule_policies (is_default) where is_default;
create index idx_wsp_active on work_schedule_policies (active);

-- ---- Gán policy cho nhân viên (effective-dated) ----------------------------

create table employee_schedule_assignments
(
    id             uuid primary key default gen_random_uuid(),
    employee_id    varchar(64) not null,
    policy_id      uuid        not null references work_schedule_policies (id),
    effective_from date        not null,
    assigned_by    varchar(100),
    created_at     timestamptz not null,
    updated_at     timestamptz not null,
    version        bigint      not null default 0,
    -- DEFERRABLE: cho phép re-key PK employees (Kafka tạo lại id) trong cùng transaction.
    constraint fk_esa_employee foreign key (employee_id)
        references employees (employee_id) deferrable initially deferred,
    constraint uq_esa_emp_from unique (employee_id, effective_from)
);

create index idx_esa_emp_from on employee_schedule_assignments (employee_id, effective_from desc);
create index idx_esa_policy on employee_schedule_assignments (policy_id);

-- ---- Roster sinh sẵn (cache) ----------------------------------------------

create table employee_shift_rosters
(
    id          uuid primary key default gen_random_uuid(),
    employee_id varchar(64) not null,
    work_date   date        not null,
    shift_id    uuid references work_shifts (id),
    is_day_off  boolean     not null,
    -- POLICY_AUTO | ROTATION_GEN | MANUAL_OVERRIDE | LEGACY_ASSIGNMENT
    source      varchar(20) not null,
    policy_id   uuid references work_schedule_policies (id),
    note        varchar(500),
    created_at  timestamptz not null,
    updated_at  timestamptz not null,
    version     bigint      not null default 0,
    constraint fk_esr_employee foreign key (employee_id)
        references employees (employee_id) deferrable initially deferred,
    constraint uq_esr_emp_date unique (employee_id, work_date),
    constraint ck_esr_off_shift
        check ((is_day_off and shift_id is null) or (not is_day_off and shift_id is not null))
);

create index idx_esr_work_date on employee_shift_rosters (work_date);
create index idx_esr_emp_date on employee_shift_rosters (employee_id, work_date);
