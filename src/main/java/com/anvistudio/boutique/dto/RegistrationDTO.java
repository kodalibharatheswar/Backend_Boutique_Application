package com.anvistudio.boutique.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.io.Serializable;

/**
 * Data Transfer Object (DTO) for customer registration,
 * used for capturing form data and applying validation rules.
 */
@Data
public class RegistrationDTO implements Serializable {

    // --- REQUIRED FIELDS (Minimum for registration) ---

    @NotBlank(message = "First Name is required.")
    @Size(min = 2, max = 50, message = "First Name must be between 2 and 50 characters.")
    private String firstName;

    @NotBlank(message = "Last Name is required.")
    @Size(min = 2, max = 50, message = "Last Name must be between 2 and 50 characters.")
    private String lastName;

    @Email(message = "Enter a valid email address.")
    @NotBlank(message = "Email (Username) is required.")
    private String username; // Mapped to User.username

    // MODIFIED: Password Policy: min 8 chars, 1 uppercase, 1 lowercase, 1 digit, 1 special character
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$",
            message = "Password must be at least 8 characters long and contain one uppercase letter, one lowercase letter, one number, and one special character (@$!%*?&).")
    @NotBlank(message = "Password is required.")
    private String password;

    @NotBlank(message = "Password confirmation is required.")
    private String confirmPassword;

    // --- NEW REQUIRED FIELD (for Customer profile) ---
    // Assuming simple validation for now, more complex E.164 validation would be client/service-side
    @Pattern(regexp = "^[+]?[0-9]{10,15}$", message = "Enter a valid phone number (10-15 digits).")
    @NotBlank(message = "Phone Number is required.")
    private String phoneNumber;

    // --- MANDATORY CONSENT ---
    @AssertTrue(message = "You must accept the Terms and Privacy Policy to register.")
    private Boolean termsAccepted = false;


    // --- OPTIONAL FIELDS (Progressive Profiling) ---
    // These will be optional in the registration form but available in the DTO

    // Preferred Size is not mandatory at registration
    private String preferredSize;

    // Gender is not mandatory at registration
    private String gender;

    // Date of Birth is not mandatory at registration
    private String dateOfBirth; // Use String for form input, convert later

    // Newsletter Opt-in (Optional by default)
    private Boolean newsletterOptIn = false;
}