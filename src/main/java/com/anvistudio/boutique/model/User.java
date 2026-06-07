package com.anvistudio.boutique.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(name = "users") // Use 'users' to avoid potential conflicts with 'user' reserved word
@Data // Lombok for getters, setters, toString, equals, hashCode
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The username is the unique email address, set to nullable = false and unique
    // = true
    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String password; // Will store BCrypt encoded password

    // Role can be "CUSTOMER" or "ADMIN"
    @Column(nullable = false)
    private String role;

    // --- NEW FIELDS FOR OAUTH2 ---
    @Column(nullable = true)
    private String authProvider; // e.g., "LOCAL", "GOOGLE", "GITHUB"

    @Column(nullable = true)
    private String providerId; // The unique ID from the OAuth2 provider

    // --- NEW FIELDS FOR MFA ---
    @Column(nullable = true)
    private String mfaSecret;

    @Column(nullable = false)
    private Boolean mfaEnabled = false;

    // --- FIELD FOR EMAIL VERIFICATION ---
    @Column(nullable = false)
    private Boolean emailVerified = false;

    // --- NEW FIELD: Admin Initial Setup Flag ---
    /**
     * For ADMIN role only: true if the admin has changed the default hardcoded
     * password.
     */
    @Column(nullable = false)
    private Boolean credentialsUpdated = false;

    // --- NEW FIELD: Recovery Phone Number (Used primarily by Admin/Fallbacks) ---
    /**
     * Recovery phone number (optional, used for Admin recovery or customer
     * fallback).
     */
    @Column(nullable = true)
    private String recoveryPhoneNumber;
}
