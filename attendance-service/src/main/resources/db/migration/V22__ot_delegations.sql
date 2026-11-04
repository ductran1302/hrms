-- Bảng ủy quyền phê duyệt OT.
create table ot_delegations (
    id              uuid        primary key default gen_random_uuid(),
    delegator_id    uuid        not null,
    delegatee_id    uuid        not null,
    start_date      date        not null,
    end_date        date        not null,
    status          varchar(20) not null default 'ACTIVE',
    created_at      timestamptz not null default current_timestamp
);

create index idx_ot_delegations_delegatee on ot_delegations(delegatee_id);
create index idx_ot_delegations_date     on ot_delegations(start_date, end_date);