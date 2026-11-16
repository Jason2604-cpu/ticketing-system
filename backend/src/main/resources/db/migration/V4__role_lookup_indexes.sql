-- =============================================================================
--  V4 - keys/indexes behind the employee-role lookups
-- =============================================================================

-- role_privileges had no key at all: every request by an Engineer / Default employee reads the
-- privileges of their custom roles (JwtCookieFilter), which was a full scan, and nothing stopped
-- the same privilege being stored twice on one role. Clean up, then key it on (role_id, privilege).
DELETE FROM role_privileges WHERE privilege IS NULL;
DELETE FROM role_privileges a
USING role_privileges b
WHERE a.ctid < b.ctid AND a.role_id = b.role_id AND a.privilege = b.privilege;
ALTER TABLE role_privileges ALTER COLUMN privilege SET NOT NULL;
ALTER TABLE role_privileges ADD CONSTRAINT role_privileges_pkey PRIMARY KEY (role_id, privilege);

-- Moving an employee to Default frees every company where they are the main engineer
-- (UPDATE ... WHERE main_engineer_id = ?); the foreign key had no index behind it.
CREATE INDEX IF NOT EXISTS ix_companies_main_engineer ON companies (main_engineer_id);
