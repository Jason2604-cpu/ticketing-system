-- When a ticket's current SLA clock started: creation, or the latest reopen.
-- Until now "how much of the SLA window is used" was measured from created_at, so a reopened
-- ticket (whose deadlines restart from the reopen) read AT_RISK immediately, and a priority
-- change had no start to re-count the new window from.
-- Existing rows stay NULL: the application falls back to created_at for them.
ALTER TABLE tickets ADD COLUMN sla_started_at timestamp(6) without time zone;
