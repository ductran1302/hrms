-- Quy tắc thâm niên mở (rule): VN_TIERED = bậc theo luật VN (NĐ 145/2020),
-- LINEAR = logic cũ (mỗi stepYears +bonusDays theo năm dương lịch).
-- Bậc (tier) lưu theo policy — mở rộng Hàn/Nhật không cần đổi schema.

alter table leave_policies
    add column seniority_rule varchar(30) not null default 'VN_TIERED';

create table leave_seniority_tiers
(
    id         uuid primary key default gen_random_uuid(),
    policy_id  uuid          not null references leave_policies (id) on delete cascade,
    min_years  int           not null,  -- bậc áp dụng khi nhân viên ĐỦ min_years năm
    bonus_days numeric(8, 4) not null,
    unique (policy_id, min_years)
);

-- Seed bậc mặc định theo luật VN: tổng 12/14/16/18/20 ngày (base 12 → bonus 0/2/4/6/8).
insert into leave_seniority_tiers (policy_id, min_years, bonus_days)
select p.id, t.min_years, t.bonus_days
from leave_policies p
         cross join (values (0, 0.0), (5, 2.0), (10, 4.0), (15, 6.0), (20, 8.0))
                     as t (min_years, bonus_days);
