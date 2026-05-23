package com.anvistudio.boutique.config;

import com.stripe.Stripe;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class to initialize the Stripe API key globally on application startup.
 */
@Configuration
public class StripeConfig {

    // Reads the secret key from application.properties
    @Value("${stripe.api.secretKey}")
    private String stripeSecretKey;

    /**
     * Initializes the Stripe API key. This method runs after bean properties are set.
     */
    @PostConstruct
    public void init() {
        // Set the API key globally in the Stripe SDK
        Stripe.apiKey = stripeSecretKey;
        System.out.println("STRIPE INFO: Stripe API key initialized successfully.");
    }
}