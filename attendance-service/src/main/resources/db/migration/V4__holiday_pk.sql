-- Entity Holiday trong attendance map @Id → holiday_id (theo Employee projection);
-- V2 saiPK là cột id thừa → đổi PK sang holiday_id, bỏ cột id.
alter table holidays drop constraint if exists holidays_pkey;
alter table holidays drop column if exists id;
alter table holidays add primary key (holiday_id);
