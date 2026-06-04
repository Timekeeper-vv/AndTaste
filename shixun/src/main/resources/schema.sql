CREATE DATABASE IF NOT EXISTS shixun;
USE shixun;

CREATE TABLE IF NOT EXISTS user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    age INT,
    email VARCHAR(200),
    phone VARCHAR(20),
    password VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS product (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    price DOUBLE,
    stock INT,
    category VARCHAR(100),
    description VARCHAR(500)
);

INSERT IGNORE INTO user (username, age, email, phone, password) VALUES
('testuser', 20, 'test@example.com', '13800138000', '123456');

INSERT IGNORE INTO product (name, price, stock, category, description) VALUES
('iPhone', 5999.00, 100, 'Electronics', 'Latest smartphone'),
('Sneakers', 299.00, 200, 'Shoes', 'Comfortable sport shoes'),
('Laptop', 4599.00, 50, 'Electronics', 'Lightweight laptop'),
('Coffee Mug', 39.00, 500, 'Household', 'Ceramic mug 350ml');
