package com.suriyamart.controller;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;

@RestController
@RequestMapping("/api")
public class SuriyaMartController {

    private final JdbcTemplate jdbc;

    public SuriyaMartController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private Map<String, Object> ok() {
        return new LinkedHashMap<>(Map.of("success", true));
    }

    private Map<String, Object> error(String message) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("success", false);
        r.put("message", message);
        return r;
    }

    // =========================
    // REGISTER
    // =========================
    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody Map<String, Object> body) {
        String name = str(body.get("name"));
        String email = str(body.get("email"));
        String password = str(body.get("password"));
        String role = str(body.get("role")).toLowerCase();

        if (name.isBlank() || email.isBlank() || password.isBlank()
                || !(role.equals("buyer") || role.equals("seller"))) {
            return error("Please enter valid registration details.");
        }

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM users WHERE email = ?", Integer.class, email);

        if (count != null && count > 0) {
            return error("Email already registered.");
        }

        jdbc.update(
                "INSERT INTO users(name,email,password,role) VALUES(?,?,?,?)",
                name, email, password, role);

        Map<String, Object> r = ok();
        r.put("message", "Account created successfully.");
        return r;
    }

    // =========================
    // LOGIN
    // =========================
    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, Object> body) {
        String email = str(body.get("email"));
        String password = str(body.get("password"));
        String role = str(body.get("role")).toLowerCase();

        List<Map<String, Object>> users = jdbc.queryForList(
                "SELECT id,name,email,role FROM users WHERE email=? AND password=? AND role=?",
                email, password, role);

        if (users.isEmpty()) {
            return error("Invalid email or password.");
        }

        Map<String, Object> r = ok();
        r.put("message", "Login successful.");
        r.put("user", users.get(0));
        return r;
    }

    // =========================
    // ADMIN LOGIN
    // =========================
    @PostMapping("/admin/login")
    public Map<String, Object> adminLogin(@RequestBody Map<String, Object> body) {
        String email = str(body.get("email"));
        String password = str(body.get("password"));

        if (email.isBlank() || password.isBlank()) {
            return error("Please enter admin email and password.");
        }

        List<Map<String, Object>> admins = jdbc.queryForList(
                "SELECT id,name,email,role FROM users " +
                "WHERE email=? AND password=? AND role='admin'",
                email, password);

        if (admins.isEmpty()) {
            return error("Invalid admin email or password.");
        }

        Map<String, Object> r = ok();
        r.put("message", "Admin login successful.");
        r.put("user", admins.get(0));
        return r;
    }

    // =========================
    // PRODUCTS
    // Returns seller-added products.
    // The HTML already contains the six built-in products.
    // =========================
    @GetMapping("/products")
    public Map<String, Object> products() {
        List<Map<String, Object>> products = jdbc.queryForList("""
            SELECT id, seller_id, name, price, quantity, category,
                   COALESCE(description, CONCAT(category, ' | Available: ', quantity)) AS description,
                   COALESCE(icon, '🛍️') AS icon
            FROM products
            WHERE is_default = FALSE
            ORDER BY id DESC
            """);

        Map<String, Object> r = ok();
        r.put("products", products);
        return r;
    }

    @PostMapping("/products")
    public Map<String, Object> addProduct(@RequestBody Map<String, Object> body) {
        int sellerId = intValue(body.get("seller_id"));
        String name = str(body.get("name"));
        BigDecimal price = decimal(body.get("price"));
        int quantity = intValue(body.get("quantity"));
        String category = str(body.get("category"));

        if (sellerId <= 0 || name.isBlank() || price.compareTo(BigDecimal.ZERO) <= 0
                || quantity <= 0 || category.isBlank()) {
            return error("Invalid product details.");
        }

        if (!userHasRole(sellerId, "seller")) {
            return error("Invalid seller account.");
        }

        jdbc.update("""
            INSERT INTO products(seller_id,name,price,quantity,category,description,icon,is_default)
            VALUES(?,?,?,?,?,?,?,FALSE)
            """,
            sellerId, name, price, quantity, category,
            category + " | Available: " + quantity, "🛍️");

        Map<String, Object> r = ok();
        r.put("message", "Product added successfully.");
        r.put("product_id", jdbc.queryForObject("SELECT LAST_INSERT_ID()", Integer.class));
        return r;
    }

    @PutMapping("/products/update")
    public Map<String, Object> updateProduct(@RequestBody Map<String, Object> body) {
        int id = intValue(body.get("id"));
        int sellerId = intValue(body.get("seller_id"));
        String name = str(body.get("name"));
        BigDecimal price = decimal(body.get("price"));
        int quantity = intValue(body.get("quantity"));
        String category = str(body.get("category"));

        if (id <= 0 || sellerId <= 0 || name.isBlank()
                || price.compareTo(BigDecimal.ZERO) <= 0 || quantity < 0 || category.isBlank()) {
            return error("Invalid product details.");
        }

        int changed = jdbc.update("""
            UPDATE products
            SET name=?, price=?, quantity=?, category=?,
                description=?
            WHERE id=? AND seller_id=? AND is_default=FALSE
            """,
            name, price, quantity, category,
            category + " | Available: " + quantity,
            id, sellerId);

        return changed == 1 ? okWithMessage("Product updated successfully.")
                : error("Product not found or you are not the owner.");
    }

    @DeleteMapping("/products/delete")
    public Map<String, Object> deleteProduct(@RequestBody Map<String, Object> body) {
        int id = intValue(body.get("id"));
        int changed = jdbc.update(
                "DELETE FROM products WHERE id=? AND is_default=FALSE", id);

        return changed == 1 ? okWithMessage("Product deleted successfully.")
                : error("Product not found or cannot be deleted.");
    }

    // =========================
    // CART
    // =========================
    @GetMapping("/cart")
    public Map<String, Object> getCart(@RequestParam int buyer_id) {
        List<Map<String, Object>> cart = jdbc.queryForList("""
            SELECT c.product_id, p.name, p.price, c.quantity
            FROM cart_items c
            JOIN products p ON p.id=c.product_id
            WHERE c.buyer_id=?
            ORDER BY c.id
            """, buyer_id);

        Map<String, Object> r = ok();
        r.put("cart", cart);
        return r;
    }

    @PutMapping("/cart")
    public Map<String, Object> updateCart(@RequestBody Map<String, Object> body) {
        int buyerId = intValue(body.get("buyer_id"));
        int productId = intValue(body.get("product_id"));
        int quantity = intValue(body.get("quantity"));

        if (buyerId <= 0 || productId <= 0 || quantity <= 0) {
            return error("Invalid cart details.");
        }

        jdbc.update("""
            INSERT INTO cart_items(buyer_id,product_id,quantity)
            VALUES(?,?,?)
            ON DUPLICATE KEY UPDATE quantity=VALUES(quantity)
            """, buyerId, productId, quantity);

        return okWithMessage("Cart updated.");
    }

    @DeleteMapping("/cart")
    public Map<String, Object> deleteCart(@RequestBody Map<String, Object> body) {
        int buyerId = intValue(body.get("buyer_id"));
        int productId = intValue(body.get("product_id"));

        jdbc.update("DELETE FROM cart_items WHERE buyer_id=? AND product_id=?",
                buyerId, productId);

        return okWithMessage("Product removed from cart.");
    }

    // =========================
    // ORDERS
    // =========================
    @PostMapping("/orders")
    @Transactional
    public Map<String, Object> createOrder(@RequestBody Map<String, Object> body) {
        int buyerId = intValue(body.get("buyer_id"));
        String customerName = str(body.get("customer_name"));
        String mobile = str(body.get("mobile"));
        String address = str(body.get("address"));
        String city = str(body.get("city"));
        String pincode = str(body.get("pincode"));
        String payment = str(body.get("payment"));
        BigDecimal total = decimal(body.get("total"));

        if (buyerId <= 0 || customerName.isBlank() || mobile.isBlank()
                || address.isBlank() || city.isBlank() || pincode.isBlank()
                || payment.isBlank()) {
            return error("Please complete all delivery details.");
        }

        Object rawItems = body.get("items");
        if (!(rawItems instanceof List<?> items) || items.isEmpty()) {
            return error("Your cart is empty.");
        }

        jdbc.update("""
            INSERT INTO orders(buyer_id,customer_name,mobile,address,city,pincode,payment,total,status)
            VALUES(?,?,?,?,?,?,?,?,'Placed')
            """,
            buyerId, customerName, mobile, address, city, pincode, payment, total);

        int orderId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Integer.class);

        BigDecimal calculatedTotal = BigDecimal.ZERO;

        for (Object raw : items) {
            if (!(raw instanceof Map<?, ?> item)) {
                throw new IllegalArgumentException("Invalid order item.");
            }

            int productId = intValue(item.get("product_id"));
            int quantity = intValue(item.get("quantity"));

            if (productId <= 0 || quantity <= 0) {
                throw new IllegalArgumentException("Invalid product or quantity.");
            }

            List<Map<String, Object>> products = jdbc.queryForList(
                    "SELECT id,price,quantity FROM products WHERE id=?", productId);

            if (products.isEmpty()) {
                throw new IllegalArgumentException("Product " + productId + " does not exist.");
            }

            Map<String, Object> p = products.get(0);
            BigDecimal price = new BigDecimal(String.valueOf(p.get("price")));
            int stock = ((Number) p.get("quantity")).intValue();

            if (stock < quantity) {
                throw new IllegalArgumentException(
                        "Not enough stock for product ID " + productId + ".");
            }

            jdbc.update("""
                INSERT INTO order_items(order_id,product_id,quantity,price)
                VALUES(?,?,?,?)
                """, orderId, productId, quantity, price);

            jdbc.update(
                    "UPDATE products SET quantity=quantity-? WHERE id=?",
                    quantity, productId);

            calculatedTotal = calculatedTotal.add(price.multiply(BigDecimal.valueOf(quantity)));
        }

        // Use the database-calculated amount for consistency.
        jdbc.update("UPDATE orders SET total=? WHERE id=?", calculatedTotal, orderId);

        jdbc.update("DELETE FROM cart_items WHERE buyer_id=?", buyerId);

        Map<String, Object> r = ok();
        r.put("message", "Order placed successfully.");
        r.put("order_id", orderId);
        return r;
    }

    @GetMapping("/orders/buyer")
    public Map<String, Object> buyerOrders(@RequestParam int buyer_id) {
        List<Map<String, Object>> orders = jdbc.queryForList("""
            SELECT id,buyer_id,customer_name,mobile,address,city,pincode,payment,
                   total,status,order_date AS date
            FROM orders
            WHERE buyer_id=?
            ORDER BY id DESC
            """, buyer_id);

        addItemsToOrders(orders);

        Map<String, Object> r = ok();
        r.put("orders", orders);
        return r;
    }

    @GetMapping("/orders/seller")
    public Map<String, Object> sellerOrders(@RequestParam int seller_id) {
        List<Map<String, Object>> orders = jdbc.queryForList("""
            SELECT DISTINCT o.id,o.buyer_id,o.customer_name,o.mobile,o.address,
                   o.city,o.pincode,o.payment,o.total,o.status,o.order_date AS date
            FROM orders o
            JOIN order_items oi ON oi.order_id=o.id
            JOIN products p ON p.id=oi.product_id
            WHERE p.seller_id=?
            ORDER BY o.id DESC
            """, seller_id);

        addItemsToOrders(orders);

        Map<String, Object> r = ok();
        r.put("orders", orders);
        return r;
    }

    @GetMapping("/admin/orders")
    public Map<String, Object> adminOrders() {
        List<Map<String, Object>> orders = jdbc.queryForList("""
            SELECT id,buyer_id,customer_name,mobile,address,city,pincode,payment,
                   total,status,order_date AS date
            FROM orders ORDER BY id DESC
            """);

        addItemsToOrders(orders);

        Map<String, Object> r = ok();
        r.put("orders", orders);
        return r;
    }

    @PutMapping("/admin/orders/status")
    public Map<String, Object> updateOrderStatus(@RequestBody Map<String, Object> body) {
        int id = intValue(body.get("id"));
        String status = str(body.get("status"));

        Set<String> allowed = Set.of("Placed", "Processing", "Shipped", "Delivered");
        if (!allowed.contains(status)) {
            return error("Invalid order status.");
        }

        int changed = jdbc.update("UPDATE orders SET status=? WHERE id=?", status, id);
        return changed == 1 ? okWithMessage("Order status updated!")
                : error("Order not found.");
    }

    // =========================
    // ADMIN USERS
    // =========================
    @GetMapping("/admin/users")
    public Map<String, Object> adminUsers() {
        List<Map<String, Object>> users = jdbc.queryForList("""
            SELECT id,name,email,role,created_at
            FROM users
            ORDER BY id DESC
            """);

        Map<String, Object> r = ok();
        r.put("users", users);
        return r;
    }

    @PostMapping("/admin/users/delete")
    @Transactional
    public Map<String, Object> deleteUser(@RequestBody Map<String, Object> body) {
        int id = intValue(body.get("id"));

        Integer adminCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM users WHERE id=? AND role='admin'", Integer.class, id);

        if (adminCount != null && adminCount > 0) {
            return error("Admin account cannot be deleted.");
        }

        int changed = jdbc.update("DELETE FROM users WHERE id=?", id);
        return changed == 1 ? okWithMessage("User removed successfully!")
                : error("User not found.");
    }

    // =========================
    // REVIEWS
    // =========================
    @GetMapping("/reviews")
    public Map<String, Object> reviews(@RequestParam int product_id) {
        List<Map<String, Object>> reviews = jdbc.queryForList("""
            SELECT r.id,r.product_id,r.rating,r.review_text,
                   DATE_FORMAT(r.review_date,'%d-%m-%Y %H:%i') AS review_date,
                   u.name AS reviewer
            FROM reviews r
            JOIN users u ON u.id=r.buyer_id
            WHERE r.product_id=?
            ORDER BY r.id DESC
            """, product_id);

        Map<String, Object> r = ok();
        r.put("reviews", reviews);
        return r;
    }

    @PostMapping("/reviews")
    public Map<String, Object> addReview(@RequestBody Map<String, Object> body) {
        int productId = intValue(body.get("product_id"));
        int buyerId = intValue(body.get("buyer_id"));
        int rating = intValue(body.get("rating"));
        String text = str(body.get("review_text"));

        if (productId <= 0 || buyerId <= 0 || rating < 1 || rating > 5) {
            return error("Invalid review.");
        }

        jdbc.update("""
            INSERT INTO reviews(product_id,buyer_id,rating,review_text)
            VALUES(?,?,?,?)
            """, productId, buyerId, rating, text);

        return okWithMessage("Review submitted successfully.");
    }

    private void addItemsToOrders(List<Map<String, Object>> orders) {
        for (Map<String, Object> order : orders) {
            int orderId = ((Number) order.get("id")).intValue();

            List<Map<String, Object>> items = jdbc.queryForList("""
                SELECT oi.product_id, p.name, oi.price, oi.quantity,
                       p.seller_id
                FROM order_items oi
                JOIN products p ON p.id=oi.product_id
                WHERE oi.order_id=?
                ORDER BY oi.id
                """, orderId);

            order.put("products", items);
        }
    }

    private boolean userHasRole(int id, String role) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM users WHERE id=? AND role=?",
                Integer.class, id, role);
        return count != null && count > 0;
    }

    private Map<String, Object> okWithMessage(String message) {
        Map<String, Object> r = ok();
        r.put("message", message);
        return r;
    }

    private String str(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private int intValue(Object value) {
        if (value == null) return 0;
        if (value instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private BigDecimal decimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }
}
