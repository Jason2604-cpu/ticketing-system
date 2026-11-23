-- =============================================================================
--  V12 - Reply to a message in the ticket Activity feed (WhatsApp-style quote)
-- =============================================================================
--
-- A comment can point at the earlier comment it answers. The feed stays a flat, chronological
-- list; the reply just shows a short quote of the original above its own text. Only the link is
-- stored — the quote is built from the original when the ticket is read.
--
--   reply_to_id   the comment this one replies to; NULL for an ordinary comment (all existing rows)
--
-- Rules live in TicketService.comment(): same ticket only, never a system entry, and a reply to an
-- internal note must itself be internal (so a customer-visible quote can never show internal text).
-- ON DELETE SET NULL: if an original is ever removed, its replies stay and show "unavailable".

ALTER TABLE ticket_comments
    ADD COLUMN IF NOT EXISTS reply_to_id bigint;

ALTER TABLE ticket_comments DROP CONSTRAINT IF EXISTS fk_ticket_comments_reply_to;
ALTER TABLE ticket_comments
    ADD CONSTRAINT fk_ticket_comments_reply_to
    FOREIGN KEY (reply_to_id) REFERENCES ticket_comments (id) ON DELETE SET NULL;

-- Lets the ON DELETE SET NULL above find a parent's replies without scanning the table.
CREATE INDEX IF NOT EXISTS ix_ticket_comments_reply_to
    ON ticket_comments (reply_to_id)
    WHERE reply_to_id IS NOT NULL;
