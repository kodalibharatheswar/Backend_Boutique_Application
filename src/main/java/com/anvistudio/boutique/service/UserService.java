package com.anvistudio.boutique.service;

import com.anvistudio.boutique.model.Customer;
import com.anvistudio.boutique.model.User;
import com.anvistudio.boutique.model.VerificationToken;
import com.anvistudio.boutique.model.VerificationToken.TokenType;
import com.anvistudio.boutique.repository.CustomerRepository;
import com.anvistudio.boutique.repository.UserRepository;
import com.anvistudio.boutique.repository.VerificationTokenRepository;
import com.anvistudio.boutique.dto.RegistrationDTO;
import jakarta.annotation.PostConstruct;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Optional;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.regex.Pattern;

@Service
public class UserService implements UserDetailsService {

    private static final Pattern PHONE_PATTERN = Pattern.compile("^[+]?[0-9]{10,15}$");

//    private static final Pattern PASSWORD_POLICY_PATTERN = Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)[a-zA-Z\\d]{8,}$");

    // MODIFIED: Added special character validation: (?=.*[@$!%*?&])
    private static final Pattern PASSWORD_POLICY_PATTERN = Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$");

    // Hardcoded Default Admin Credentials
    private static final String DEFAULT_ADMIN_USERNAME = "admin";
    private static final String DEFAULT_ADMIN_PASSWORD = "password123";


    public final UserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final VerificationTokenRepository tokenRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, CustomerRepository customerRepository,
                       VerificationTokenRepository tokenRepository, EmailService emailService,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.customerRepository = customerRepository;
        this.tokenRepository = tokenRepository;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * NEW: Ensures the default admin account exists in the database upon application startup.
     */
    @PostConstruct
    @Transactional
    public void createDefaultAdminIfNotFound() {
        // Only proceed if the default admin user does NOT exist in the DB
        if (userRepository.findByUsername(DEFAULT_ADMIN_USERNAME).isEmpty()) {
            User defaultAdmin = new User();
            defaultAdmin.setUsername(DEFAULT_ADMIN_USERNAME);
            defaultAdmin.setPassword(passwordEncoder.encode(DEFAULT_ADMIN_PASSWORD));
            defaultAdmin.setRole("ADMIN");
            defaultAdmin.setEmailVerified(true);
            defaultAdmin.setCredentialsUpdated(false); // MUST be false for initial login check
            // Set a placeholder phone number to enable phone recovery on first login if needed
            defaultAdmin.setRecoveryPhoneNumber("9999999999");
            userRepository.save(defaultAdmin);
            System.out.println("SECURITY INFO: Default admin account created in DB.");
            System.out.println("DEBUG: Username: " + DEFAULT_ADMIN_USERNAME);
            System.out.println("DEBUG: Encoded Password: " + defaultAdmin.getPassword());
        } else {
            System.out.println("SECURITY INFO: Admin account already exists in DB.");
        }
    }


    /**
     * CRITICAL REFACTOR: Now relies ONLY on the database for user details.
     */
    @Override
    public UserDetails loadUserByUsername(String identifier) throws UsernameNotFoundException {

        Optional<User> userOptional = findUserByIdentifier(identifier);

        // --- 1. Handle Admin or Customer Database User ---
        User user = userOptional
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + identifier));

        // CRITICAL: User must be verified to log in (except for Admin)
        boolean isEnabled = user.getEmailVerified() || "ADMIN".equals(user.getRole());

        // Throw DisabledException if not enabled (will be caught by SecurityConfig failure handler)
        if (!isEnabled) {
            throw new DisabledException("Account is not yet verified. Please confirm your email address.");
        }

        GrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + user.getRole());

        // For social (Google/OAuth2) users, password is stored as empty string ""
        // Spring Security's User constructor throws IllegalArgumentException for empty/null passwords.
        // Use a sentinel placeholder that can NEVER match any real BCrypt hash.
        String password = user.getPassword();
        if (password == null || password.isEmpty()) {
            password = "{noop}OAUTH2_SOCIAL_ACCOUNT_NO_LOCAL_PASSWORD";
        }

        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                password,
                isEnabled,
                true, true, true,
                Collections.singleton(authority)
        );
    }

    /**
     * Finds the User entity (not UserDetails) by username (email).
     */
    public Optional<User> findUserByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    /**
     * NEW: Finds a User by either username (email) or phone number.
     * Used by loadUserByUsername and Forgot Password feature.
     */
    public Optional<User> findUserByIdentifier(String identifier) {
        // 1. Try to treat identifier as email (User.username)
        Optional<User> userByEmail = userRepository.findByUsername(identifier);
        if (userByEmail.isPresent()) {
            return userByEmail;
        }

        // 2. If not found by email, check if it matches phone pattern
        if (PHONE_PATTERN.matcher(identifier).matches()) {

            // 2a. Try to find CUSTOMER by phone number (via Customer profile table)
            Optional<User> customerUser = customerRepository.findByPhoneNumber(identifier)
                    .map(Customer::getUser);
            if (customerUser.isPresent()) {
                return customerUser;
            }

            // 2b. Try to find ADMIN by recovery phone number (via User table)
            // Use stream to handle potential multiple matches, though recovery phone should ideally be unique.
            Optional<User> adminUser = userRepository.findAll().stream()
                    .filter(u -> "ADMIN".equals(u.getRole()))
                    .filter(u -> identifier.equals(u.getRecoveryPhoneNumber()))
                    .findFirst();

            if (adminUser.isPresent()) {
                return adminUser;
            }
        }

        // 3. Not found by email or valid phone pattern
        return Optional.empty();
    }



    /**
     * MODIFIED: Updates admin credentials and sets the flag.
     */
    @Transactional
    public User updateAdminCredentials(String currentUsername, String newUsername, String newPassword, String recoveryPhoneNumber) {

        // 1. Fetch the existing admin user from the DB
        User adminUser = userRepository.findByUsername(currentUsername)
                .orElseThrow(() -> new IllegalStateException("Admin user not found."));

        // 2. Prevent overwriting another user if changing username
        if (!currentUsername.equals(newUsername) && userRepository.findByUsername(newUsername).isPresent()) {
            throw new IllegalStateException("The new username is already taken.");
        }

        // 3. Validation: Ensure phone number is valid
        if (!PHONE_PATTERN.matcher(recoveryPhoneNumber).matches()) {
            throw new IllegalStateException("Invalid phone number format for recovery.");
        }

        // Validation: Enforce new password policy
        if (!PASSWORD_POLICY_PATTERN.matcher(newPassword).matches()) {
            throw new IllegalStateException("New password does not meet complexity requirements (Min 8 chars, 1 uppercase, 1 lowercase, 1 number, 1 special character).");
        }


        // 4. Apply changes and set the critical flags
        adminUser.setUsername(newUsername);
        adminUser.setPassword(passwordEncoder.encode(newPassword));
        adminUser.setRecoveryPhoneNumber(recoveryPhoneNumber); // NEW: Save the recovery phone
        adminUser.setEmailVerified(true);
        adminUser.setCredentialsUpdated(true); // This invalidates the default login.

        return userRepository.save(adminUser);
    }

    // --- NEW: Check Admin Update Status ---
    public boolean isAdminCredentialsUpdated(String username) {
        // This method is called by AdminController to check the flag
        return userRepository.findByUsername(username)
                .filter(u -> "ADMIN".equals(u.getRole()))
                .map(User::getCredentialsUpdated)
                .orElse(false);
    }


    // --- Customer Registration Logic ---
    @Transactional
    public User registerCustomer(RegistrationDTO registrationDTO) {
        // ... (standard validation unchanged)
        if (userRepository.findByUsername(registrationDTO.getUsername()).isPresent()) {
            throw new IllegalStateException("Username is already taken.");
        }

        // Ensure phone number isn't already used
        if (customerRepository.findByPhoneNumber(registrationDTO.getPhoneNumber()).isPresent()) {
            throw new IllegalStateException("Phone number is already registered.");
        }

        if (!registrationDTO.getPassword().equals(registrationDTO.getConfirmPassword())) {
            throw new IllegalStateException("Passwords do not match.");
        }

        User newUser = new User();
        newUser.setUsername(registrationDTO.getUsername());
        newUser.setPassword(passwordEncoder.encode(registrationDTO.getPassword()));
        newUser.setRole("CUSTOMER");
        newUser.setEmailVerified(false);
        User savedUser = userRepository.save(newUser);

        Customer newCustomer = new Customer();
        newCustomer.setFirstName(registrationDTO.getFirstName());
        newCustomer.setLastName(registrationDTO.getLastName());
        newCustomer.setUser(savedUser);
        newCustomer.setPhoneNumber(registrationDTO.getPhoneNumber());
        newCustomer.setPreferredSize(registrationDTO.getPreferredSize());
        newCustomer.setGender(registrationDTO.getGender());
        newCustomer.setTermsAccepted(registrationDTO.getTermsAccepted());
        // FIX: Ensure newsletterOptIn is directly taken from the DTO, it can be null if not checked
        newCustomer.setNewsletterOptIn(registrationDTO.getNewsletterOptIn() != null && registrationDTO.getNewsletterOptIn());

        if (registrationDTO.getDateOfBirth() != null && !registrationDTO.getDateOfBirth().isEmpty()) {
            try {
                Date dob = new SimpleDateFormat("yyyy-MM-dd").parse(registrationDTO.getDateOfBirth());
                newCustomer.setDateOfBirth(dob);
            } catch (Exception e) {
                System.err.println("Failed to parse DOB: " + e.getMessage());
            }
        }

        customerRepository.save(newCustomer);

        // Create OTP and send email for REGISTRATION
        createOtpAndSendEmail(savedUser, TokenType.REGISTRATION);

        return savedUser;
    }

    /**
     * Creates a new OTP for the user and triggers the email sending.
     * The caller is responsible for cleaning up old tokens *before* calling this method.
     */
    @Transactional
    public void createOtpAndSendEmail(User user, TokenType tokenType) {
        // We create the token here and rely on the calling method to have cleared the old token.

        VerificationToken otpToken = new VerificationToken(user, tokenType);
        tokenRepository.save(otpToken);

        // NOTE: The recipient email is derived from the 'user' object's username field.
        emailService.sendOtpEmail(user, otpToken);
    }

    /**
     * NEW: Creates a new OTP for the user and triggers the email sending (For REGISTRATION).
     * This method is called from registration flow.
     */
    @Transactional
    public void createOtpAndSendEmail(User user) {
        // Ensure old token is deleted and flushed before creating a new one for registration
        tokenRepository.deleteByUserId(user.getId());
        tokenRepository.flush(); // <--- CRITICAL FIX: Ensure DELETE is executed now

        VerificationToken otpToken = new VerificationToken(user, TokenType.REGISTRATION); // Use REGISTRATION type
        tokenRepository.save(otpToken);

        emailService.sendOtpEmail(user, otpToken);
    }

    // --- Password Reset Logic (Modified for Transaction Safety) ---
    @Transactional
    public User findAndCreateResetOtp(String identifier) throws UsernameNotFoundException {
        User user = findUserByIdentifier(identifier)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + identifier));

        // FIX: Ensure old token is deleted and flushed before creating a new one for password reset
        tokenRepository.deleteByUserId(user.getId());
        tokenRepository.flush(); // <--- CRITICAL FIX: Ensure DELETE is executed now

        // Create OTP and send email for PASSWORD_RESET
        createOtpAndSendEmail(user, TokenType.PASSWORD_RESET); // Use generic method

        return user;
    }


    // --- NEW: Email Change Logic (Critical Constructor Fix Applied) ---

    /**
     * Step 1: Validates the new email, checks if it's available, and sends an OTP to it.
     */
    @Transactional
    public void initiateEmailChange(String currentUsername, String newEmail) {
        // 1. Find the current user
        User user = userRepository.findByUsername(currentUsername)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found."));

        // 2. Validation
        if (!newEmail.contains("@") || !newEmail.contains(".")) {
            throw new IllegalStateException("Invalid email address format.");
        }
        if (currentUsername.equalsIgnoreCase(newEmail)) {
            throw new IllegalStateException("The new email cannot be the same as your current email.");
        }

        // 3. Check if the new email is already taken
        if (userRepository.findByUsername(newEmail).isPresent()) {
            throw new IllegalStateException("The email address '" + newEmail + "' is already registered to another account.");
        }

        // 4. CRITICAL FIX: Delete the existing token *before* saving the new one and flush the changes.
        tokenRepository.deleteByUserId(user.getId());
        tokenRepository.flush();

        // 5. Create OTP: The token is linked to the existing user ID.
        // We create a temp User object *in memory* with the new email for the EmailService to target.
        User tempUserForEmail = new User();
        tempUserForEmail.setId(user.getId());
        tempUserForEmail.setUsername(newEmail);
        tempUserForEmail.setPassword(user.getPassword());
        tempUserForEmail.setRole(user.getRole());
        tempUserForEmail.setEmailVerified(user.getEmailVerified());
        tempUserForEmail.setCredentialsUpdated(user.getCredentialsUpdated());
        tempUserForEmail.setRecoveryPhoneNumber(user.getRecoveryPhoneNumber());


        VerificationToken otpToken = new VerificationToken(user, TokenType.NEW_EMAIL_VERIFICATION);
        tokenRepository.save(otpToken); // Saves token linked to old User ID

        // 6. Send email to the NEW address
        emailService.sendOtpEmail(tempUserForEmail, otpToken);
    }

    /**
     * Step 2: Finalizes the email change by verifying the OTP and updating the username.
     */
    @Transactional
    public void finalizeEmailChange(String currentUsername, String newEmail, String otp) {
        // 1. Find the current user
        User user = userRepository.findByUsername(currentUsername)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found."));

        // 2. Verify the OTP against the user ID and expected token type
        Optional<VerificationToken> tokenOptional = tokenRepository.findByUserId(user.getId())
                .filter(token -> token.getTokenType() == TokenType.NEW_EMAIL_VERIFICATION);

        if (tokenOptional.isEmpty()) {
            throw new IllegalStateException("No active email change request found or token expired. Please try initiating the change again.");
        }

        VerificationToken token = tokenOptional.get();

        if (token.isExpired()) {
            tokenRepository.delete(token);
            throw new IllegalStateException("Verification code expired.");
        }

        if (!token.getToken().equals(otp)) {
            throw new IllegalStateException("Invalid verification code.");
        }

        // 3. OTP is valid: Update the user's username (email)
        user.setUsername(newEmail);
        userRepository.save(user);

        // 4. Delete the token
        tokenRepository.delete(token);
    }

    // ... (rest of the service methods remain the same)

    // --- Core Verification Methods (OTP check remains the same) ---
    public Optional<VerificationToken> findActiveToken(String email, TokenType tokenType) {
        // This method is primarily used for frontend validation/redirection logic
        return userRepository.findByUsername(email)
                .flatMap(user -> tokenRepository.findByUserId(user.getId()))
                .filter(token -> token.getTokenType() == tokenType && !token.isExpired());
    }

    @Transactional
    public Optional<User> verifyOtp(String otp, String username, TokenType tokenType) {
        // ... (OTP verification logic remains the same)
        Optional<User> userOptional = userRepository.findByUsername(username);

        if (userOptional.isEmpty()) {
            return Optional.empty();
        }

        User user = userOptional.get();

        // Find the specific token for this user
        Optional<VerificationToken> tokenOptional = tokenRepository.findByUserId(user.getId())
                .filter(token -> token.getTokenType() == tokenType);

        if (tokenOptional.isEmpty()) {
            return Optional.empty();
        }

        VerificationToken otpToken = tokenOptional.get();

        // Check 1: Expiry
        if (otpToken.isExpired()) {
            tokenRepository.delete(otpToken);
            return Optional.empty();
        }

        // Check 2: OTP match
        if (!otpToken.getToken().equals(otp)) {
            return Optional.empty();
        }

        // Valid OTP found. Delete the token immediately after success.
        tokenRepository.delete(otpToken);

        return Optional.of(user);
    }

    // NEW: Combined registration confirmation logic
    @Transactional
    public String confirmUserAccountWithOtp(String otp, String username) {
        Optional<User> verifiedUser = verifyOtp(otp, username, TokenType.REGISTRATION);

        if (verifiedUser.isPresent()) {
            User user = verifiedUser.get();
            user.setEmailVerified(true);
            userRepository.save(user);
            return "Verification successful: Your account is now active!";
        } else {
            return "Invalid or expired OTP. Please check the code, request a new one, and try again.";
        }
    }

    @Transactional
    public void resetPassword(String email, String newPassword) {
        User user = userRepository.findByUsername(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found for reset."));

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    public Optional<Customer> getCustomerDetailsByUsername(String username) {
        return userRepository.findByUsername(username)
                .flatMap(user -> customerRepository.findByUserId(user.getId()));
    }

    /**
     * NEW: Creates a DTO from the Customer entity for form pre-population.
     */
    public RegistrationDTO getProfileDTOFromCustomer(Customer customer) {
        RegistrationDTO dto = new RegistrationDTO();
        dto.setFirstName(customer.getFirstName());
        dto.setLastName(customer.getLastName());
        dto.setUsername(customer.getUser().getUsername()); // Email
        dto.setPhoneNumber(customer.getPhoneNumber());
        dto.setPreferredSize(customer.getPreferredSize());
        dto.setGender(customer.getGender());
        dto.setNewsletterOptIn(customer.getNewsletterOptIn());

        if (customer.getDateOfBirth() != null) {
            dto.setDateOfBirth(new SimpleDateFormat("yyyy-MM-dd").format(customer.getDateOfBirth()));
        }

        return dto;
    }


    /**
     * NEW: Updates customer details (names, phone, optional fields).
     */
    @Transactional
    public void updateCustomerProfile(String currentUsername, RegistrationDTO profileDTO) {
        User user = userRepository.findByUsername(currentUsername)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found."));

        Customer customer = customerRepository.findByUserId(user.getId())
                .orElseThrow(() -> new IllegalStateException("Customer profile not found."));

        // 1. Validate Phone Number change
        if (!customer.getPhoneNumber().equals(profileDTO.getPhoneNumber())) {
            // Check if the new phone number is already registered by another customer
            Optional<Customer> existingCustomerWithNewPhone = customerRepository.findByPhoneNumber(profileDTO.getPhoneNumber());

            if (existingCustomerWithNewPhone.isPresent() && !existingCustomerWithNewPhone.get().getId().equals(customer.getId())) {
                throw new IllegalStateException("The new phone number is already registered with another account.");
            }
            customer.setPhoneNumber(profileDTO.getPhoneNumber());
        }

        // 2. Update Customer fields
        customer.setFirstName(profileDTO.getFirstName());
        customer.setLastName(profileDTO.getLastName());
        customer.setPreferredSize(profileDTO.getPreferredSize());
        customer.setGender(profileDTO.getGender());
        // FIX: Ensure newsletterOptIn handles null/false correctly
        customer.setNewsletterOptIn(profileDTO.getNewsletterOptIn() != null && profileDTO.getNewsletterOptIn());

        // Update Date of Birth
        if (profileDTO.getDateOfBirth() != null && !profileDTO.getDateOfBirth().isEmpty()) {
            try {
                Date dob = new SimpleDateFormat("yyyy-MM-dd").parse(profileDTO.getDateOfBirth());
                customer.setDateOfBirth(dob);
            } catch (Exception e) {
                System.err.println("Failed to parse DOB during update: " + e.getMessage());
            }
        } else {
            customer.setDateOfBirth(null); // Allow clearing DOB
        }

        customerRepository.save(customer);
        // Note: User.username (email) is NOT updated here. That requires a separate secure flow.
    }

    /**
     * NEW: Changes the customer's password.
     */
    @Transactional
    public void changePassword(String username, String currentPassword, String newPassword, String confirmPassword) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found."));

        // 1. Validate current password
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new IllegalStateException("Your current password is not correct.");
        }

        // 2. Validate new password match
        if (!newPassword.equals(confirmPassword)) {
            throw new IllegalStateException("New passwords do not match.");
        }

        // 3. Validate new password strength (using the same pattern as registration DTO)
        if (!PASSWORD_POLICY_PATTERN.matcher(newPassword).matches()) {
            throw new IllegalStateException("New password does not meet complexity requirements (Min 8 chars, 1 uppercase, 1 lowercase, 1 number).");
        }

        // 4. Update and Save
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    /**
     * NEW: Updates a registered customer's newsletter opt-in status.
     */
    @Transactional
    public void updateNewsletterOptIn(String username, boolean optIn) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("User not found."));

        Customer customer = customerRepository.findByUserId(user.getId())
                .orElseThrow(() -> new IllegalStateException("Customer profile not found."));

        customer.setNewsletterOptIn(optIn);
        customerRepository.save(customer);
    }
}