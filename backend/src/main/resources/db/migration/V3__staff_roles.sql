-- =============================================================================
--  V3 - employee roles become one choice: Admin, Engineer, or Default (no modules)
-- =============================================================================

-- New primary role STAFF = "Default": an employee who can sign in but has no module until an
-- admin ticks Engineer / Admin or grants a custom role. The CHECK constraint lists every value
-- of the Roles enum, so it has to be replaced to accept the new one.
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check;
ALTER TABLE users ADD CONSTRAINT users_role_check
    CHECK (role IN ('ENGINEER', 'ADMIN', 'CUSTOMER_ADMIN', 'CUSTOMER', 'STAFF'));

-- Until now an employee had a primary role PLUS optional "additional" system roles
-- (an Engineer could also be granted Admin). The service code only ever looked at the primary
-- role, so such a user had Admin pages but Engineer-scoped data. Flatten it: an Engineer who
-- was also granted Admin becomes an Admin (Admin already includes everything an Engineer does).
UPDATE users u
SET role = 'ADMIN', updated_at = now()
WHERE u.role = 'ENGINEER'
  AND EXISTS (
      SELECT 1
      FROM user_roles ur
      JOIN roles r ON r.id = ur.role_id
      WHERE ur.user_id = u.id AND r.custom = false AND r.name = 'ADMIN'
  );

-- The additive system-role grants are gone; custom (Role Management) grants are kept.
DELETE FROM user_roles ur
USING roles r
WHERE ur.role_id = r.id AND r.custom = false;

-- An Admin already holds every privilege, so custom roles on an Admin are dead weight; the Users
-- page shows "Included in Admin" and saving an Admin drops them too. Do the same here.
DELETE FROM user_roles ur
USING users u
WHERE ur.user_id = u.id AND u.role = 'ADMIN';
