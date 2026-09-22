-- Provision a standard internal back-office account. It can submit routine
-- internal work, but is not a manager, super administrator, or C-end user.
-- The password is BCrypt(123456). Existing installations keep a manually
-- changed password because this migration does not overwrite it.
INSERT INTO user (username, age, email, phone, password, role, status)
VALUES (
    'backoffice_user',
    30,
    NULL,
    NULL,
    '$2a$10$nDn6zmH9yqEz0agEk8NZmevOiFys/plHVvjBtUvzvVHsy4LH5pvlS',
    'backoffice_user',
    'active'
)
ON DUPLICATE KEY UPDATE
    role = 'backoffice_user',
    status = 'active';
