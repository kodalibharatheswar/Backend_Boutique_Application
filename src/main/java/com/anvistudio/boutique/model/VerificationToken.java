package com.anvistudio.boutique.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Date;
import java.util.Calendar;
import java.util.Random;

/**
 * Entity to store OTP (One-Time Password) for email verification OR password reset.
 */
@Entity
@Table(name = "verification_tokens")
@Data
@NoArgsConstructor
public class VerificationToken {

    public enum TokenType {
        REGISTRATION,
        PASSWORD_RESET,
        NEW_EMAIL_VERIFICATION // NEW: For verifying a change of the primary email address
    }

    // OTPs expire quickly, setting a short window (e.g., 5 or 10 minutes)
    private static final int EXPIRATION_TIME_MINUTES = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The 6-digit OTP string
    @Column(nullable = false, unique = true, length = 6) // Max length 6 for OTP
    private String token; // OTP is stored here

    // Links to the User requiring verification
    @OneToOne(targetEntity = User.class, fetch = FetchType.EAGER)
    @JoinColumn(nullable = false, name = "user_id", unique = true)
    private User user;

    // NEW FIELD: Type of token (Registration or Reset)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 65) // FIX: Increased length to accommodate 'NEW_EMAIL_VERIFICATION' (24 chars)
    private TokenType tokenType;

    // Date/Time when the token expires
    @Column(nullable = false)
    private Date expiryDate;

    public VerificationToken(User user, TokenType tokenType) {
        this.user = user;
        this.tokenType = tokenType; // Initialize type
        this.token = generateOtp(); // Generate 6-digit OTP
        this.expiryDate = calculateExpiryDate(EXPIRATION_TIME_MINUTES);
    }

    private Date calculateExpiryDate(int expiryTimeInMinutes) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(new Date());
        cal.add(Calendar.MINUTE, expiryTimeInMinutes);
        return cal.getTime();
    }

    /**
     * Helper to generate a random 6-digit number string.
     */
    private String generateOtp() {
        Random random = new Random();
        // Generates a number between 100000 and 999999
        int otp = 100000 + random.nextInt(900000);
        return String.valueOf(otp);
    }

    /**
     * Checks if the token is still valid.
     */
    public boolean isExpired() {
        return new Date().after(this.expiryDate);
    }
}