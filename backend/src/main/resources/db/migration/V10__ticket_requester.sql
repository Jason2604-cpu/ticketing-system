-- =============================================================================
--  V10 - Ticket requester
-- =============================================================================
--
-- Two different people on a ticket from now on:
--   created_by    who typed the ticket in. Set once, never changes (the audit record).
--   requester_id  who the ticket is FOR — the customer its notifications and emails go to.
--                 Staff pick it when raising a ticket on a company's behalf, and can change it
--                 later. NULL = a company-level ticket (staff raised it without picking anyone,
--                 or a scheduled-maintenance visit); those notify the company's Customer Admins.

ALTER TABLE tickets ADD COLUMN IF NOT EXISTS requester_id bigint;
ALTER TABLE tickets ADD CONSTRAINT fk_tickets_requester FOREIGN KEY (requester_id) REFERENCES users(id);
CREATE INDEX IF NOT EXISTS ix_tickets_requester ON tickets (requester_id);

-- A ticket a customer raised themselves was always "for" that customer.
UPDATE tickets t
   SET requester_id = t.created_by
  FROM users u
 WHERE u.id = t.created_by
   AND u.role IN ('CUSTOMER_ADMIN', 'CUSTOMER')
   AND t.requester_id IS NULL;

-- New notification type. Same pattern as V6: the CHECK lists every value, so it is replaced.
ALTER TABLE notifications DROP CONSTRAINT IF EXISTS notifications_type_check;
ALTER TABLE notifications ADD CONSTRAINT notifications_type_check
    CHECK (type IN ('TICKET_ASSIGNED_TO_YOU', 'TICKET_ASSIGNED_NOTICE', 'TICKET_REPLY', 'TICKET_RESOLVED',
                    'TICKET_CLOSED', 'TICKET_CREATED', 'SLA_BREACH', 'CONTRACT_RENEWAL_DUE',
                    'KB_PUBLISH_REQUESTED', 'KB_PUBLISH_DECIDED', 'TICKET_REQUESTER_CHANGED'));
