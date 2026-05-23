package com.anvistudio.boutique.controller;

import com.anvistudio.boutique.model.Product;
import com.anvistudio.boutique.service.ProductService;
import com.anvistudio.boutique.service.ReviewService; // NEW IMPORT
import org.springframework.security.core.annotation.AuthenticationPrincipal; // NEW IMPORT
import org.springframework.security.core.userdetails.UserDetails; // NEW IMPORT
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes; // NEW IMPORT
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;

/**
 * Handles endpoints related to the product catalog, individual product pages, and product reviews.
 */
@Controller
public class ProductController {

    private final ProductService productService;
    private final ReviewService reviewService; // NEW INJECTION

    public ProductController(ProductService productService, ReviewService reviewService) {
        this.productService = productService;
        this.reviewService = reviewService;
    }


    // --- Helper method to define all available categories ---
    private String[] getAllCategories() {
        // Must match the list used in AdminController and navigation dropdowns
        return new String[]{
                "Sarees", "Lehengas", "Kurtis", "Long Frocks", "Mom & Me", "Crop Top – Skirts",
                "Handlooms", "Casual Frocks", "Ready To Wear", "Dupattas", "Kids wear",
                "Dress Material", "Blouses", "Fabrics"
        };
    }

    // --- Helper method to define available filter colors ---
    private String[] getFilterColors() {
        return new String[]{"Black", "Blue", "Green", "Red", "Pink", "Yellow", "Maroon", "Purple", "White", "Gray", "Brown", "Orange"};
    }


    /**
     * Displays the product listing page with filtering and sorting options.
     * **MODIFIED: Added keyword parameter.**
     */
    @GetMapping("/products")
    public String viewProductCatalog(
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "sortBy", required = false, defaultValue = "latest") String sortBy,
            @RequestParam(value = "minPrice", required = false) Double minPrice,
            @RequestParam(value = "maxPrice", required = false) Double maxPrice,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "color", required = false) String color,
            @RequestParam(value = "keyword", required = false) String keyword, // NEW PARAMETER
            Model model) {

        // Use the comprehensive filtering service method
        List<Product> products = productService.getFilteredProducts(
                category, sortBy, minPrice, maxPrice, status, color, keyword); // PASS KEYWORD

        model.addAttribute("products", products);

        String currentCategoryDisplay = category != null && !category.isEmpty() ? category : "All Products";
        if (keyword != null && !keyword.isEmpty()) {
            currentCategoryDisplay = "Search results for: '" + keyword + "'";
        }
        model.addAttribute("currentCategory", currentCategoryDisplay);

        // Pass filter states back to the view for form persistence
        model.addAttribute("selectedSortBy", sortBy);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("selectedColor", color);
        model.addAttribute("minPriceValue", minPrice);
        model.addAttribute("maxPriceValue", maxPrice);
        model.addAttribute("currentKeyword", keyword);

        // Pass filter lists to the view
        model.addAttribute("allCategories", getAllCategories());
        model.addAttribute("filterColors", getFilterColors());

        return "products";
    }

    /**
     * Displays the individual product detail page.
     */
    @GetMapping("/products/{id}")
    public String viewProductDetail(@PathVariable Long id, Model model) {
        Optional<Product> productOptional = productService.getProductById(id);

        if (productOptional.isEmpty() || !productOptional.get().getIsAvailable()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found.");
        }

        Product product = productOptional.get();
        model.addAttribute("product", product);

        // Fetch Review Data for the product
        model.addAttribute("averageRating", reviewService.getAverageRating(id));
        model.addAttribute("reviewCount", reviewService.getReviewCount(id));
        model.addAttribute("reviews", reviewService.getApprovedReviewsForProduct(id));


        // Suggest related products based on category (using the first 4 from the same category)
        List<Product> relatedProducts = productService.getProductsByCategoryOrAll(product.getCategory());
        // Filter out the current product and take up to 4 related items
        relatedProducts.removeIf(p -> p.getId().equals(id));
        model.addAttribute("relatedProducts", relatedProducts.subList(0, Math.min(relatedProducts.size(), 4)));

        return "product_detail";
    }

    /**
     * NEW ENDPOINT: Handles submission of a new review for a product.
     */
    @PostMapping("/products/{id}/review")
    public String submitProductReview(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable("id") Long productId,
            @RequestParam("rating") int rating,
            @RequestParam("comment") String comment,
            RedirectAttributes redirectAttributes) {

        if (userDetails == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "You must be logged in to submit a review.");
            return "redirect:/login";
        }

        try {
            reviewService.submitReview(userDetails.getUsername(), productId, rating, comment);
            redirectAttributes.addFlashAttribute("successMessage", "Review submitted! It will appear once approved by our team.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Failed to submit review. An unexpected error occurred.");
            System.err.println("Review submission error: " + e.getMessage());
        }

        // Redirect back to the product detail page
        return "redirect:/products/" + productId + "#reviews";
    }
}