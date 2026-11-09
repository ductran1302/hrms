-- Phase 6: effective-dated schedule policies (versions)
alter table work_schedule_policies add column if not exists policy_group_id uuid;
update work_schedule_policies set policy_group_id = id where policy_group_id is null;
alter table work_schedule_policies alter column policy_group_id set not null;

alter table work_schedule_policies add column if not exists effective_from date;
update work_schedule_policies set effective_from = date '1900-01-01' where effective_from is null;
alter table work_schedule_policies alter column effective_from set not null;

alter table work_schedule_policies add column if not exists effective_to date;
alter table work_schedule_policies add column if not exists version_no int not null default 1;
