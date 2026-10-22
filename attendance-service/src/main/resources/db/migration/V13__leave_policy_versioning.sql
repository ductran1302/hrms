-- Phase 2: effective-dated policy versioning (6 states theo workflow expert).
-- 1 năm tài chính có nhiều version; mỗi version có khoảng hiệu lực [effective_from, effective_to].
-- States: DRAFT → PENDING_APPROVAL → APPROVED → ACTIVE → EXPIRED; CANCELLED (chưa active).

-- Bỏ unique(year) cũ — giờ mỗi năm nhiều version.
alter table leave_policies drop constraint leave_policies_year_key;

alter table leave_policies
    add column version_no     int,
    add column status         varchar(20),
    add column effective_from date,
    add column effective_to   date,
    add column created_by     varchar(100),
    add column approved_by    varchar(100),
    add column approved_at    timestamptz,
    add column activated_by   varchar(100),
    add column activated_at   timestamptz;

-- Backfill: chính sách hiện tại (đang dùng) → Version 1 ACTIVE,
-- effective = [FY start của năm, +1 năm − 1 ngày] theo leave_settings hiện tại.
with s as (
    select coalesce((select fy_start_month from leave_settings limit 1), 1) as m,
           coalesce((select fy_start_day from leave_settings limit 1), 1)   as d
)
update leave_policies p
set version_no     = 1,
    status         = 'ACTIVE',
    effective_from = make_date(p.year, s.m, s.d),
    effective_to   = make_date(p.year, s.m, s.d) + interval '1 year' - interval '1 day'
from s;

alter table leave_policies
    alter column version_no set not null,
    alter column status set not null,
    alter column effective_from set not null,
    alter column effective_to set not null;

alter table leave_policies
    add constraint ck_leave_policy_status
        check (status in ('DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'ACTIVE', 'EXPIRED', 'CANCELLED')),
    add constraint uq_leave_policies_year_version unique (year, version_no);

create index idx_leave_policies_year_status on leave_policies (year, status);

-- Toggle "Bắt buộc phê duyệt chính sách" (Tab Tham số global).
alter table leave_settings
    add column require_policy_approval boolean not null default true;

-- Snapshot truy vết: dòng entitlement tính theo version chính sách nào, khi nào.
alter table leave_entitlements
    add column policy_id     uuid references leave_policies (id),
    add column calculated_at timestamptz;

update leave_entitlements e
set policy_id     = p.id,
    calculated_at = e.created_at
from leave_policies p
where p.year = e.year;

create index idx_leave_ent_policy on leave_entitlements (policy_id);
