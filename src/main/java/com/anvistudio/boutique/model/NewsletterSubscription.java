package com.anvistudio.boutique.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Date;

/**
 * Entity to store non-registered user emails for newsletter subscription.
 */
@Entity
@Table(name = "newsletter_subscriptions")
@Data
@NoArgsConstructor
public class NewsletterSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date dateSubscribed = new Date();

    @Column(nullable = false)
    private Boolean isActive = true; // For opt-out management

    public NewsletterSubscription(String email) {
        this.email = email;
    }
}