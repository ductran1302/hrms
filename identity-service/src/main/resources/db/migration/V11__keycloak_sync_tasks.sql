-- P0-6: hang doi dong bo Keycloak, ghi cung transaction voi business data.
create table keycloak_sync_tasks (
    id uuid primary key,
    operation varchar(30) not null,
    username varchar(64) not null,
    full_name varchar(200),
    email varchar(200),
    role_name varchar(30),
    enabled boolean,
    status varchar(20) not null,
    attempts integer not null default 0,
    next_attempt_at timestamptz not null,
    last_error varchar(1000),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    version bigint not null default 0
);

create index idx_keycloak_sync_tasks_pending
    on keycloak_sync_tasks (status, next_attempt_at);
