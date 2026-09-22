-- Provision the restricted internal back-office administrator account.
-- The password is BCrypt(123456). Existing installations keep a manually
-- changed password because this migration only updates the role/status when
-- the username already exists.
INSERT INTO user (username, age, email, phone, password, role, status)
VALUES (
    'company_admin',
    30,
    NULL,
    NULL,
    '$2a$10$II9ChDVeF3sL3ftnAkrKrerZSXiiuzvs/r39u2wXSlofQU8PSx9YO',
    'company_admin',
    'active'
)
ON DUPLICATE KEY UPDATE
    role = 'company_admin',
    status = 'active';
