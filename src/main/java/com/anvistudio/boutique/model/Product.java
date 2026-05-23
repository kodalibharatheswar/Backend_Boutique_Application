package com.anvistudio.boutique.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.ArrayList;

@Entity
@Table(name = "products")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    // Existing: Description (Set to TEXT)
    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private BigDecimal price;

    // ****************************************
    // *** NEW FIELD: Discount Percentage ***
    // ****************************************
    @Column(nullable = false)
    private Integer discountPercent = 0; // Default to 0% discount

    /**
     * Helper method to calculate the final price after discount.
     * @return The discounted price, or the original price if no discount is applied.
     */
    public BigDecimal getDiscountedPrice() {
        if (discountPercent == null || discountPercent <= 0 || price == null) {
            return price;
        }

        // Calculate discounted price: price * (1 - discountPercent / 100)
        BigDecimal discountFactor = BigDecimal.ONE.subtract(
                BigDecimal.valueOf(discountPercent)
                        .divide(BigDecimal.valueOf(100), 2, BigDecimal.ROUND_HALF_UP)
        );

        // Use setScale to ensure consistent rounding for currency
        return price.multiply(discountFactor).setScale(2, BigDecimal.ROUND_HALF_UP);
    }
    // ****************************************

    /**
     * NEW: Determines if the product is a Clearance Sale item (50% discount or more).
     */
    public boolean isClearance() {
        return discountPercent != null && discountPercent >= 50;
    }

    @Column(nullable = false)
    private String category;

    private String imageUrl;

    // *** NEW MEDIA FIELDS ***
    @ElementCollection
    @CollectionTable(name = "product_images", joinColumns = @JoinColumn(name = "product_id"))
    @Column(name = "image_url")
    private List<String> additionalImageUrls;

    private String videoUrl;
    private String audioUrl;
    // ************************

    // *** NEW FIELD ADDED ***
    // 9. Product Color (Used for filtering)
    private String productColor;
    // ***********************

    @Column(nullable = false)
    private Integer stockQuantity;

    // Existing: Date Created
    @Column(nullable = false, updatable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date dateCreated;

    @PrePersist
    protected void onCreate() {
        if (this.dateCreated == null) {
            this.dateCreated = new Date();
        }
    }

    // ===================================
    // === NEW FIELDS FOR RICH PRODUCT INFO ===
    // ===================================

    // 1. SKU (Unique Identifier)
    @Column(unique = true, nullable = true)
    private String sku;

    // 2. Size Options (e.g., S, M, L or 38, 40)
    @Column(columnDefinition = "VARCHAR(255)") // Can be short, comma-separated list
    private String sizeOptions;

    // 3. Size Guide Link
    private String sizeGuideUrl;

    // 4. Delivery Information
    private String estimatedDelivery; // e.g., "4-6 business days"

    // 5. Delivery and Return Policy Details
    @Column(columnDefinition = "TEXT")
    private String deliveryAndReturnPolicy;

    // 6. Detailed Fabric/Wash Care Information
    @Column(columnDefinition = "TEXT")
    private String additionalInformation;

    // 7. SEO Tags
    @Column(columnDefinition = "VARCHAR(512)")
    private String productTags; // Comma-separated SEO tags

    // 8. Availability/Visibility toggle
    @Column(nullable = false)
    private Boolean isAvailable = true; // Default: visible

}