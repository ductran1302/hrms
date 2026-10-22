-- Bonus quỹ phép theo role KC (thay thế position quota — Q&A #9).
-- DB enum source giữ POSITION (không vỡ data cũ); bảng mới role-based.
create table leave_role_quotas
(
    id          uuid primary key default gen_random_uuid(),
    policy_id   uuid          not null references leave_policies (id) on delete cascade,
    role        varchar(30)   not null,  -- ADMIN | GLOBAL_LEAD | DEPT_LEAD | MANAGER | EMPLOYEE
    bonus_days  numeric(8, 4) not null,
    unique (policy_id, role)
);
