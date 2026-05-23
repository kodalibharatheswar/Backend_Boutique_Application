package com.anvistudio.boutique.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.util.Date;

/**
 * Entity representing a customer's gift card balance or history.
 */
@Entity
@Table(name = "gift_cards")
@Data
@NoArgsConstructor
public class GiftCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String cardNumber; // Unique card number or ID

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user; // The user who currently owns/redeemed it

    @Column(nullable = false)
    private BigDecimal currentBalance;

    @Column(nullable = false)
    private BigDecimal initialValue;

    @Temporal(TemporalType.DATE)
    private Date expirationDate;
}