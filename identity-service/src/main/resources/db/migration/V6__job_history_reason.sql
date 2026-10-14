-- Cột lý do nghỉ việc trên lịch sử job — reason trước đây chỉ đẩy ra outbox event.
ALTER TABLE employee_job_history ADD COLUMN reason varchar(500);
