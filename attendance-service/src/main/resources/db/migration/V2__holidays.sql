-- Projection ngày lễ từ identity qua Kafka (holiday.changed) — attendance không join
-- DB chéo. holiday_id unique làm neo idempotent khi replay event.

create table holidays
(
    id            uuid         not null primary key,
    holiday_id    varchar(64)  not null,
    calendar_id   varchar(64)  not null,
    calendar_code varchar(30)  not null,
    name          varchar(200) not null,
    date_from     date         not null,
    date_to       date         not null,
    year          smallint     not null,
    holiday_type  varchar(20)  not null,
    is_half_day   boolean      not null default false,
    note          varchar(300),
    created_at    timestamptz  not null,
    updated_at    timestamptz  not null,
    constraint uq_holidays_holiday_id unique (holiday_id),
    constraint chk_holidays_range check (date_to >= date_from)
);

create index idx_holidays_calendar_year on holidays (calendar_code, year);
create index idx_holidays_year on holidays (year);
