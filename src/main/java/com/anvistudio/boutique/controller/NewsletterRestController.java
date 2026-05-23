package com.anvistudio.boutique.controller;

import com.anvistudio.boutique.service.NotificationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * REST API Controller to handle newsletter subscriptions from non-registered users.
 */
@RestController
@RequestMapping("/api/public/newsletter")
public class NewsletterRestController {

    private final NotificationService notificationService;

    public NewsletterRestController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * Handles the subscription request from the footer form (REST API version).
     * 
     * @param request The newsletter subscription request containing email
     * @return ResponseEntity with success/error message
     */
    @PostMapping("/subscribe")
    public ResponseEntity<Map<String, Object>> subscribe(@RequestBody NewsletterSubscriptionRequest request) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            String email = request.getEmail();
            
            // Validation
            if (email == null || email.trim().isEmpty() || !email.contains("@")) {
                throw new IllegalStateException("Please enter a valid email address.");
            }
            
            // This is the correct logic for non-registered users (saves to generic table)
            notificationService.subscribeEmail(email);
            
            response.put("success", true);
            response.put("message", "Thank you! You are now subscribed to our exclusive offers.");
            return ResponseEntity.ok(response);
            
        } catch (IllegalStateException e) {
            // Handle specific error cases
            String message = e.getMessage();
            
            if (message.contains("already registered as a customer")) {
                // Provide actionable message for registered customers
                response.put("success", false);
                response.put("message", "You are already a registered customer. Please log in and manage your subscription preferences on your profile page.");
                response.put("isRegisteredUser", true);
                return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
            } else {
                response.put("success", false);
                response.put("message", message);
                return ResponseEntity.badRequest().body(response);
            }
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Subscription failed. Please try again later.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Inner class for newsletter subscription request
     */
    public static class NewsletterSubscriptionRequest {
        private String email;

        public NewsletterSubscriptionRequest() {
        }

        public NewsletterSubscriptionRequest(String email) {
            this.email = email;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }
    }
}