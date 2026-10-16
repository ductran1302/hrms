-- Buoc 12: noi lam viec/van phong cua nhan vien — dung cho filter "van phong" tren man So do to chuc.
alter table employees
    add column office varchar(100);

create index idx_employees_office on employees (office);
