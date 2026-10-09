INSERT INTO users (name, email, password, role)
SELECT 'Admin', 'admin@gmail.com', 'admin123', 'admin'
WHERE NOT EXISTS (SELECT 1 FROM users WHERE email = 'admin@gmail.com');

INSERT INTO users (name, email, password, role)
SELECT 'Demo Buyer', 'buyer@gmail.com', 'buyer123', 'buyer'
WHERE NOT EXISTS (SELECT 1 FROM users WHERE email = 'buyer@gmail.com');

INSERT INTO users (name, email, password, role)
SELECT 'Demo Seller', 'seller@gmail.com', 'seller123', 'seller'
WHERE NOT EXISTS (SELECT 1 FROM users WHERE email = 'seller@gmail.com');

-- Six built-in Suriya Mart products are marketplace-style items.
-- They are used as the default products shown on the buyer home page.
INSERT INTO products (id, seller_id, name, price, quantity, category, description, icon, is_default)
VALUES
(1, NULL, 'Smartphone 5G', 14999.00, 40, 'mobiles', '5G smartphone with large display and long battery life', '📱', TRUE),
(2, NULL, 'Gaming Laptop', 54990.00, 20, 'laptops', 'Performance laptop suitable for study, work and gaming', '💻', TRUE),
(3, NULL, 'Wireless Earbuds', 1999.00, 80, 'audio', 'Bluetooth wireless earbuds with charging case', '🎧', TRUE),
(4, NULL, 'Smart LED TV 43 inch', 29999.00, 25, 'televisions', 'Full HD smart LED TV for entertainment and streaming', '📺', TRUE),
(5, NULL, 'Men Solid Casual T-Shirt', 599.00, 100, 'fashion', 'Comfortable cotton casual t-shirt for men', '👕', TRUE),
(6, NULL, 'Running Sneakers', 1299.00, 70, 'footwear', 'Lightweight everyday running and sports shoes', '👟', TRUE)
ON DUPLICATE KEY UPDATE
    name=VALUES(name),
    price=VALUES(price),
    quantity=VALUES(quantity),
    category=VALUES(category),
    description=VALUES(description),
    icon=VALUES(icon),
    is_default=TRUE;
