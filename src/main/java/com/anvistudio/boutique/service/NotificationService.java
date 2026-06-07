package com.anvistudio.boutique.service;

import com.anvistudio.boutique.model.Customer;
import com.anvistudio.boutique.model.Product;
import com.anvistudio.boutique.model.User;
import com.anvistudio.boutique.model.NewsletterSubscription;
import com.anvistudio.boutique.repository.CustomerRepository;
import com.anvistudio.boutique.repository.NewsletterSubscriptionRepository;
import com.anvistudio.boutique.repository.UserRepository; // New Import
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class NotificationService {

    private final NewsletterSubscriptionRepository subscriptionRepository;
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final JavaMailSender javaMailSender;

    @Value("${app.base.url:http://localhost:8080}")
    private String appBaseUrl;

    public NotificationService(NewsletterSubscriptionRepository subscriptionRepository,
            CustomerRepository customerRepository,
            UserRepository userRepository, // Injected
            JavaMailSender javaMailSender) {
        this.subscriptionRepository = subscriptionRepository;
        this.customerRepository = customerRepository;
        this.userRepository = userRepository;
        this.javaMailSender = javaMailSender;
    }

    /**
     * Adds a new email to the newsletter subscriptions (from footer form).
     * FIX: Use userRepository to check for existing customers.
     */
    @Transactional
    public void subscribeEmail(String email) {
        if (email == null || email.trim().isEmpty() || !email.contains("@")) {
            throw new IllegalStateException("Please enter a valid email address.");
        }

        // 1. Check if the email already exists as a registered user
        if (userRepository.findByUsername(email).isPresent()) {
            // Note: If they are a registered user, they should manage their subscription in
            // their profile.
            // We just provide a warning here if they try to use the public form.
            throw new IllegalStateException(
                    "This email is already registered as a customer. Please check your profile to manage newsletter settings.");
        }

        // 2. Check if the email is already in the general subscription list
        Optional<NewsletterSubscription> existing = subscriptionRepository.findByEmail(email);

        if (existing.isPresent()) {
            NewsletterSubscription sub = existing.get();
            if (Boolean.TRUE.equals(sub.getIsActive())) {
                throw new IllegalStateException("You are already subscribed.");
            } else {
                // Re-activate subscription
                sub.setIsActive(true);
                subscriptionRepository.save(sub);
            }
        } else {
            // New subscription
            subscriptionRepository.save(new NewsletterSubscription(email));
        }
    }

    /**
     * Finds all unique emails for notification:
     * 1. Registered Customers who opted-in (`newsletterOptIn = true`)
     * 2. Non-registered Subscribers from the footer table (`isActive = true`)
     * 
     * @return Set of unique email addresses.
     */
    private Set<String> getAllActiveSubscriberEmails() {
        Set<String> emails = new HashSet<>();

        // 1. Get emails from registered customers who opted-in
        List<Customer> customers = customerRepository.findAll().stream()
                .filter(Customer::getNewsletterOptIn)
                .collect(Collectors.toList());

        for (Customer customer : customers) {
            // Ensure the user account itself is enabled and present
            Optional<User> userOptional = userRepository.findById(customer.getUser().getId());
            if (userOptional.isPresent()) {
                emails.add(userOptional.get().getUsername());
            }
        }

        // 2. Get emails from general newsletter subscribers
        List<NewsletterSubscription> subscribers = subscriptionRepository.findByIsActiveTrue();
        for (NewsletterSubscription subscriber : subscribers) {
            emails.add(subscriber.getEmail());
        }

        return emails;
    }

    /**
     * Sends an exclusive offer notification for a new/updated sale product.
     * 
     * @param product The product now on sale/clearance.
     */
    public void sendSaleNotification(Product product) {
        Set<String> recipientEmails = getAllActiveSubscriberEmails();

        if (recipientEmails.isEmpty()) {
            System.out.println("NOTIFICATION: No active newsletter subscribers found.");
            return;
        }

        String offerType = product.isClearance() ? "🔥 Clearance Sale" : "✨ Exclusive Offer";
        String subject = String.format("%s: New Product on Sale! - %s", offerType, product.getName());

        // Note: Configured via app.base.url in application.properties
        String productUrl = appBaseUrl + "/products/" + product.getId();

        String body = String.format(
                "Hello valued customer,\n\n" +
                        "We are thrilled to announce a new product is now on %s!\n\n" +
                        "Product: %s (%s)\n" +
                        "Category: %s\n" +
                        "Original Price: ₹ %.2f\n" +
                        "Discount: %d%%\n" +
                        "New Price: ₹ %.2f\n\n" +
                        "Description: %s\n\n" +
                        "Shop now before it sells out!\n" +
                        "[Link to Product: %s]\n\n" + // Use the new product URL
                        "Thank you for being an anvi Studio subscriber!",
                offerType,
                product.getName(),
                product.getSku(),
                product.getCategory(),
                product.getPrice().doubleValue(),
                product.getDiscountPercent(),
                product.getDiscountedPrice().doubleValue(),
                product.getDescription(),
                productUrl // Use the new product URL
        );

        // Send a separate email to each recipient
        for (String email : recipientEmails) {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("anvi Studio Offers <bharath161099@gmail.com>");
            message.setTo(email);
            message.setSubject(subject);
            message.setText(body);

            try {
                javaMailSender.send(message);
                System.out.println("NOTIFICATION: Sent sale email to " + email);
            } catch (Exception e) {
                System.err.println("SMTP ERROR: Failed to send sale email to " + email + ": " + e.getMessage());
            }
        }
    }
}