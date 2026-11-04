-- Cấu hình tính OT toàn công ty (active row).
create table ot_configs (
    id              uuid        primary key default gen_random_uuid(),
    day_start_time  time        not null default '06:00:00',        -- điểm giao đêm-ngày
    night_start_time time        not null default '22:00:00',       -- điểm giao ngày-đêm
    post_shift_buffer_minutes int  not null default 60,             -- nghỉ sau ca trước khi OT (phút)
    pre_shift_buffer_minutes  int  not null default 60,             -- nghỉ trước ca sau khi OT (phút)
    break_duration_minutes    int  not null default 0,              -- giờ nghỉ giải lao trong ca OT

    rate_weekday        numeric(5,2) not null default 1.50,
    rate_weekday_night  numeric(5,2) not null default 2.10,
    rate_weekend        numeric(5,2) not null default 2.00,
    rate_weekend_night  numeric(5,2) not null default 2.70,
    rate_holiday        numeric(5,2) not null default 3.00,
    rate_holiday_night  numeric(5,2) not null default 3.90,

    status              varchar(20) not null default 'ACTIVE',
    created_by          varchar(100),
    created_at          timestamptz not null default current_timestamp,
    updated_by          varchar(100),
    updated_at          timestamptz not null default current_timestamp,
    version             bigint      not null default 0
);

-- Đảm bảo chỉ có 1 config ACTIVE
create unique index uq_ot_configs_active on ot_configs ((status = 'ACTIVE')) where status = 'ACTIVE';