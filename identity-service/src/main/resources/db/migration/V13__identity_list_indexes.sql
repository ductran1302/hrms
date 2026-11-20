-- Composite indexes cho danh sách phân trang identity (contract/document).
create index idx_contracts_employee_created on contracts (employee_id, created_at desc);
create index idx_contracts_status_created on contracts (status, created_at desc);
create index idx_contracts_status_end_date on contracts (status, end_date);
create index idx_employee_documents_employee_uploaded
    on employee_documents (employee_id, uploaded_at desc);
