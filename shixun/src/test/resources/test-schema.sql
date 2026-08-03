CREATE TABLE user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    age INT,
    email VARCHAR(200),
    phone VARCHAR(30),
    password VARCHAR(255) NOT NULL,
    -- Mapper-level unit tests intentionally omit role; production registration
    -- always supplies it and the production schema keeps it NOT NULL.
    role VARCHAR(20) DEFAULT 'user'
);
