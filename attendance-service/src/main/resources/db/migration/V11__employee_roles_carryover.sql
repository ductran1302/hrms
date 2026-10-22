-- Role KC của NV (mirror từ Kafka event của identity) — nguồn tính "Phép theo role".
alter table employees
    add column roles varchar(200); -- comma-joined, vd "ADMIN,DEPT_LEAD"; null = chưa sync

-- CARRYOVER do recalc tự quản lý → cần phân biệt auto/thủ công (ADMIN sửa tay không bị đè).
alter table leave_entitlements
    add column is_auto boolean not null default true;

-- Gộp CARRYOVER trùng (trước đây grant tạo nhiều dòng, cap theo tổng) → 1 dòng/NV/năm,
-- giữ dòng mới nhất, days = tổng; tổng = 0 thì xóa (vi phạm check days<>0).
create temp table _co_agg as
select employee_id,
       year,
       sum(days)                                        as total_days,
       (array_agg(id order by created_at desc, id desc))[1] as keep_id
from leave_entitlements
where source = 'CARRYOVER'
group by employee_id, year;

delete
from leave_entitlements e
using _co_agg c
where e.source = 'CARRYOVER'
  and e.employee_id = c.employee_id
  and e.year = c.year
  and e.id <> c.keep_id;

delete
from leave_entitlements e
using _co_agg c
where e.id = c.keep_id
  and c.total_days = 0;

update leave_entitlements e
set days = c.total_days
from _co_agg c
where e.id = c.keep_id
  and c.total_days <> 0
  and e.days <> c.total_days;

drop table _co_agg;

-- CARRYOVER hiện có đều là dòng tay cũ → recalc không đè.
update leave_entitlements
set is_auto = false
where source = 'CARRYOVER';

-- Nới unique auto: thêm CARRYOVER (mỗi NV/năm chỉ 1 dòng tự quản lý).
drop index uk_leave_ent_auto;
create unique index uk_leave_ent_auto
    on leave_entitlements (employee_id, year, source)
    where source in ('BASE', 'SENIORITY', 'POSITION', 'CARRYOVER');
