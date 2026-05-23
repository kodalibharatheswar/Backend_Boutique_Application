package com.anvistudio.boutique.controller;

import com.anvistudio.boutique.dto.*;
import com.anvistudio.boutique.model.Order;
import com.anvistudio.boutique.model.Product;
import com.anvistudio.boutique.model.Review;
import com.anvistudio.boutique.model.ContactMessage;
import com.anvistudio.boutique.model.User;
import com.anvistudio.boutique.service.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * REST API Controller for admin-specific functionality
 * Base path: /api/admin
 * Requires ROLE_ADMIN access
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminRestController {

    private final ProductService productService;
    private final UserService userService;
    private final ContactService contactService;
    private final OrderService orderService;
    private final ReviewService reviewService;
    private final ShippingIntegrationService shippingService;

    public AdminRestController(ProductService productService, UserService userService, 
                              ContactService contactService, OrderService orderService, 
                              ReviewService reviewService, ShippingIntegrationService shippingService) {
        this.productService = productService;
        this.userService = userService;
        this.contactService = contactService;
        this.orderService = orderService;
        this.reviewService = reviewService;
        this.shippingService = shippingService;
    }

    // =========================================================================
    // 1. DASHBOARD & STATISTICS
    // =========================================================================

    /**
     * Get admin dashboard statistics
     * GET /api/admin/dashboard/stats
     */
    @GetMapping("/dashboard/stats")
    public ResponseEntity<?> getDashboardStats() {
        try {
            List<Order> allOrders = orderService.getAllOrders();
            List<Product> allProducts = productService.getAllProducts();
            List<Review> unapprovedReviews = reviewService.getUnapprovedReviews();
            List<ContactMessage> allMessages = contactService.getAllMessages();

            // Calculate statistics
            long totalOrders = allOrders.size();
            long pendingOrders = allOrders.stream()
                .filter(o -> o.getStatus() == Order.OrderStatus.PENDING || 
                            o.getStatus() == Order.OrderStatus.PROCESSING ||
                            o.getStatus() == Order.OrderStatus.CONFIRMED)
                .count();
            long returnRequests = allOrders.stream()
                .filter(o -> o.getStatus() == Order.OrderStatus.RETURN_REQUESTED)
                .count();
            long totalProducts = allProducts.size();
            long lowStockProducts = allProducts.stream()
                .filter(p -> p.getStockQuantity() < 10)
                .count();
            long pendingReviews = unapprovedReviews.size();
            long unreadMessages = allMessages.size();

            Map<String, Object> stats = new HashMap<>();
            stats.put("totalOrders", totalOrders);
            stats.put("pendingOrders", pendingOrders);
            stats.put("returnRequests", returnRequests);
            stats.put("totalProducts", totalProducts);
            stats.put("lowStockProducts", lowStockProducts);
            stats.put("pendingReviews", pendingReviews);
            stats.put("unreadMessages", unreadMessages);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("stats", stats);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Failed to load dashboard statistics: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    // =========================================================================
    // 2. ORDER MANAGEMENT
    // =========================================================================

    /**
     * Get all orders for admin management
     * GET /api/admin/orders
     */
    @GetMapping("/orders")
    public ResponseEntity<?> getAllOrders() {
        try {
            List<Order> orders = orderService.getAllOrders();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("orders", orders);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Failed to load orders: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Update order status
     * PUT /api/admin/order/updateStatus/{orderId}
     */
    @PutMapping("/order/updateStatus/{orderId}")
    public ResponseEntity<?> updateOrderStatus(
            @PathVariable Long orderId,
            @RequestBody Map<String, String> request) {
        try {
            String newStatus = request.get("newStatus");
            
            Order order = orderService.getOrderById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));

            Order.OrderStatus status = Order.OrderStatus.valueOf(newStatus);
            order.setStatus(status);
            orderService.saveOrder(order);
            
            if (status == Order.OrderStatus.PACKED) {
                // Simulate pushing order to shipping partner
                shippingService.createShippingLabel(order);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Order #" + orderId + " status updated to " + newStatus);
            response.put("order", order);
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Error updating order status: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Finalize return/refund for an order
     * POST /api/admin/order/finalizeReturn/{orderId}
     */
    @PostMapping("/order/finalizeReturn/{orderId}")
    public ResponseEntity<?> finalizeReturn(@PathVariable Long orderId) {
        try {
            Order order = orderService.getOrderById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));

            if (order.getStatus() != Order.OrderStatus.RETURN_REQUESTED) {
                throw new IllegalStateException("Order must be RETURN_REQUESTED to finalize return");
            }

            order.setStatus(Order.OrderStatus.RETURNED);
            orderService.saveOrder(order);

            System.out.println("LOG: Refund approved and finalized for Order: " + orderId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Return for Order #" + orderId + " approved and refund processed");
            response.put("order", order);
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException | IllegalStateException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Error finalizing return: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    // =========================================================================
    // 3. REVIEW MODERATION
    // =========================================================================

    /**
     * Get all unapproved reviews for moderation
     * GET /api/admin/reviews/moderate
     */
    @GetMapping("/reviews/moderate")
    public ResponseEntity<?> getUnapprovedReviews() {
        try {
            List<Review> reviews = reviewService.getUnapprovedReviews();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("reviews", reviews);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Failed to load reviews: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Approve a review
     * POST /api/admin/review/approve/{reviewId}
     */
    @PostMapping("/review/approve/{reviewId}")
    public ResponseEntity<?> approveReview(@PathVariable Long reviewId) {
        try {
            reviewService.approveReview(reviewId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Review approved successfully");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Failed to approve review: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Delete a review (reject)
     * DELETE /api/admin/review/delete/{reviewId}
     */
    @DeleteMapping("/review/delete/{reviewId}")
    public ResponseEntity<?> deleteReview(@PathVariable Long reviewId) {
        try {
            reviewService.deleteReview(reviewId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Review deleted successfully");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Failed to delete review: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    // =========================================================================
    // 4. PRODUCT MANAGEMENT
    // =========================================================================

    /**
     * Get all products (with optional category filter)
     * GET /api/admin/products?category=Sarees
     */
    @GetMapping("/products")
    public ResponseEntity<?> getProducts(@RequestParam(required = false) String category) {
        try {
            List<Product> products = productService.getProductsByCategoryOrAll(category);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("products", products);
            response.put("currentCategory", category);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Failed to load products: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Get product by ID for editing
     * GET /api/admin/product/edit/{id}
     */
    @GetMapping("/product/edit/{id}")
    public ResponseEntity<?> getProductById(@PathVariable Long id) {
        try {
            Optional<Product> productOpt = productService.getProductById(id);
            
            if (productOpt.isEmpty()) {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("message", "Product with ID " + id + " not found");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("product", productOpt.get());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Database error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Add new product
     * POST /api/admin/product
     */
    @PostMapping("/product")
    public ResponseEntity<?> addProduct(@RequestBody Product product) {
        try {
            Product savedProduct = productService.saveProduct(product);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Product added successfully");
            response.put("product", savedProduct);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Error adding product: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Update existing product
     * PUT /api/admin/product/{id}
     */
    @PutMapping("/product/{id}")
    public ResponseEntity<?> updateProduct(
            @PathVariable Long id,
            @RequestBody Product product) {
        try {
            // Ensure the ID matches
            product.setId(id);
            Product updatedProduct = productService.saveProduct(product);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Product updated successfully");
            response.put("product", updatedProduct);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Error updating product: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Delete product
     * DELETE /api/admin/product/{id}
     */
    @DeleteMapping("/product/{id}")
    public ResponseEntity<?> deleteProduct(@PathVariable Long id) {
        try {
            productService.deleteProduct(id);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Product ID " + id + " deleted successfully");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Error deleting product: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Get all product categories
     * GET /api/admin/categories
     */
    @GetMapping("/categories")
    public ResponseEntity<?> getCategories() {
        String[] categories = new String[]{
            "Sarees", "Lehengas", "Kurtis", "Long Frocks", "Mom & Me", "Crop Top – Skirts",
            "Handlooms", "Casual Frocks", "Ready To Wear", "Dupattas", "Kids wear",
            "Dress Material", "Blouses", "Fabrics"
        };

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("categories", categories);
        return ResponseEntity.ok(response);
    }

    // =========================================================================
    // 5. CONTACT MESSAGE MANAGEMENT
    // =========================================================================

    /**
     * Get all contact messages
     * GET /api/admin/contacts
     */
    @GetMapping("/contacts")
    public ResponseEntity<?> getContactMessages() {
        try {
            List<ContactMessage> messages = contactService.getAllMessages();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("messages", messages);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Failed to load messages: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Delete contact message
     * DELETE /api/admin/contact/delete/{id}
     */
    @DeleteMapping("/contact/delete/{id}")
    public ResponseEntity<?> deleteContactMessage(@PathVariable Long id) {
        try {
            contactService.deleteMessage(id);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Message ID " + id + " deleted");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Error deleting message: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    // =========================================================================
    // 6. ADMIN PROFILE MANAGEMENT
    // =========================================================================

    /**
     * Get admin profile information
     * GET /api/admin/profile
     */
    @GetMapping("/profile")
    public ResponseEntity<?> getAdminProfile(@AuthenticationPrincipal UserDetails userDetails) {
        try {
            String username = userDetails.getUsername();
            Optional<User> adminUserOpt = userService.findUserByUsername(username);
            
            if (adminUserOpt.isEmpty()) {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("message", "Admin user not found");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
            }

            User admin = adminUserOpt.get();
            boolean needsUpdate = !userService.isAdminCredentialsUpdated(username) 
                                 && "admin".equals(username);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("username", username);
            response.put("recoveryPhone", admin.getRecoveryPhoneNumber());
            response.put("needsCredentialUpdate", needsUpdate);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Failed to load profile: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Update admin credentials
     * PUT /api/admin/profile
     */
    @PutMapping("/profile")
    public ResponseEntity<?> updateAdminProfile(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody AdminCredentialsUpdateDTO updateDTO) {
        try {
            String currentUsername = userDetails.getUsername();

            userService.updateAdminCredentials(
                currentUsername,
                updateDTO.getNewUsername(),
                updateDTO.getNewPassword(),
                updateDTO.getRecoveryPhoneNumber()
            );

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Credentials updated successfully. Please log in with your new details.");
            return ResponseEntity.ok(response);

        } catch (IllegalStateException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Error updating credentials: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Get order status types
     * GET /api/admin/order/statuses
     */
    @GetMapping("/order/statuses")
    public ResponseEntity<?> getOrderStatuses() {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("statuses", Order.OrderStatus.values());
        return ResponseEntity.ok(response);
    }
}