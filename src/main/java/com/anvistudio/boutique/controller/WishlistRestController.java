package com.anvistudio.boutique.controller;

import com.anvistudio.boutique.model.User;
import com.anvistudio.boutique.model.Wishlist;
import com.anvistudio.boutique.service.UserService;
import com.anvistudio.boutique.service.WishlistService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST API Controller for Wishlist operations.
 */
@RestController
@RequestMapping("/api")
public class WishlistRestController {

    private final WishlistService wishlistService;
    private final UserService userService;

    public WishlistRestController(WishlistService wishlistService, UserService userService) {
        this.wishlistService = wishlistService;
        this.userService = userService;
    }

    /**
     * GET /api/wishlist
     * Retrieves the user's wishlist. Requires authentication.
     * Returns 401 if not authenticated.
     */
    @GetMapping("/wishlist")
    public ResponseEntity<Map<String, Object>> viewWishlist(@AuthenticationPrincipal UserDetails userDetails) {
        Map<String, Object> response = new HashMap<>();

        try {
            // Check if user is authenticated
            if (userDetails == null || "anonymousUser".equals(userDetails.getUsername())) {
                response.put("authenticated", false);
                response.put("message", "User not authenticated. Please log in.");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }

            // Get authenticated user
            User user = userService.findUserByUsername(userDetails.getUsername())
                    .orElseThrow(() -> new RuntimeException("Authenticated user not found in DB."));

            // Get wishlist items
            List<Wishlist> items = wishlistService.getWishlistItems(user.getId());

            response.put("authenticated", true);
            response.put("wishlistItems", items);
            response.put("itemCount", items.size());
            
            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            response.put("authenticated", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            
        } catch (Exception e) {
            response.put("error", true);
            response.put("message", "Error retrieving wishlist: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * POST /api/wishlist/add/{productId}
     * Adds a product to the wishlist.
     * Matches the Path Variable used in original controller.
     * Requires authentication.
     */
    @PostMapping("/wishlist/add/{productId}")
    public ResponseEntity<Map<String, Object>> addProductToWishlist(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long productId) {
        
        Map<String, Object> response = new HashMap<>();

        try {
            // Check authentication
            if (userDetails == null || "anonymousUser".equals(userDetails.getUsername())) {
                response.put("success", false);
                response.put("message", "Please log in to add items to wishlist.");
                response.put("requiresLogin", true);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }

            // Add to wishlist
            wishlistService.addToWishlist(userDetails.getUsername(), productId);

            response.put("success", true);
            response.put("message", "Product added to your Wishlist!");
            
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Could not add item to wishlist: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * DELETE /api/wishlist/remove/{productId}
     * Removes a product from the wishlist.
     * Using DELETE method for RESTful design (changed from POST).
     */
    @DeleteMapping("/wishlist/remove/{productId}")
    public ResponseEntity<Map<String, Object>> removeProductFromWishlist(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long productId) {
        
        Map<String, Object> response = new HashMap<>();

        try {
            // Check authentication
            if (userDetails == null || "anonymousUser".equals(userDetails.getUsername())) {
                response.put("success", false);
                response.put("message", "User not authenticated.");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }

            // Get authenticated user
            User user = userService.findUserByUsername(userDetails.getUsername())
                    .orElseThrow(() -> new RuntimeException("Authenticated user not found in DB."));

            // Remove from wishlist
            wishlistService.removeFromWishlist(user.getId(), productId);

            response.put("success", true);
            response.put("message", "Item removed from Wishlist.");
            
            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Could not remove item from wishlist: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}