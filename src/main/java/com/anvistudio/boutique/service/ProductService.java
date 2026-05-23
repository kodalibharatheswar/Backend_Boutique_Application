package com.anvistudio.boutique.service;

import com.anvistudio.boutique.model.Product;
import com.anvistudio.boutique.repository.ProductRepository;
import com.anvistudio.boutique.repository.CartItemRepository;
import com.anvistudio.boutique.repository.WishlistRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;
import java.util.Comparator;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final CartItemRepository cartItemRepository;
    private final WishlistRepository wishlistRepository;
    private final NotificationService notificationService; // NEW INJECTION

    public ProductService(ProductRepository productRepository, CartItemRepository cartItemRepository,
                          WishlistRepository wishlistRepository, NotificationService notificationService) { // NEW CONSTRUCTOR PARAMETER
        this.productRepository = productRepository;
        this.cartItemRepository = cartItemRepository;
        this.wishlistRepository = wishlistRepository;
        this.notificationService = notificationService; // <--- CRITICAL: Initialization was missing or incorrect previously
    }

    /**
     * Retrieves a single product by its ID.
     */
    public Optional<Product> getProductById(Long id) {
        return productRepository.findById(id);
    }


    /**
     * NEW: Retrieves only the image URL for a given product ID.
     */
    public String getProductImageUrl(Long id) {
        return productRepository.findById(id)
                .map(Product::getImageUrl)
                .orElse("https://placehold.co/80x80/f0f0f0/333?text=N%2FA");
    }


    /**
     * Retrieves all products (used for admin view).
     */
    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }


    // The getFilteredProducts method remains unchanged and is omitted for brevity.


    /**
     * Admin function: Saves a new product or updates an existing one.
     * MODIFIED: Added logic to check for sale/clearance status and trigger notification.
     */
    @Transactional
    public Product saveProduct(Product product) {
        // 1. Check the previous state of the discount if updating an existing product
        boolean wasPreviouslyDiscounted = false;
        if (product.getId() != null) {
            Optional<Product> oldProductOptional = productRepository.findById(product.getId());
            if (oldProductOptional.isPresent()) {
                // If old discount was > 0, set flag to true
                wasPreviouslyDiscounted = oldProductOptional.get().getDiscountPercent() > 0;
            }
        }

        // 2. Save the product first to commit the new discount/clearance status
        Product savedProduct = productRepository.save(product);

        // 3. Notification Logic:
        // Trigger notification ONLY if the product is currently discounted (> 0%)
        // AND it was *NOT* previously discounted. This prevents spamming on every edit.
        boolean isCurrentlyDiscounted = savedProduct.getDiscountPercent() > 0;

        if (isCurrentlyDiscounted && !wasPreviouslyDiscounted) {
            System.out.println("Product is NEWLY discounted. Triggering sale notification...");
            notificationService.sendSaleNotification(savedProduct);
        }

        return savedProduct;
    }


    /**
     * Retrieves products based on multiple filter and sort criteria.
     */
    public List<Product> getFilteredProducts(String category, String sortBy, Double minPrice, Double maxPrice, String status, String color, String keyword) {
        List<Product> products;

        // 1. Base Retrieval (Filter by Keyword first for broad search, or Category)
        if (keyword != null && !keyword.trim().isEmpty()) {
            products = productRepository.searchByKeyword(keyword.trim());
            category = null;
        } else if (category != null && !category.trim().isEmpty()) {
            products = productRepository.findByCategory(category.trim());
        } else {
            products = productRepository.findAll();
        }

        // 2. Filter by Price (In-memory filtering - FIX APPLIED HERE)
        if (minPrice != null || maxPrice != null) {
            products.removeIf(p -> {
                // *** FIX: Use discounted price for comparison in price range filtering ***
                double price = p.getDiscountedPrice().doubleValue();
                if (minPrice != null && price < minPrice) return true;
                if (maxPrice != null && price > maxPrice) return true;
                return false;
            });
        }

        // 3. Filter by Color (In-memory filtering)
        if (color != null && !color.trim().isEmpty()) {
            final String normalizedColor = color.trim().toLowerCase();
            products.removeIf(p -> p.getProductColor() == null || !p.getProductColor().toLowerCase().contains(normalizedColor));
        }


        // 4. Filter by Status (In-memory filtering)
        if (status != null && !status.isEmpty()) {
            switch (status) {
                case "inStock":
                    products.removeIf(p -> p.getStockQuantity() <= 0 || !p.getIsAvailable());
                    break;
                case "lowStock":
                    products.removeIf(p -> p.getStockQuantity() <= 0 || p.getStockQuantity() > 5 || !p.getIsAvailable());
                    break;
                case "onSale":
                    products.removeIf(p -> p.getDiscountPercent() <= 0);
                    break;
                case "clearance":
                    products.removeIf(p -> !p.isClearance());
                    break;
            }
        }

        // 5. Sort (In-memory sorting already uses discounted price correctly)
        if (sortBy != null && !sortBy.isEmpty()) {
            Comparator<Product> comparator;
            switch (sortBy) {
                case "priceAsc":
                    comparator = Comparator.comparing(Product::getDiscountedPrice);
                    break;
                case "priceDesc":
                    comparator = Comparator.comparing(Product::getDiscountedPrice).reversed();
                    break;
                case "oldest":
                    comparator = Comparator.comparing(Product::getDateCreated);
                    break;
                case "latest":
                default:
                    comparator = Comparator.comparing(Product::getDateCreated).reversed();
                    break;
            }
            products.sort(comparator);
        }

        // Final filter: Only show products marked as 'isAvailable' to customers
        products.removeIf(p -> !p.getIsAvailable());

        return products;
    }


    /**
     * Retrieves the top 8 latest products for display (uses the sorting query).
     */
    public List<Product> getDisplayableProducts() {
        // Ensure the home page only shows available products (good practice)
        List<Product> availableProducts = productRepository.findTop8ByOrderByDateCreatedDesc();
        availableProducts.removeIf(p -> !p.getIsAvailable());
        return availableProducts;
    }

    /**
     * Retrieves products based on category, or all products if category is null/empty.
     */
    public List<Product> getProductsByCategoryOrAll(String category) {
        if (category != null && !category.trim().isEmpty()) {
            return productRepository.findByCategory(category.trim());
        }
        return productRepository.findAll();
    }

    /**
     * NEW: Admin function to delete a product, performing necessary cleanup first.
     */
    @Transactional // Ensure all steps (cleanup and delete) succeed or fail together
    public void deleteProduct(Long id) {

        // 1. Cleanup: Remove product from all customer carts
        cartItemRepository.deleteByProductId(id);

        // 2. Cleanup: Remove product from all customer wishlists
        wishlistRepository.deleteByProductId(id);

        // 3. Delete the product itself
        productRepository.deleteById(id);
    }
}