package com.anvistudio.boutique.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.util.Date;

/**
 * Entity representing an available promotional coupon.
 */
@Entity
@Table(name = "coupons")
@Data
@NoArgsConstructor
public class Coupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code; // e.g., FESTIVE20

    @Column(nullable = false)
    private String description; // e.g., 20% off all sarees

    @Column(nullable = false)
    private BigDecimal discountValue; // Value of discount (e.g., 20 or 500)

    @Column(nullable = false)
    private String discountType; // e.g., PERCENT, FLAT_AMOUNT

    private BigDecimal minPurchaseAmount;

    @Temporal(TemporalType.DATE)
    private Date expirationDate;

    @Column(nullable = false)
    private Boolean isActive = true;
}