-- Thêm snapshot cấu hình OT vào lịch sử.
alter table ot_config_histories
    add column if not exists day_start_time          varchar(8),
    add column if not exists night_start_time        varchar(8),
    add column if not exists post_shift_buffer_minutes integer,
    add column if not exists pre_shift_buffer_minutes  integer,
    add column if not exists break_duration_minutes    integer,
    add column if not exists rate_weekday              numeric(3,2),
    add column if not exists rate_weekday_night        numeric(3,2),
    add column if not exists rate_weekend              numeric(3,2),
    add column if not exists rate_weekend_night        numeric(3,2),
    add column if not exists rate_holiday              numeric(3,2),
    add column if not exists rate_holiday_night        numeric(3,2);
