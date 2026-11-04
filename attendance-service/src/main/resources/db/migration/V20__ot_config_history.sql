-- Lịch sử thay đổi cấu hình OT (audit trail).
create table ot_config_histories (
    id              uuid        primary key default gen_random_uuid(),
    config_id       uuid        not null references ot_configs(id),
    action          varchar(20) not null check (action in ('CREATE','UPDATE','DELETE')),
    changes_json    text,
    performed_by    varchar(100) not null,           -- username người thực hiện
    performed_at    timestamptz not null default current_timestamp,
    created_at      timestamptz not null default current_timestamp,
    updated_at      timestamptz not null default current_timestamp,
    version         bigint      not null default 0
);

create index idx_ot_config_hist_config_id on ot_config_histories(config_id);