-- Repair attendance projection: ACTIVE employees must not keep a stale terminated_at
-- (e.g. after reactivate / status change without a terminated event).
update employees
   set terminated_at = null,
       termination_reason = null
 where status <> 'TERMINATED'
   and terminated_at is not null;
