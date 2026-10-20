-- Re-key nhan vien (identity tao lai id moi, cung employee_code):
-- hoan check FK den commit de doi employees.employee_id khi con ban ghi con
-- (children update truoc parent trong cung transaction).
alter table shift_assignments
    alter constraint shift_assignments_employee_id_fkey deferrable initially deferred;
alter table leave_entitlements
    alter constraint leave_entitlements_employee_id_fkey deferrable initially deferred;
