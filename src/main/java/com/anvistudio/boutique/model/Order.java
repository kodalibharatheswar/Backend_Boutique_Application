package com.anvistudio.boutique.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * Entity representing a customer's confirmed order.
 */
@Entity
@Table(name = "orders")
@Data
@NoArgsConstructor
public class Order {

    public enum OrderStatus {
        CREATED, PENDING, CONFIRMED, PROCESSING, PACKED, SHIPPED, OUT_FOR_DELIVERY, DELIVERED, CANCELLED, RETURN_REQUESTED, RETURN_PICKED, RETURNED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Link to the user who placed the order
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private Date orderDate = new Date();

    @Column(nullable = false)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status = OrderStatus.PENDING;

    // Snapshot of the shipping address (or link to the address if needed)
    @Column(columnDefinition = "TEXT")
    private String shippingAddressSnapshot;

    // Details of items included (simplified: would typically be a separate OrderItem entity)
    @Column(columnDefinition = "TEXT")
    private String orderItemsSnapshot;
}