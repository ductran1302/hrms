-- Lịch nghỉ lễ theo country/type — nhân viên gán 1 calendar (onsite country khác
-- có danh sách ngày lễ riêng). Ngày lễ lưu theo từng năm (year) — không recurring.

create table holiday_calendars
(
    id          uuid         not null primary key,
    code        varchar(30)  not null,
    name        varchar(100) not null,
    country     varchar(5)   not null,
    description varchar(300),
    status      varchar(20)  not null default 'ACTIVE',
    created_at  timestamptz  not null,
    updated_at  timestamptz  not null,
    version     bigint       not null default 0,
    constraint uq_holiday_calendars_code unique (code)
);

create index idx_holiday_calendars_country on holiday_calendars (country);

create table holidays
(
    id          uuid         not null primary key,
    calendar_id uuid         not null,
    name        varchar(200) not null,
    date_from   date         not null,
    date_to     date         not null,
    year        smallint     not null,
    type        varchar(20)  not null,
    is_half_day boolean      not null default false,
    note        varchar(300),
    created_at  timestamptz  not null,
    updated_at  timestamptz  not null,
    version     bigint       not null default 0,
    constraint fk_holidays_calendar foreign key (calendar_id) references holiday_calendars (id),
    constraint chk_holidays_range check (date_to >= date_from)
);

create index idx_holidays_calendar_year on holidays (calendar_id, year);
create index idx_holidays_year on holidays (year);

-- Nhân viên gán 1 lịch nghỉ lễ (null = dùng lịch mặc định theo country).
alter table employees add column calendar_id uuid;
alter table employees
    add constraint fk_employees_calendar foreign key (calendar_id) references holiday_calendars (id);
