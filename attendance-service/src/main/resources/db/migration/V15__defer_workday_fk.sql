-- Defer FK ngày công theo nhân sự để re-key PK employees cùng transaction
-- (match shift_assignments + leave_entitlements + leave_employee_notes
-- DEFERRABLE INITIALLY DEFERRED — thieu 1 bang -> re-key delete employee loi FK).
alter table leave_work_days
  drop constraint leave_work_days_employee_id_fkey;

alter table leave_work_days
  add constraint fk_leave_work_days_employee
  foreign key (employee_id)
  references employees (employee_id)
  deferrable initially deferred;
