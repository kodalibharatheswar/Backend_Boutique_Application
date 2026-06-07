package com.anvistudio.boutique.controller;

import com.anvistudio.boutique.model.ContactMessage;
import com.anvistudio.boutique.model.Customer;
import com.anvistudio.boutique.service.ContactService;
import com.anvistudio.boutique.service.ProductService;
import com.anvistudio.boutique.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * REST API Controller for Home/Public endpoints.
 * Provides JSON-based endpoints for the React frontend.
 */
@RestController
@RequestMapping("/api/public")
public class HomeRestController {

    private final ProductService productService;
    private final ContactService contactService;
    private final UserService userService;

    public HomeRestController(ProductService productService, ContactService contactService, UserService userService) {
        this.productService = productService;
        this.contactService = contactService;
        this.userService = userService;
    }

    /**
     * GET /api/public/home
     * Get home page data with products and user info (if authenticated)
     * 
     * Response: {
     * authenticated: boolean,
     * customer: { firstName, lastName, email },
     * products: [...]
     * }
     */
    @GetMapping("/home")
    public ResponseEntity<Map<String, Object>> getHomeData() {
        Map<String, Object> response = new HashMap<>();

        // Check authentication
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean isAuthenticated = authentication != null &&
                authentication.isAuthenticated() &&
                !authentication.getPrincipal().equals("anonymousUser");

        response.put("authenticated", isAuthenticated);

        if (isAuthenticated) {
            String username = authentication.getName();

            // Fetch Customer details if authenticated
            Optional<Customer> customerOptional = userService.getCustomerDetailsByUsername(username);
            if (customerOptional.isPresent()) {
                Customer customer = customerOptional.get();
                Map<String, Object> customerData = new HashMap<>();
                customerData.put("email", customer.getUser().getUsername());
                // Add customer-specific fields if available
                response.put("customer", customerData);
            } else {
                // Fallback - just username
                Map<String, Object> userData = new HashMap<>();
                userData.put("email", username);
                response.put("customer", userData);
            }
        }

        // Fetch products for display
        response.put("products", productService.getDisplayableProducts());

        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/public/products
     * Get all displayable products, optionally filtered and sorted
     */
    @GetMapping("/products")
    public ResponseEntity<Map<String, Object>> getProducts(
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "sortBy", required = false, defaultValue = "latest") String sortBy,
            @RequestParam(value = "minPrice", required = false) Double minPrice,
            @RequestParam(value = "maxPrice", required = false) Double maxPrice,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "color", required = false) String color,
            @RequestParam(value = "keyword", required = false) String keyword) {

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);

        if (category != null || minPrice != null || maxPrice != null || status != null || color != null
                || keyword != null || (sortBy != null && !sortBy.equals("latest"))) {
            response.put("products",
                    productService.getFilteredProducts(category, sortBy, minPrice, maxPrice, status, color, keyword));
        } else {
            response.put("products", productService.getDisplayableProducts());
        }

        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/public/categories
     * Utility endpoint to get standard categories
     */
    @GetMapping("/categories")
    public ResponseEntity<String[]> getCategories() {
        return ResponseEntity.ok(new String[] {
                "Sarees", "Lehengas", "Kurtis", "Long Frocks", "Mom & Me", "Crop Top – Skirts",
                "Handlooms", "Casual Frocks", "Ready To Wear", "Dupattas", "Kids wear",
                "Dress Material", "Blouses", "Fabrics"
        });
    }

    /**
     * GET /api/public/colors
     * Utility endpoint to get standard filter colors
     */
    @GetMapping("/colors")
    public ResponseEntity<String[]> getColors() {
        return ResponseEntity.ok(new String[] {
                "Black", "Blue", "Green", "Red", "Pink", "Yellow", "Maroon", "Purple", "White", "Gray", "Brown",
                "Orange"
        });
    }

    /**
     * GET /api/public/products/{id}
     * Get a single product by ID along with its reviews and related products.
     */
    @GetMapping("/products/{id}")
    public ResponseEntity<Map<String, Object>> getProductById(@PathVariable Long id) {
        Optional<com.anvistudio.boutique.model.Product> productOptional = productService.getProductById(id);

        if (productOptional.isEmpty() || !productOptional.get().getIsAvailable()) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Product not found");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
        }

        com.anvistudio.boutique.model.Product product = productOptional.get();

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("product", product);

        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/public/contact
     * Submit contact form
     * 
     * Request Body: { name, email, subject, message }
     * Response: { success: boolean, message: string }
     */
    @PostMapping("/contact")
    public ResponseEntity<Map<String, Object>> submitContactForm(@RequestBody ContactMessage contactMessage) {
        Map<String, Object> response = new HashMap<>();

        try {
            contactService.saveMessage(contactMessage);
            response.put("success", true);
            response.put("message", "Thank you for your message! We will get back to you shortly.");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "There was an error submitting your message: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * GET /api/public/about
     * Get about page content
     * 
     * Response: { success: boolean, content: string }
     */
    @GetMapping("/about")
    public ResponseEntity<Map<String, Object>> getAboutContent() {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("title", "About anvi Studio Boutique");
        response.put("content", "Your destination for exquisite traditional and contemporary ethnic wear.");
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/public/policies/return
     * Get return policy content
     * 
     * Response: { success: boolean, policy: string }
     */
    @GetMapping("/policies/return")
    public ResponseEntity<Map<String, Object>> getReturnPolicy() {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("title", "Return Policy");
        response.put("policy", "Return policy content here...");
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/public/policies/privacy
     * Get privacy policy content
     * 
     * Response: { success: boolean, policy: string }
     */
    @GetMapping("/policies/privacy")
    public ResponseEntity<Map<String, Object>> getPrivacyPolicy() {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("title", "Privacy Policy");
        response.put("policy", "Privacy policy content here...");
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/public/policies/terms
     * Get terms and conditions content
     * 
     * Response: { success: boolean, policy: string }
     */
    @GetMapping("/policies/terms")
    public ResponseEntity<Map<String, Object>> getTermsAndConditions() {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("title", "Terms and Conditions");
        response.put("policy", "Terms and conditions content here...");
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/public/policies/shipping
     * Get shipping policy content
     * 
     * Response: { success: boolean, policy: string }
     */
    @GetMapping("/policies/shipping")
    public ResponseEntity<Map<String, Object>> getShippingPolicy() {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("title", "Shipping Policy");
        response.put("policy", "Shipping policy content here...");
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/public/custom-request-info
     * Get information about custom requests
     * 
     * Response: { success: boolean, info: string }
     */
    @GetMapping("/custom-request-info")
    public ResponseEntity<Map<String, Object>> getCustomRequestInfo() {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("title", "Custom Request");
        response.put("info", "Submit your custom tailoring requirements here...");
        return ResponseEntity.ok(response);
    }
}