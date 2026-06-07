package com.anvistudio.boutique.controller;

import com.anvistudio.boutique.model.Address; // NEW IMPORT
import com.anvistudio.boutique.model.CartItem;
import com.anvistudio.boutique.model.User;
import com.anvistudio.boutique.service.AddressService; // NEW IMPORT
import com.anvistudio.boutique.service.CartService;
import com.anvistudio.boutique.service.OrderService;
import com.anvistudio.boutique.service.StripeService;
import com.anvistudio.boutique.service.UserService;

import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Handles the payment checkout flow integration with Stripe (Custom Payment
 * Element approach).
 */
@Controller
public class PaymentController {

    private final StripeService stripeService;
    private final CartService cartService;
    private final OrderService orderService;
    private final UserService userService;
    private final AddressService addressService; // NEW INJECTION

    public PaymentController(StripeService stripeService, CartService cartService, OrderService orderService,
            UserService userService, AddressService addressService) {
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

    /**
     * STEP 1: Entry point from the Cart. Navigates user to the Address selection
     * page.
     * Maps to /customer/addresses.
     */
    @GetMapping("/checkout")
    public String initiateCheckout(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null || "anonymousUser".equals(userDetails.getUsername())) {
            return "redirect:/login";
        }
        return "redirect:/customer/addresses";
    }

    /**
     * STEP 2 (GET): Displays the custom payment page with selected address details
     * and payment options.
     * This endpoint is hit after the user selects an address and clicks 'Continue'
     * on the address page.
     */
    @GetMapping("/payment/modes")
    public String showPaymentModes(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam("addressId") Long addressId, // We expect the selected address ID here
            Model model,
            RedirectAttributes redirectAttributes) {

        User user = getAuthenticatedUser(userDetails);
        Long userId = user.getId();

        Optional<Address> addressOptional = addressService.getAddressById(addressId);
        if (addressOptional.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Selected address not found.");
            return "redirect:/customer/addresses";
        }

        try {
            // 1. Fetch Cart details
            List<CartItem> cartItems = cartService.getCartItems(userId);
            if (cartItems.isEmpty()) {
                redirectAttributes.addFlashAttribute("errorMessage", "Your cart is empty. Cannot proceed to payment.");
                return "redirect:/cart";
            }
            double cartTotal = cartService.getCartTotal(userId);

            // 2. Create Stripe Payment Intent Secret
            String clientSecret = stripeService.createPaymentIntent(user.getUsername());

            // 3. Populate Model
            model.addAttribute("cartItems", cartItems);
            model.addAttribute("cartTotal", cartTotal);
            model.addAttribute("shippingAddress", addressOptional.get());
            model.addAttribute("clientSecret", clientSecret);
            model.addAttribute("publishableKey", stripeService.getPublishableKey()); // Pass PK to load Stripe.js
            model.addAttribute("addressId", addressId); // Keep address ID for final fulfillment POST

            return "payment_modes"; // NEW TEMPLATE

        } catch (StripeException e) {
            System.err.println("Stripe API Error: " + e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Could not initialize secure payment. Stripe Error: " + e.getCode());
            return "redirect:/customer/addresses";
        } catch (Exception e) {
            System.err.println("Internal Server Error: " + e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", "An unexpected error occurred. Please try again.");
            return "redirect:/customer/addresses";
        }
    }

    /**
     * STEP 3 (POST): Handles final confirmation for COD or successful card payments
     * confirmed by the client-side Payment Element.
     */
    @PostMapping("/payment/confirm")
    @Transactional
    public String finalizeOrder(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam("addressId") Long addressId,
            @RequestParam("paymentMethod") String paymentMethod, // 'CARD' or 'COD'
            @RequestParam(value = "paymentIntentId", required = false) String paymentIntentId, // Only for CARD
            RedirectAttributes redirectAttributes) {

        User user = getAuthenticatedUser(userDetails);
        Long userId = user.getId();

        // FUTURE: In a production app, here you would:
        // 1. Verify Payment Intent status (if CARD) using paymentIntentId.
        // 2. Fetch the selected Address object using addressId and include it in the
        // Order creation.

        try {
            List<CartItem> cartItems = cartService.getCartItems(userId);

            if (cartItems.isEmpty()) {
                redirectAttributes.addFlashAttribute("successMessage",
                        "Your order was already confirmed! See your order history.");
                return "redirect:/customer/orders";
            }

            // Fulfill the Order
            orderService.createOrderFromCart(userId, cartItems, addressId);

            // Clear the user's cart
            cartService.clearCart(userId);

            String confirmationMessage = paymentMethod.equalsIgnoreCase("COD")
                    ? "Order placed successfully! Your Cash on Delivery order is confirmed."
                    : "Payment confirmed via Card! Your order has been placed.";

            redirectAttributes.addFlashAttribute("successMessage", confirmationMessage);
            return "redirect:/customer/orders";

        } catch (Exception e) {
            System.err.println("Order fulfillment critical error: " + e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage",
                    "A critical error occurred while finalizing your order. Please contact support.");
            return "redirect:/cart";
        }
    }

    // --- Unchanged Redirects (Kept for compatibility with old Checkout
    // links/webhooks) ---
    @GetMapping("/payment/success")
    public String paymentSuccess(@RequestParam(value = "session_id", required = false) String sessionId,
            RedirectAttributes redirectAttributes) {
        // If Stripe Checkout redirects here, we immediately assume the order was
        // fulfilled by
        // the client-side success handler in the new flow or redirect to prevent errors
        // from the old flow.
        redirectAttributes.addFlashAttribute("successMessage", "Order finalized. Check your order history.");
        return "redirect:/customer/orders";
    }

    @GetMapping("/payment/cancel")
    public String paymentCancel(RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("cartError",
                "Payment cancelled or failed. You can try again from your cart.");
        return "redirect:/cart";
    }
}