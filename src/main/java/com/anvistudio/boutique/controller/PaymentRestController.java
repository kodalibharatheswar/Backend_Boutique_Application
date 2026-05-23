package com.anvistudio.boutique.controller;

import com.anvistudio.boutique.model.Address;
import com.anvistudio.boutique.model.CartItem;
import com.anvistudio.boutique.model.User;
import com.anvistudio.boutique.service.AddressService;
import com.anvistudio.boutique.service.CartService;
import com.anvistudio.boutique.service.OrderService;
import com.anvistudio.boutique.service.StripeService;
import com.anvistudio.boutique.service.UserService;
import com.stripe.exception.StripeException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/payment")
public class PaymentRestController {

    private final StripeService stripeService;
    private final CartService cartService;
    private final OrderService orderService;
    private final UserService userService;
    private final AddressService addressService;

    public PaymentRestController(StripeService stripeService, CartService cartService, OrderService orderService, UserService userService, AddressService addressService) {
        this.stripeService = stripeService;
        this.cartService = cartService;
        this.orderService = orderService;
        this.userService = userService;
        this.addressService = addressService;
    }

    private User getAuthenticatedUser(UserDetails userDetails) {
        if (userDetails == null) {
            throw new IllegalArgumentException("User not authenticated.");
        }
        return userService.findUserByUsername(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Authenticated user not found in DB."));
    }

    @GetMapping("/setup")
    public ResponseEntity<?> setupPayment(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam("addressId") Long addressId) {

        try {
            User user = getAuthenticatedUser(userDetails);
            Long userId = user.getId();

            Optional<Address> addressOptional = addressService.getAddressById(addressId);
            if (addressOptional.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Selected address not found."));
            }

            List<CartItem> cartItems = cartService.getCartItems(userId);
            if (cartItems.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Your cart is empty."));
            }

            double cartTotal = cartService.getCartTotal(userId);
            String clientSecret = stripeService.createPaymentIntent(user.getUsername());

            Map<String, Object> response = new HashMap<>();
            response.put("cartItems", cartItems);
            response.put("cartTotal", cartTotal);
            response.put("cartItemsCount", cartItems.stream().mapToInt(CartItem::getQuantity).sum());
            response.put("shippingAddress", addressOptional.get());
            response.put("clientSecret", clientSecret);
            response.put("publishableKey", stripeService.getPublishableKey());

            return ResponseEntity.ok(response);

        } catch (StripeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Stripe API Error: " + e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Internal Server Error: " + e.getMessage()));
        }
    }

    @PostMapping("/confirm")
    public ResponseEntity<?> confirmPayment(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody Map<String, Object> payload) {

        try {
            User user = getAuthenticatedUser(userDetails);
            Long userId = user.getId();

            Long addressId = payload.containsKey("addressId") ? Long.valueOf(payload.get("addressId").toString()) : null;
            String paymentMethod = payload.containsKey("paymentMethod") ? payload.get("paymentMethod").toString() : "";
            
            if (addressId == null || paymentMethod.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Missing addressId or paymentMethod"));
            }

            List<CartItem> cartItems = cartService.getCartItems(userId);
            if (cartItems.isEmpty()) {
                return ResponseEntity.ok(Map.of("message", "Order already confirmed or cart empty."));
            }

            orderService.createOrderFromCart(userId, cartItems, addressId);
            cartService.clearCart(userId);

            return ResponseEntity.ok(Map.of("message", "Order confirmed successfully."));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error finalizing order: " + e.getMessage()));
        }
    }
}
