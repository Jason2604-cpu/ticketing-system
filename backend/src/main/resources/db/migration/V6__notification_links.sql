-- =============================================================================
--  V6 - Notification links + Knowledge Base notifications
-- =============================================================================
--
-- Until now a notification could only point at a ticket (ticket_id). Knowledge Base publish
-- requests point at an article, and contract reminders at a company, so a notification gets a
-- generic in-app path to open instead ("/admin/kb/12/edit", "/companies/3"). ticket_id stays for
-- the ticket notifications that already use it.

ALTER TABLE notifications ADD COLUMN IF NOT EXISTS link varchar(300);

-- New enum values. Same pattern as V3 / V5: the CHECK lists every value, so it is replaced.
ALTER TABLE notifications DROP CONSTRAINT IF EXISTS notifications_type_check;
ALTER TABLE notifications ADD CONSTRAINT notifications_type_check
    CHECK (type IN ('TICKET_ASSIGNED_TO_YOU', 'TICKET_ASSIGNED_NOTICE', 'TICKET_REPLY', 'TICKET_RESOLVED',
                    'TICKET_CLOSED', 'TICKET_CREATED', 'SLA_BREACH', 'CONTRACT_RENEWAL_DUE',
                    'KB_PUBLISH_REQUESTED', 'KB_PUBLISH_DECIDED'));

