-- Buoc 11A: thong tin bo sung cho ho so nhan vien + bang tai lieu dinh kem
alter table employees
    add column education_level varchar(50),
    add column skills text,
    add column emergency_contact_name varchar(100),
    add column emergency_contact_phone varchar(20),
    add column emergency_contact_relation varchar(50);

-- Metadata tai lieu -- file luu tren dia (hrms.storage.documents-dir)
create table employee_documents
(
    id                      uuid         not null primary key,
    employee_id             uuid         not null,
    doc_type                varchar(50)  not null,
    file_name               varchar(255) not null,
    content_type            varchar(100),
    file_size               bigint       not null,
    file_path               varchar(500) not null,
    note                    varchar(500),
    uploaded_by             varchar(100),
    uploaded_at             timestamptz  not null,
    created_at              timestamptz  not null,
    updated_at              timestamptz  not null,
    version                 bigint       not null default 0,
    constraint fk_employee_documents_employee foreign key (employee_id) references employees (id)
);

create index idx_employee_documents_employee on employee_documents (employee_id);
