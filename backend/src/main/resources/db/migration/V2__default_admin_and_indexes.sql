-- =============================================================================
--  V2 - forced password change for the default admin, session revocation on password
--       change, and query-shaped indexes
-- =============================================================================

-- ── Default admin ────────────────────────────────────────────────────────────
-- AdminBootstrap creates admin@admin.com / admin with this set; the API only allows
-- /auth/me, /auth/change-password and /auth/logout until the user picks a new password.
ALTER TABLE users ADD COLUMN IF NOT EXISTS must_change_password boolean NOT NULL DEFAULT false;
-- Auth cookies issued before this instant are refused: changing / resetting a password signs out
-- every other session (including one opened with admin/admin before it was replaced).
ALTER TABLE users ADD COLUMN IF NOT EXISTS password_changed_at timestamp(6) without time zone;


-- ── tickets ──────────────────────────────────────────────────────────────────
-- Company-scoped ticket list (ORDER BY created_at DESC), the monthly quota count and every
-- report query filter on company_id plus a created_at range. Replaces ix_tickets_company,
-- which is a prefix of this one.
CREATE INDEX IF NOT EXISTS ix_tickets_company_created ON tickets (company_id, created_at);
DROP INDEX IF EXISTS ix_tickets_company;

-- Sidebar / dashboard open-ticket counts (polled): status IN ('OPEN','IN_PROGRESS') by company.
CREATE INDEX IF NOT EXISTS ix_tickets_open_by_company
    ON tickets (company_id)
    WHERE status IN ('OPEN', 'IN_PROGRESS');

-- "Resolved in window" report queries use coalesce(resolved_at, closed_at).
CREATE INDEX IF NOT EXISTS ix_tickets_company_finished
    ON tickets (company_id, (coalesce(resolved_at, closed_at)));


-- ── users ────────────────────────────────────────────────────────────────────
-- Login, forgot-password and invite checks compare upper(email) = upper(?) (Spring Data
-- "IgnoreCase"), which cannot use the plain index on email.
CREATE INDEX IF NOT EXISTS ix_users_email_upper ON users (upper(email));


-- ── lookups on the second column of a composite key ─────────────────────────
-- (A composite unique key / primary key only helps queries on its leading column.)
CREATE INDEX IF NOT EXISTS ix_engineer_assignments_company ON engineer_assignments (company_id);
CREATE INDEX IF NOT EXISTS ix_user_roles_role             ON user_roles (role_id);
CREATE INDEX IF NOT EXISTS ix_contract_assets_asset       ON contract_assets (asset_id);
CREATE INDEX IF NOT EXISTS ix_contract_asset_groups_group ON contract_asset_groups (group_id);


-- ── audit log viewer + token cleanup ─────────────────────────────────────────
-- Filter by action / actor, newest first. Supersede the single-column versions.
CREATE INDEX IF NOT EXISTS ix_audit_action_created ON audit_events (action, created_at);
CREATE INDEX IF NOT EXISTS ix_audit_actor_created  ON audit_events (actor_id, created_at);
DROP INDEX IF EXISTS idx_audit_action;
DROP INDEX IF EXISTS idx_audit_actor;

-- Daily purge of expired activation / reset tokens.
CREATE INDEX IF NOT EXISTS ix_account_tokens_expires ON account_tokens (expires_at);


-- ── comments ─────────────────────────────────────────────────────────────────
-- Ticket thread is read in order.
CREATE INDEX IF NOT EXISTS ix_ticket_comments_ticket_created ON ticket_comments (ticket_id, created_at);
DROP INDEX IF EXISTS ix_ticket_comments_ticket;

ANALYZE;
