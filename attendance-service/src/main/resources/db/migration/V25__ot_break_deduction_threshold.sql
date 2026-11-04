-- Ngưỡng (phút) chỉ trừ "nghỉ giữa ca" khi thời lượng OT của item VƯỢT ngưỡng.
-- Default 240 = 4 giờ: OT <= 4h giữ nguyên giờ, OT > 4h mới trừ break.
-- Có thể chỉnh qua cấu hình OT (vd sau này đặt = 1/2 thời lượng ca làm việc).
alter table ot_configs
    add column if not exists break_deduction_threshold_minutes integer not null default 240;

alter table ot_config_histories
    add column if not exists break_deduction_threshold_minutes integer;
