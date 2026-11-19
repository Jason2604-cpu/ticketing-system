-- =============================================================================
--  V11 - On Hold status (pauses the resolution SLA)
-- =============================================================================
--
-- A ticket waiting on a spare part, another vendor, a booked visit or the customer can be put
-- ON_HOLD with a reason. While it is on hold the RESOLUTION clock is paused (the first-response
-- clock is not — a first reply should already have happened). When it leaves hold, the working
-- minutes it spent paused are added to resolution_due_at.
--
--   hold_reason         why it is on hold (customers see this; the staff note is an internal comment)
--   hold_until          optional date shown on the ticket, e.g. the booked visit
--   sla_paused_at       when the current hold started; NULL whenever the ticket is not on hold
--   sla_paused_minutes  working minutes spent on hold during the CURRENT SLA clock (reset on reopen)

ALTER TABLE tickets DROP CONSTRAINT IF EXISTS tickets_status_check;
ALTER TABLE tickets ADD CONSTRAINT tickets_status_check
    CHECK (status IN ('OPEN', 'IN_PROGRESS', 'ON_HOLD', 'RESOLVED', 'CLOSED'));

ALTER TABLE tickets
    ADD COLUMN IF NOT EXISTS hold_reason character varying(30),
    ADD COLUMN IF NOT EXISTS hold_until timestamp(6) without time zone,
    ADD COLUMN IF NOT EXISTS sla_paused_at timestamp(6) without time zone,
    ADD COLUMN IF NOT EXISTS sla_paused_minutes bigint NOT NULL DEFAULT 0;

ALTER TABLE tickets ADD CONSTRAINT tickets_hold_reason_check
    CHECK (hold_reason IS NULL OR hold_reason IN ('SPARE_PART', 'VENDOR', 'SCHEDULED', 'CUSTOMER_REPLY', 'OTHER'));

-- On-hold tickets are still unfinished work: the sidebar / dashboard open counts include them,
-- so the partial index behind those counts (V2) has to as well, or the planner can't use it.
DROP INDEX IF EXISTS ix_tickets_open_by_company;
CREATE INDEX IF NOT EXISTS ix_tickets_open_by_company
    ON tickets (company_id)
    WHERE status IN ('OPEN', 'IN_PROGRESS', 'ON_HOLD');

-- New notification types (the CHECK lists every value, so it is replaced — see V6 / V10).
ALTER TABLE notifications DROP CONSTRAINT IF EXISTS notifications_type_check;
ALTER TABLE notifications ADD CONSTRAINT notifications_type_check
    CHECK (type IN ('TICKET_ASSIGNED_TO_YOU', 'TICKET_ASSIGNED_NOTICE', 'TICKET_REPLY', 'TICKET_RESOLVED',
                    'TICKET_CLOSED', 'TICKET_CREATED', 'SLA_BREACH', 'CONTRACT_RENEWAL_DUE',
                    'KB_PUBLISH_REQUESTED', 'KB_PUBLISH_DECIDED', 'TICKET_REQUESTER_CHANGED',
                    'TICKET_ON_HOLD', 'TICKET_RESUMED'));
