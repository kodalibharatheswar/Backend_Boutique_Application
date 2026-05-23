package com.anvistudio.boutique.controller;

import com.anvistudio.boutique.dto.RegistrationDTO;
import com.anvistudio.boutique.model.Address;
import com.anvistudio.boutique.model.Customer;
import com.anvistudio.boutique.model.Order;
import com.anvistudio.boutique.model.User;
import com.anvistudio.boutique.service.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * REST API Controller for customer-specific operations, requiring ROLE_CUSTOMER access.
 */
@RestController
@RequestMapping("/api/customer")
public class CustomerRestController {

    private final ProductService productService;
    private final UserService userService;
    private final CartService cartService;
    private final WishlistService wishlistService;
    private final OrderService orderService;
    private final AddressService addressService;
    private final CouponService couponService;
    private final GiftCardService giftCardService;
    private final ReviewService reviewService;

    public CustomerRestController(ProductService productService, UserService userService,
                                  CartService cartService, WishlistService wishlistService,
                                  OrderService orderService, AddressService addressService,
                                  CouponService couponService, GiftCardService giftCardService,
                                  ReviewService reviewService) {
        this.productService = productService;
        this.userService = userService;
        this.cartService = cartService;
        this.wishlistService = wishlistService;
        this.orderService = orderService;
        this.addressService = addressService;
        this.couponService = couponService;
        this.giftCardService = giftCardService;
        this.reviewService = reviewService;
    }

    // =========================================================================
    // 1. My Orders & Returns
    // =========================================================================

    /**
     * GET /api/customer/orders
     * Retrieves customer's order history
     */
    @GetMapping("/orders")
    public ResponseEntity<Map<String, Object>> showMyOrders(@AuthenticationPrincipal UserDetails userDetails) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            String username = userDetails.getUsername();
            List<Order> orders = orderService.getOrdersByUsername(username);
            
            response.put("success", true);
            response.put("orders", orders);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Could not load order history: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * GET /api/customer/orders/{orderId}/tracking
     * Retrieves tracking history for a specific order
     */
    @GetMapping("/orders/{orderId}/tracking")
    public ResponseEntity<Map<String, Object>> getOrderTracking(@AuthenticationPrincipal UserDetails userDetails, @PathVariable Long orderId) {
        Map<String, Object> response = new HashMap<>();
        try {
            String username = userDetails.getUsername();
            Optional<Order> orderOptional = orderService.getOrderById(orderId);
            
            if (orderOptional.isEmpty()) {
                response.put("success", false);
                response.put("message", "Order not found.");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
            
            Order order = orderOptional.get();
            if (!order.getUser().getUsername().equals(username)) {
                response.put("success", false);
                response.put("message", "Unauthorized access to order.");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }
            
            response.put("success", true);
            response.put("tracking", orderService.getTrackingHistory(orderId));
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Could not load tracking history: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * POST /api/customer/order/review
     * Submit a review for a product from an order
     */
    @PostMapping("/order/review")
    public ResponseEntity<Map<String, Object>> submitOrderProductReview(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody ReviewSubmissionRequest request) {
        
        Map<String, Object> response = new HashMap<>();
        String username = userDetails.getUsername();

        try {
            // 1. Verify that the order belongs to the current user
            Optional<Order> orderOptional = orderService.getOrderById(request.getOrderId());

            if (orderOptional.isEmpty()) {
                response.put("success", false);
                response.put("message", "Order not found.");
                return ResponseEntity.badRequest().body(response);
            }

            Order order = orderOptional.get();

            // 2. Security check: Ensure the order belongs to the authenticated user
            if (!order.getUser().getUsername().equals(username)) {
                response.put("success", false);
                response.put("message", "Unauthorized access to order.");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }

            // 3. Verify the product was part of this order
            boolean productInOrder = order.getOrderItemsSnapshot() != null &&
                    order.getOrderItemsSnapshot().contains("[ID:" + request.getProductId() + "]");

            if (!productInOrder) {
                response.put("success", false);
                response.put("message", "This product was not part of your order.");
                return ResponseEntity.badRequest().body(response);
            }

            // 4. Only allow reviews for DELIVERED orders
            if (order.getStatus() != Order.OrderStatus.DELIVERED) {
                response.put("success", false);
                response.put("message", "You can only review products from delivered orders.");
                return ResponseEntity.badRequest().body(response);
            }

            // 5. Submit the review
            reviewService.submitReview(username, request.getProductId(), request.getRating(), request.getComment());
            
            response.put("success", true);
            response.put("message", "Thank you! Your review has been submitted for approval.");
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Failed to submit review. Please try again.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * POST /api/customer/order/cancel/{orderId}
     * Cancel an order
     */
    @PostMapping("/order/cancel/{orderId}")
    public ResponseEntity<Map<String, Object>> cancelOrder(@PathVariable Long orderId) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            orderService.cancelOrder(orderId);
            
            response.put("success", true);
            response.put("message", "Order #" + orderId + " has been successfully cancelled. A refund process has been initiated.");
            return ResponseEntity.ok(response);
            
        } catch (IllegalStateException e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error cancelling order: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * POST /api/customer/order/return/{orderId}
     * Request return for an order
     */
    @PostMapping("/order/return/{orderId}")
    public ResponseEntity<Map<String, Object>> requestOrderReturn(@PathVariable Long orderId) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            orderService.returnOrder(orderId);
            
            response.put("success", true);
            response.put("message", "Return request for Order #" + orderId + " submitted successfully. Awaiting confirmation!");
            return ResponseEntity.ok(response);
            
        } catch (IllegalStateException e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error submitting return request: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // =========================================================================
    // 2. Profile Management
    // =========================================================================

    /**
     * GET /api/customer/profile
     * Get customer profile information
     */
    @GetMapping("/profile")
    public ResponseEntity<Map<String, Object>> getProfile(@AuthenticationPrincipal UserDetails userDetails) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            String username = userDetails.getUsername();
            Optional<Customer> customerOptional = userService.getCustomerDetailsByUsername(username);
            
            if (customerOptional.isEmpty()) {
                response.put("success", false);
                response.put("message", "Customer not found");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
            
            Customer customer = customerOptional.get();
            
            response.put("success", true);
            response.put("customer", customer);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error loading profile: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * PUT /api/customer/profile/update
     * Update customer profile
     */
    @PutMapping("/profile/update")
    public ResponseEntity<Map<String, Object>> updateProfile(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody RegistrationDTO profileDTO) {
        
        Map<String, Object> response = new HashMap<>();
        String currentUsername = userDetails.getUsername();

        try {
            userService.updateCustomerProfile(currentUsername, profileDTO);
            
            response.put("success", true);
            response.put("message", "Profile details updated successfully!");
            return ResponseEntity.ok(response);
            
        } catch (IllegalStateException e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "An unexpected error occurred during profile update.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * POST /api/customer/profile/change-password
     * Change customer password
     */
    @PostMapping("/profile/change-password")
    public ResponseEntity<Map<String, Object>> changePassword(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody ChangePasswordRequest request) {
        
        Map<String, Object> response = new HashMap<>();

        try {
            userService.changePassword(
                userDetails.getUsername(), 
                request.getCurrentPassword(), 
                request.getNewPassword(), 
                request.getConfirmPassword()
            );
            
            response.put("success", true);
            response.put("message", "Password changed successfully! Please log in with your new password.");
            response.put("requiresLogout", true);
            return ResponseEntity.ok(response);
            
        } catch (IllegalStateException e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "An unexpected error occurred during password change.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * POST /api/customer/profile/change-email/initiate
     * Initiate email change process
     */
    @PostMapping("/profile/change-email/initiate")
    public ResponseEntity<Map<String, Object>> initiateEmailChange(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody EmailChangeRequest request) {
        
        Map<String, Object> response = new HashMap<>();

        try {
            userService.initiateEmailChange(userDetails.getUsername(), request.getNewEmail());
            
            response.put("success", true);
            response.put("message", "Verification code sent to " + request.getNewEmail() + ".");
            response.put("newEmail", request.getNewEmail());
            return ResponseEntity.ok(response);
            
        } catch (IllegalStateException e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error initiating email change. Please try again.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * POST /api/customer/profile/change-email/finalize
     * Finalize email change with OTP
     */
    @PostMapping("/profile/change-email/finalize")
    public ResponseEntity<Map<String, Object>> finalizeEmailChange(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody EmailVerificationRequest request) {
        
        Map<String, Object> response = new HashMap<>();

        try {
            String currentUsername = userDetails.getUsername();
            userService.finalizeEmailChange(currentUsername, request.getNewEmail(), request.getOtp());
            
            response.put("success", true);
            response.put("message", "Your email address has been successfully updated to " + request.getNewEmail() + ". Please log in with your new email.");
            response.put("requiresLogout", true);
            return ResponseEntity.ok(response);
            
        } catch (IllegalStateException e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error finalizing email change.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * PUT /api/customer/profile/update-newsletter
     * Update newsletter subscription preference
     */
    @PutMapping("/profile/update-newsletter")
    public ResponseEntity<Map<String, Object>> updateNewsletterSubscription(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody NewsletterRequest request) {
        
        Map<String, Object> response = new HashMap<>();

        try {
            userService.updateNewsletterOptIn(userDetails.getUsername(), request.isOptIn());
            
            String status = request.isOptIn() ? "subscribed to" : "unsubscribed from";
            response.put("success", true);
            response.put("message", "You have successfully " + status + " the newsletter.");
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error updating newsletter status: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // =========================================================================
    // 3. Address Management
    // =========================================================================

    /**
     * GET /api/customer/addresses
     * Get all saved addresses
     */
    @GetMapping("/addresses")
    public ResponseEntity<Map<String, Object>> getSavedAddresses(@AuthenticationPrincipal UserDetails userDetails) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            String username = userDetails.getUsername();
            List<Address> addresses = addressService.getAddressesByUsername(username);
            
            response.put("success", true);
            response.put("addresses", addresses);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Could not load saved addresses.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * POST /api/customer/addresses/add
     * Add or update address
     */
    @PostMapping("/addresses/add")
    public ResponseEntity<Map<String, Object>> addOrUpdateAddress(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody Address address) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            addressService.saveAddress(userDetails.getUsername(), address);
            
            response.put("success", true);
            response.put("message", "Address saved successfully!");
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error saving address: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * DELETE /api/customer/addresses/delete/{id}
     * Delete an address
     */
    @DeleteMapping("/addresses/delete/{id}")
    public ResponseEntity<Map<String, Object>> deleteAddress(@PathVariable Long id) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            addressService.deleteAddress(id);
            
            response.put("success", true);
            response.put("message", "Address deleted successfully.");
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error deleting address.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // =========================================================================
    // 4. Coupons & Gift Cards
    // =========================================================================

    /**
     * GET /api/customer/coupons
     * Get all active coupons
     */
    @GetMapping("/coupons")
    public ResponseEntity<Map<String, Object>> getCouponsAndOffers() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            response.put("success", true);
            response.put("coupons", couponService.getAllActiveCoupons());
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Could not load coupons and offers.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * GET /api/customer/gift-cards
     * Get customer's gift cards
     */
    @GetMapping("/gift-cards")
    public ResponseEntity<Map<String, Object>> getGiftCards(@AuthenticationPrincipal UserDetails userDetails) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            String username = userDetails.getUsername();
            response.put("success", true);
            response.put("giftCards", giftCardService.getGiftCardsByUsername(username));
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Could not load gift card information.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // =========================================================================
    // Request/Response DTOs
    // =========================================================================

    public static class ReviewSubmissionRequest {
        private Long orderId;
        private Long productId;
        private int rating;
        private String comment;

        // Getters and Setters
        public Long getOrderId() { return orderId; }
        public void setOrderId(Long orderId) { this.orderId = orderId; }
        public Long getProductId() { return productId; }
        public void setProductId(Long productId) { this.productId = productId; }
        public int getRating() { return rating; }
        public void setRating(int rating) { this.rating = rating; }
        public String getComment() { return comment; }
        public void setComment(String comment) { this.comment = comment; }
    }

    public static class ChangePasswordRequest {
        private String currentPassword;
        private String newPassword;
        private String confirmPassword;

        public String getCurrentPassword() { return currentPassword; }
        public void setCurrentPassword(String currentPassword) { this.currentPassword = currentPassword; }
        public String getNewPassword() { return newPassword; }
        public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
        public String getConfirmPassword() { return confirmPassword; }
        public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }
    }

    public static class EmailChangeRequest {
        private String newEmail;

        public String getNewEmail() { return newEmail; }
        public void setNewEmail(String newEmail) { this.newEmail = newEmail; }
    }

    public static class EmailVerificationRequest {
        private String newEmail;
        private String otp;

        public String getNewEmail() { return newEmail; }
        public void setNewEmail(String newEmail) { this.newEmail = newEmail; }
        public String getOtp() { return otp; }
        public void setOtp(String otp) { this.otp = otp; }
    }

    public static class NewsletterRequest {
        private boolean optIn;

        public boolean isOptIn() { return optIn; }
        public void setOptIn(boolean optIn) { this.optIn = optIn; }
    }
}