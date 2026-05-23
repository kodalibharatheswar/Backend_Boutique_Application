package com.anvistudio.boutique.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.Date;

/**
 * Entity representing one item added to a user's shopping cart.
 */
@Entity
@Table(name = "cart_items")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Links to the User (customer) who owns this cart item
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Links to the Product being purchased
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false, updatable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date dateAdded = new Date();

    /**
     * Helper method to calculate the total price for this item.
     * CRITICAL FIX: Uses the discounted price from the Product entity.
     */
    public double getTotalPrice() {
        // Use doubleValue() for arithmetic, relying on Product to handle BigDecimal precision.
        return this.product.getDiscountedPrice().doubleValue() * this.quantity;
    }
}