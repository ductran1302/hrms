-- Defer FK ghi chú phép theo nhân sự để re-key PK employees cùng transaction
-- (match shift_assignments + leave_entitlements DEFERRABLE INITIALLY DEFERRED).
alter table leave_employee_notes
  drop constraint leave_employee_notes_employee_id_fkey;

alter table leave_employee_notes
  add constraint fk_leave_employee_notes_employee
  foreign key (employee_id)
  references employees (employee_id)
  deferrable initially deferred;
