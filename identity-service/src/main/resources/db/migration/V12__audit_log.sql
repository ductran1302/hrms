create table audit_log (
  id             uuid         not null primary key,
  occurred_at    timestamptz  not null,
  actor          varchar(200) not null,
  action         varchar(100) not null,
  target_type    varchar(100),
  target_id      varchar(100),
  outcome        varchar(20)  not null,
  detail         varchar(500),
  correlation_id varchar(100),
  client_ip      varchar(64)
);

create index idx_audit_log_occurred_at on audit_log (occurred_at desc);
create index idx_audit_log_actor on audit_log (actor);
create index idx_audit_log_target on audit_log (target_type, target_id);
