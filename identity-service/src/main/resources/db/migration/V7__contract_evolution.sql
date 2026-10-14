-- Liên kết thay thế hợp đồng (ký mới thay hợp đồng cũ — lịch sử nối chuỗi).
alter table contracts add column replaces_contract_id uuid;
alter table contracts
    add constraint fk_contracts_replaces foreign key (replaces_contract_id) references contracts (id);
create index idx_contracts_replaces on contracts (replaces_contract_id);

-- Tài liệu đính kèm hợp đồng (phụ lục/hợp đồng scan) — null = tài liệu chung của nhân viên.
alter table employee_documents add column contract_id uuid;
alter table employee_documents
    add constraint fk_documents_contract foreign key (contract_id) references contracts (id);
create index idx_documents_contract on employee_documents (contract_id);
