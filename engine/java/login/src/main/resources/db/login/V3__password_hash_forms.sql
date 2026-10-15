-- The password hash has two forms. New passwords are salted PBKDF2; the accounts of the 2.x line keep their
-- unsalted SHA-1 until the next login of the account, which replaces it.
COMMENT ON COLUMN account.password_hash IS 'Stored form of the password: pbkdf2-sha256$iterations$salt$hash (Base64) for a salted PBKDF2-HMAC-SHA256, or the Base64 of the unsalted SHA-1 digest (legacy form of the 2.x line, replaced at the next login).';
