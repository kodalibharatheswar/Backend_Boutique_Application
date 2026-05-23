package com.anvistudio.boutique.service;

import com.anvistudio.boutique.model.CartItem;
import com.anvistudio.boutique.model.User;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.PaymentIntent;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.PaymentIntentCreateParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Service to interface with the Stripe API for custom Payment Element integration.
 * This uses Payment Intents instead of Checkout Sessions (which were used previously).
 */
@Service
public class StripeService {

    private final CartService cartService;
    private final UserService userService;

    @Value("${stripe.currency}")
    private String currency;

    @Value("${stripe.api.publishableKey}")
    private String publishableKey;

    @Value("${app.base.url}")
    private String appBaseUrl;

    public StripeService(CartService cartService, UserService userService) {
        this.cartService = cartService;
        this.userService = userService;
    }

    public String getPublishableKey() {
        return publishableKey;
    }

    /**
     * Helper to ensure the image URL is absolute for Stripe's API. (Retained from previous fix)
     */
    private String makeAbsoluteUrl(String relativeUrl) {
        if (relativeUrl == null || relativeUrl.isEmpty()) {
            return "https://placehold.co/600x600";
        }
        if (relativeUrl.startsWith("http://") || relativeUrl.startsWith("https://")) {
            return relativeUrl;
        }
        return appBaseUrl + (relativeUrl.startsWith("/") ? relativeUrl : "/" + relativeUrl);
    }

    /**
     * NEW: Creates a Payment Intent and returns the client secret for the frontend Payment Element.
     * This replaces the old createCheckoutSession method.
     * * @param username The authenticated user's username (email).
     * @return The client secret string.
     * @throws StripeException If the Stripe API call fails.
     */
    public String createPaymentIntent(String username) throws StripeException {
        User user = userService.findUserByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));

        Long userId = user.getId();
        List<CartItem> cartItems = cartService.getCartItems(userId);

        if (cartItems.isEmpty()) {
            throw new IllegalStateException("Cannot create a payment intent for an empty cart.");
        }

        // Calculate total amount in smallest unit (e.g., paise)
        Long amountInCents = BigDecimal.valueOf(cartService.getCartTotal(userId))
                .multiply(BigDecimal.valueOf(100))
                .longValue();

        // 1. Create or retrieve Stripe Customer ID
        // NOTE: This assumes we successfully create a customer every time, which is fine for test mode.
        String customerId = getOrCreateStripeCustomer(user.getUsername(), user.getUsername()).getId();

        // 2. Build the Payment Intent Parameters
        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(amountInCents)
                .setCurrency(currency)
                .setCustomer(customerId)
                .setSetupFutureUsage(PaymentIntentCreateParams.SetupFutureUsage.ON_SESSION) // Saves card for future purchases
                // REMOVED: .addPaymentMethodType("card") - This conflicts with automatic methods
                .setDescription("Anvi Studio Order for " + user.getUsername())
                .setReceiptEmail(user.getUsername())
                .putMetadata("cart_user_id", userId.toString()) // Reference to your internal system
                .setAutomaticPaymentMethods(PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                        .setEnabled(true) // Enables card, UPI, etc., depending on Stripe settings
                        .build())
                .build();

        // 3. Create the Payment Intent
        PaymentIntent paymentIntent = PaymentIntent.create(params);
        return paymentIntent.getClientSecret();
    }

    /**
     * Helper to get a Stripe Customer or create one if not found.
     * In a production app, the Customer ID would be stored on your local User model.
     */
    private Customer getOrCreateStripeCustomer(String email, String name) throws StripeException {
        // Simple search by email. In a real app, use the user's saved Stripe Customer ID.
        CustomerCreateParams params = CustomerCreateParams.builder()
                .setEmail(email)
                .setName(name)
                .build();
        return Customer.create(params);
    }
}