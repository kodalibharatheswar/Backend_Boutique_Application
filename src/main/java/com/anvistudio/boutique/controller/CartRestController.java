package com.anvistudio.boutique.controller;

import com.anvistudio.boutique.model.CartItem;
import com.anvistudio.boutique.model.User;
import com.anvistudio.boutique.service.CartService;
import com.anvistudio.boutique.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST API Controller for Shopping Cart operations.
 */
@RestController
@RequestMapping("/api")
public class CartRestController {

    private final CartService cartService;
    private final UserService userService;

    public CartRestController(CartService cartService, UserService userService) {
        this.cartService = cartService;
        this.userService = userService;
    }

    /**
     * Helper method to get authenticated user.
     * Throws exception if user is not authenticated.
     */
    private User getAuthenticatedUser(UserDetails userDetails) {
        if (userDetails == null) {
            throw new IllegalArgumentException("User not authenticated.");
        }
        return userService.findUserByUsername(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Authenticated user not found in DB."));
    }

    /**
     * GET /api/cart
     * Retrieves the user's shopping cart. Requires authentication.
     * Returns 401 if not authenticated.
     */
    @GetMapping("/cart")
    public ResponseEntity<Map<String, Object>> viewCart(@AuthenticationPrincipal UserDetails userDetails) {
        Map<String, Object> response = new HashMap<>();

        try {
            // Check if user is authenticated
            if (userDetails == null || "anonymousUser".equals(userDetails.getUsername())) {
                response.put("authenticated", false);
                response.put("message", "User not authenticated. Please log in.");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }

            // Get authenticated user
            User user = getAuthenticatedUser(userDetails);

            // Get cart items and total
            List<CartItem> items = cartService.getCartItems(user.getId());
            double total = cartService.getCartTotal(user.getId());

            response.put("authenticated", true);
            response.put("cartItems", items);
            response.put("cartTotal", total);
            response.put("itemCount", items.size());
            
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            response.put("authenticated", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            
        } catch (Exception e) {
            response.put("error", true);
            response.put("message", "Error retrieving cart: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * POST /api/cart/add
     * Adds a product to the cart.
     * Requires authentication.
     */
    @PostMapping("/cart/add")
    public ResponseEntity<Map<String, Object>> addProductToCart(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody AddToCartRequest request) {
        
        Map<String, Object> response = new HashMap<>();

        try {
            // Check authentication status
            if (userDetails == null || "anonymousUser".equals(userDetails.getUsername())) {
                response.put("success", false);
                response.put("message", "Please log in to add items to cart.");
                response.put("requiresLogin", true);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }

            // Add product to cart
            cartService.addProductToCart(
                userDetails.getUsername(), 
                request.getProductId(), 
                request.getQuantity() != null ? request.getQuantity() : 1
            );

            response.put("success", true);
            response.put("message", "Item added to cart successfully!");
            
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error adding item to cart: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * DELETE /api/cart/remove/{itemId}
     * Removes an item completely from the cart using the CartItem ID.
     */
    @DeleteMapping("/cart/remove/{itemId}")
    public ResponseEntity<Map<String, Object>> removeItemFromCart(
            @PathVariable Long itemId,
            @AuthenticationPrincipal UserDetails userDetails) {
        
        Map<String, Object> response = new HashMap<>();

        try {
            // Check authentication
            if (userDetails == null || "anonymousUser".equals(userDetails.getUsername())) {
                response.put("success", false);
                response.put("message", "User not authenticated.");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }

            // Remove item from cart
            cartService.removeItem(itemId);

            response.put("success", true);
            response.put("message", "Item removed from cart successfully.");
            
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error removing item: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * PUT /api/cart/update
     * Updates the quantity of a specific item using the CartItem ID.
     */
    @PutMapping("/cart/update")
    public ResponseEntity<Map<String, Object>> updateCartItemQuantity(
            @RequestBody UpdateCartItemRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        
        Map<String, Object> response = new HashMap<>();

        try {
            // Check authentication
            if (userDetails == null || "anonymousUser".equals(userDetails.getUsername())) {
                response.put("success", false);
                response.put("message", "User not authenticated.");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }

            // Update item quantity
            cartService.updateItemQuantity(request.getItemId(), request.getQuantity());

            response.put("success", true);
            response.put("message", "Cart item updated successfully.");
            
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error updating cart item: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Inner class for Add to Cart request
     */
    public static class AddToCartRequest {
        private Long productId;
        private Integer quantity;

        public AddToCartRequest() {
        }

        public AddToCartRequest(Long productId, Integer quantity) {
            this.productId = productId;
            this.quantity = quantity;
        }

        public Long getProductId() {
            return productId;
        }

        public void setProductId(Long productId) {
            this.productId = productId;
        }

        public Integer getQuantity() {
            return quantity;
        }

        public void setQuantity(Integer quantity) {
            this.quantity = quantity;
        }
    }

    /**
     * Inner class for Update Cart Item request
     */
    public static class UpdateCartItemRequest {
        private Long itemId;
        private Integer quantity;

        public UpdateCartItemRequest() {
        }

        public UpdateCartItemRequest(Long itemId, Integer quantity) {
            this.itemId = itemId;
            this.quantity = quantity;
        }

        public Long getItemId() {
            return itemId;
        }

        public void setItemId(Long itemId) {
            this.itemId = itemId;
        }

        public Integer getQuantity() {
            return quantity;
        }

        public void setQuantity(Integer quantity) {
            this.quantity = quantity;
        }
    }
}