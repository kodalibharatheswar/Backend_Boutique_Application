package com.anvistudio.boutique.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.Date;

@Entity
@Table(name = "customer_details")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Retaining first/last name for compatibility, but focusing on the DTO for input
    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    // --- NEW REQUIRED FIELD ---
    @Column(nullable = false)
    private String phoneNumber;

    // --- NEW OPTIONAL/PROGRESSIVE PROFILING FIELDS ---
    // Preferred Size (S, M, L, etc.)
    private String preferredSize;

    // Gender/Style Preference
    private String gender; // e.g., "FEMALE", "MALE", "UNISEX"

    // Date of Birth (for birthday offers)
    @Temporal(TemporalType.DATE)
    private Date dateOfBirth;

    // --- NEW CONSENT FLAGS (MANDATORY at sign-up) ---
    @Column(nullable = false)
    private Boolean termsAccepted = false; // Must be accepted at registration

    // Newsletter Opt-in
    @Column(nullable = false)
    private Boolean newsletterOptIn = false;

    // One-to-one relationship with the User authentication entity
    @OneToOne
    @JoinColumn(name = "user_id", referencedColumnName = "id", nullable = false, unique = true)
    private User user;
}