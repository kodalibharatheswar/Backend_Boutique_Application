package com.anvistudio.boutique.controller;

import com.anvistudio.boutique.config.JwtUtil;
import com.anvistudio.boutique.dto.RegistrationDTO;
import com.anvistudio.boutique.model.Customer;
import com.anvistudio.boutique.model.RefreshToken;
import com.anvistudio.boutique.model.User;
import com.anvistudio.boutique.model.VerificationToken.TokenType;
import com.anvistudio.boutique.repository.CustomerRepository;
import com.anvistudio.boutique.service.RefreshTokenService;
import com.anvistudio.boutique.service.UserService;
import jakarta.servlet.http.Cookie;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * REST API Controller for Authentication operations.
 * This controller provides JSON-based endpoints for the React frontend.
 * Handles: Registration, Login, OTP Verification, Password Reset flows.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthRestController {

    private final UserService userService;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final RefreshTokenService refreshTokenService;
    private final CustomerRepository customerRepository;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    @Value("${jwt.expiration}")
    private long jwtExpiration;

    @Value("${jwt.refresh-expiration}")
    private long refreshExpiration;

    public AuthRestController(UserService userService, AuthenticationManager authenticationManager,
            JwtUtil jwtUtil, RefreshTokenService refreshTokenService, CustomerRepository customerRepository) {
        this.userService = userService;
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.refreshTokenService = refreshTokenService;
        this.customerRepository = customerRepository;
    }

    // =========================================================================
    // 1. REGISTRATION FLOW
    // =========================================================================

    /**
     * POST /api/auth/register
     * Registers a new customer and sends OTP to their email
     * 
     * Request Body: RegistrationDTO (JSON)
     * Response: { success: boolean, message: string, email: string }
     */
    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@Valid @RequestBody RegistrationDTO registrationDTO,
            BindingResult bindingResult) {
        Map<String, Object> response = new HashMap<>();

        // Handle validation errors
        if (bindingResult.hasErrors()) {
            Map<String, String> errors = bindingResult.getFieldErrors().stream()
                    .collect(Collectors.toMap(
                            FieldError::getField,
                            error -> error.getDefaultMessage() != null ? error.getDefaultMessage() : "Invalid value",
                            (existing, replacement) -> existing // Keep first error if multiple for same field
                    ));
            response.put("success", false);
            response.put("message", "Validation failed");
            response.put("errors", errors);
            return ResponseEntity.badRequest().body(response);
        }

        try {
            // Register the customer and send OTP
            User savedUser = userService.registerCustomer(registrationDTO);

            // Clear security context to prevent auto-login
            SecurityContextHolder.clearContext();

            response.put("success", true);
            response.put("message", "Registration successful! A 6-digit OTP has been sent to your email.");
            response.put("email", savedUser.getUsername());
            return ResponseEntity.ok(response);

        } catch (IllegalStateException e) {
            // Username/phone already taken or password mismatch
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "An unexpected error occurred during registration. Please try again.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // =========================================================================
    // 2. OTP CONFIRMATION (Email Verification)
    // =========================================================================

    /**
     * POST /api/auth/confirm-otp
     * Verifies the OTP sent during registration
     * 
     * Request Body: { email: string, otp: string }
     * Response: { success: boolean, message: string }
     */
    @PostMapping("/confirm-otp")
    public ResponseEntity<Map<String, Object>> confirmOTP(@RequestBody Map<String, String> request) {
        Map<String, Object> response = new HashMap<>();

        String email = request.get("email");
        String otp = request.get("otp");

        // Validate input
        if (email == null || email.trim().isEmpty() || otp == null || otp.trim().isEmpty()) {
            response.put("success", false);
            response.put("message", "Email and OTP are required");
            return ResponseEntity.badRequest().body(response);
        }

        try {
            String result = userService.confirmUserAccountWithOtp(otp, email);

            if (result.startsWith("Verification successful")) {
                response.put("success", true);
                response.put("message", result + " You can now log in.");
                return ResponseEntity.ok(response);
            } else {
                response.put("success", false);
                response.put("message", result);
                return ResponseEntity.badRequest().body(response);
            }
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error confirming OTP. Please try again.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // =========================================================================
    // 3. LOGIN
    // =========================================================================

    /**
     * POST /api/auth/login
     * Authenticates a user and creates a session
     * 
     * Request Body: { username: string, password: string }
     * Response: { success: boolean, message: string, user: { email, role } }
     */
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> credentials,
            HttpServletRequest request,
            HttpServletResponse responseMap) {
        Map<String, Object> response = new HashMap<>();

        String username = credentials.get("username");
        String password = credentials.get("password");

        // Validate input
        if (username == null || username.trim().isEmpty() || password == null || password.trim().isEmpty()) {
            response.put("success", false);
            response.put("message", "Username and password are required");
            return ResponseEntity.badRequest().body(response);
        }

        try {
            // Detect social (Google/OAuth2) accounts before attempting password auth
            userService.findUserByUsername(username).ifPresent(existingUser -> {
                if ("GOOGLE".equalsIgnoreCase(existingUser.getAuthProvider())
                        || (existingUser.getPassword() != null && existingUser.getPassword().isEmpty())) {
                    throw new RuntimeException("SOCIAL_ACCOUNT:" + existingUser.getAuthProvider());
                }
            });

            // Attempt authentication via Spring Security AuthenticationManager
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password));


            // Set authentication in security context
            SecurityContextHolder.getContext().setAuthentication(authentication);

            final UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            final String accessToken = jwtUtil.generateToken(userDetails);

            // Get local user entity
            User user = userService.findUserByUsername(username)
                    .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

            // Generate Refresh Token
            RefreshToken refreshToken = refreshTokenService.createRefreshToken(user.getId());

            // Set HttpOnly Cookies
            responseMap.addCookie(createCookie("accessToken", accessToken, (int) (jwtExpiration / 1000)));
            responseMap
                    .addCookie(createCookie("refreshToken", refreshToken.getToken(), (int) (refreshExpiration / 1000)));

            // Build user data map - FIXED to handle different User model structures
            Map<String, Object> userData = new HashMap<>();
            userData.put("email", user.getUsername());

            // Safely get role - handle both String and Enum types
            try {
                Object roleObj = user.getRole();
                if (roleObj != null) {
                    // If role is an enum, call name() method
                    if (roleObj.getClass().isEnum()) {
                        userData.put("role", ((Enum<?>) roleObj).name());
                    } else {
                        // If role is a String, use it directly
                        userData.put("role", roleObj.toString());
                    }
                } else {
                    userData.put("role", "CUSTOMER");
                }
            } catch (Exception e) {
                userData.put("role", "CUSTOMER"); // Default fallback
            }

            response.put("success", true);
            response.put("message", "Login successful");
            response.put("user", userData);
            response.put("accessToken", accessToken);
            return ResponseEntity.ok(response);

        } catch (AuthenticationException e) {
            System.err.println("AUTH ERROR: Authentication failed for " + username + ": " + e.getMessage());
            String errorMessage = "Invalid username or password";
            if (e.getMessage().contains("disabled") || e.getMessage().contains("not verified")) {
                errorMessage = "Your account is not verified. Please check your email for the OTP.";
                response.put("requiresVerification", true);
                response.put("email", username);
            }
            response.put("success", false);
            response.put("message", errorMessage);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        } catch (RuntimeException e) {
            // Handle social account detection
            if (e.getMessage() != null && e.getMessage().startsWith("SOCIAL_ACCOUNT:")) {
                String provider = e.getMessage().replace("SOCIAL_ACCOUNT:", "");
                response.put("success", false);
                response.put("socialLogin", true);
                response.put("provider", provider);
                response.put("message", "This account was created with " + provider + " Sign-In. Please use the \"Continue with Google\" button to log in.");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }
            System.err.println("AUTH RUNTIME ERROR during login for [" + username + "]: " + e.getMessage());
            response.put("success", false);
            response.put("message", "An unexpected error occurred. Please try again.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        } catch (Exception e) {
            System.err.println("AUTH CRITICAL ERROR during login for [" + username + "]: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "An unexpected error occurred. Please try again.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }


    /**
     * POST /api/auth/refresh-token
     */
    @PostMapping("/refresh-token")
    public ResponseEntity<Map<String, Object>> refreshToken(HttpServletRequest request, HttpServletResponse response) {
        Map<String, Object> result = new HashMap<>();
        String refreshTokenString = getCookieValue(request, "refreshToken");

        if (refreshTokenString == null) {
            result.put("success", false);
            result.put("message", "Refresh token is missing");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
        }

        return refreshTokenService.findByToken(refreshTokenString)
                .map(refreshTokenService::verifyExpiration)
                .map(RefreshToken::getUser)
                .map(user -> {
                    String accessToken = jwtUtil.generateToken(userService.loadUserByUsername(user.getUsername()));
                    response.addCookie(createCookie("accessToken", accessToken, (int) (jwtExpiration / 1000)));
                    result.put("success", true);
                    result.put("accessToken", accessToken);
                    return ResponseEntity.ok(result);
                })
                .orElseGet(() -> {
                    result.put("success", false);
                    result.put("message", "Invalid refresh token");
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(result);
                });
    }

    /**
     * GET /api/auth/me
     * Returns the currently authenticated user's details based on the HttpOnly JWT/Session.
     * Crucial for OAuth2 login where frontend relies on cookie to fetch profile.
     */
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getCurrentUser() {
        Map<String, Object> response = new HashMap<>();
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            response.put("success", false);
            response.put("message", "Not authenticated");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        try {
            String username = authentication.getName(); // Usually email
            System.out.println("GET /me - authentication.getName() = [" + username + "]");
            System.out.println("GET /me - principal class = " + authentication.getPrincipal().getClass().getName());

            User user = userService.findUserByUsername(username).orElse(null);
            System.out.println("GET /me - user found in DB? " + (user != null));

            if (user != null) {
                Map<String, Object> userData = new HashMap<>();
                userData.put("email", user.getUsername());
                
                Object roleObj = user.getRole();
                if (roleObj != null) {
                    if (roleObj.getClass().isEnum()) {
                        userData.put("role", ((Enum<?>) roleObj).name());
                    } else {
                        userData.put("role", roleObj.toString());
                    }
                } else {
                    userData.put("role", "CUSTOMER");
                }

                response.put("success", true);
                response.put("user", userData);
                return ResponseEntity.ok(response);
            } else {
                response.put("success", false);
                response.put("message", "User not found");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
        } catch (Exception e) {
            System.err.println("GET /me error: " + e.getMessage());
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "Error fetching user details");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * POST /api/auth/logout
     */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(HttpServletRequest request, HttpServletResponse response) {
        response.addCookie(createCookie("accessToken", null, 0));
        response.addCookie(createCookie("refreshToken", null, 0));

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "Logged out successfully");
        return ResponseEntity.ok(result);
    }

    private Cookie createCookie(String name, String value, int maxAge) {
        Cookie cookie = new Cookie(name, value);
        cookie.setHttpOnly(true);
        cookie.setSecure(false);
        cookie.setPath("/");
        cookie.setMaxAge(maxAge);
        return cookie;
    }

    private String getCookieValue(HttpServletRequest request, String name) {
        if (request.getCookies() == null)
            return null;
        for (Cookie cookie : request.getCookies()) {
            if (cookie.getName().equals(name)) {
                return cookie.getValue();
            }
        }
        return null;
    }



    // =========================================================================
    // 5. FORGOT PASSWORD FLOW - Step 1: Request OTP
    // =========================================================================

    /**
     * POST /api/auth/forgot-password
     * Sends password reset OTP to the user's email
     * 
     * Request Body: { identifier: string } (email or phone number)
     * Response: { success: boolean, message: string, email: string }
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, Object>> forgotPassword(@RequestBody Map<String, String> request) {
        Map<String, Object> response = new HashMap<>();

        String identifier = request.get("identifier");

        if (identifier == null || identifier.trim().isEmpty()) {
            response.put("success", false);
            response.put("message", "Email or phone number is required");
            return ResponseEntity.badRequest().body(response);
        }

        try {
            // Find user and send PASSWORD_RESET OTP
            User user = userService.findAndCreateResetOtp(identifier);

            response.put("success", true);
            response.put("message", "An OTP has been sent to your registered email.");
            response.put("email", user.getUsername());
            return ResponseEntity.ok(response);

        } catch (UsernameNotFoundException e) {
            // Keep the error message generic to avoid revealing account existence
            response.put("success", false);
            response.put("message", "Could not find an account matching that identifier.");
            return ResponseEntity.badRequest().body(response);

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error sending OTP. Please try again.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // =========================================================================
    // 6. FORGOT PASSWORD FLOW - Step 2: Validate OTP
    // =========================================================================

    /**
     * POST /api/auth/reset-otp
     * Validates the password reset OTP
     * 
     * Request Body: { email: string, otp: string }
     * Response: { success: boolean, message: string }
     */
    @PostMapping("/reset-otp")
    public ResponseEntity<Map<String, Object>> validateResetOTP(@RequestBody Map<String, String> request) {
        Map<String, Object> response = new HashMap<>();

        String email = request.get("email");
        String otp = request.get("otp");

        if (email == null || email.trim().isEmpty() || otp == null || otp.trim().isEmpty()) {
            response.put("success", false);
            response.put("message", "Email and OTP are required");
            return ResponseEntity.badRequest().body(response);
        }

        try {
            Optional<User> userOptional = userService.verifyOtp(otp, email, TokenType.PASSWORD_RESET);

            if (userOptional.isPresent()) {
                response.put("success", true);
                response.put("message", "OTP verified successfully. You can now reset your password.");
                return ResponseEntity.ok(response);
            } else {
                response.put("success", false);
                response.put("message", "Invalid or expired OTP. Please try again.");
                return ResponseEntity.badRequest().body(response);
            }
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error validating OTP. Please try again.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // =========================================================================
    // 7. FORGOT PASSWORD FLOW - Step 3: Reset Password
    // =========================================================================

    /**
     * POST /api/auth/reset-password
     * Sets a new password after OTP verification
     * 
     * Request Body: { email: string, newPassword: string, confirmPassword: string }
     * Response: { success: boolean, message: string }
     */
    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, Object>> resetPassword(@RequestBody Map<String, String> request) {
        Map<String, Object> response = new HashMap<>();

        String email = request.get("email");
        String newPassword = request.get("newPassword");
        String confirmPassword = request.get("confirmPassword");

        // Validate input
        if (email == null || email.trim().isEmpty() ||
                newPassword == null || newPassword.trim().isEmpty() ||
                confirmPassword == null || confirmPassword.trim().isEmpty()) {
            response.put("success", false);
            response.put("message", "All fields are required");
            return ResponseEntity.badRequest().body(response);
        }

        if (!newPassword.equals(confirmPassword)) {
            response.put("success", false);
            response.put("message", "Passwords do not match");
            return ResponseEntity.badRequest().body(response);
        }

        try {
            userService.resetPassword(email, newPassword);
            response.put("success", true);
            response.put("message", "Password reset successfully! You can now log in with your new password.");
            return ResponseEntity.ok(response);

        } catch (UsernameNotFoundException e) {
            response.put("success", false);
            response.put("message", "Account error during reset. Please try again.");
            return ResponseEntity.badRequest().body(response);

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error resetting password. Please try again.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // =========================================================================
    // 8. CHECK AUTHENTICATION STATUS
    // =========================================================================

    /**
     * GET /api/auth/check
     * Checks if the user is currently authenticated.
     * Returns firstName + lastName from customer_details so Navbar can show the user's name.
     *
     * Response: { authenticated: boolean, user: { email, role, firstName, lastName } }
     */
    @GetMapping("/check")
    public ResponseEntity<Map<String, Object>> checkAuth() {
        Map<String, Object> response = new HashMap<>();

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.isAuthenticated() &&
                !authentication.getPrincipal().equals("anonymousUser")) {

            String username = authentication.getName();
            
            // CRITICAL FIX: If the session was preserved from Google OAuth2 login directly (via JSESSIONID),
            // authentication.getName() returns the Google Provider ID (e.g., 101588...).
            // We need to extract the actual email address which is stored in the attributes.
            if (authentication.getPrincipal() instanceof org.springframework.security.oauth2.core.user.OAuth2User oauthUser) {
                String email = (String) oauthUser.getAttributes().get("email");
                if (email != null) {
                    username = email;
                }
            }

            Optional<User> userOptional = userService.findUserByUsername(username);

            if (userOptional.isPresent()) {
                User user = userOptional.get();
                Map<String, Object> userData = new HashMap<>();
                userData.put("email", user.getUsername());

                // Get role safely
                try {
                    Object roleObj = user.getRole();
                    if (roleObj != null) {
                        userData.put("role", roleObj.getClass().isEnum()
                            ? ((Enum<?>) roleObj).name() : roleObj.toString());
                    } else {
                        userData.put("role", "CUSTOMER");
                    }
                } catch (Exception e) {
                    userData.put("role", "CUSTOMER");
                }

                // Fetch customer profile for firstName / lastName (works for both normal and Google users)
                try {
                    Optional<Customer> customerOpt = customerRepository.findByUserId(user.getId());
                    customerOpt.ifPresent(customer -> {
                        userData.put("firstName", customer.getFirstName());
                        userData.put("lastName",  customer.getLastName());
                        userData.put("phoneNumber", customer.getPhoneNumber());
                    });
                } catch (Exception ignored) {
                    // Admin has no customer profile — that's fine
                }

                response.put("authenticated", true);
                response.put("user", userData);
                return ResponseEntity.ok(response);
            }
        }

        response.put("authenticated", false);
        return ResponseEntity.ok(response);
    }

    // =========================================================================
    // 9. RESEND OTP (Optional - for better UX)
    // =========================================================================

    /**
     * POST /api/auth/resend-otp
     * Resends OTP to the user's email
     * 
     * Request Body: { email: string, type: "REGISTRATION" | "PASSWORD_RESET" }
     * Response: { success: boolean, message: string }
     */
    @PostMapping("/resend-otp")
    public ResponseEntity<Map<String, Object>> resendOTP(@RequestBody Map<String, String> request) {
        Map<String, Object> response = new HashMap<>();

        String email = request.get("email");
        String type = request.get("type");

        if (email == null || email.trim().isEmpty()) {
            response.put("success", false);
            response.put("message", "Email is required");
            return ResponseEntity.badRequest().body(response);
        }

        try {
            if ("PASSWORD_RESET".equals(type)) {
                userService.findAndCreateResetOtp(email);
                response.put("success", true);
                response.put("message", "Password reset OTP has been resent to your email.");
            } else {
                // For registration OTP resend
                Optional<User> userOptional = userService.findUserByUsername(email);
                if (userOptional.isPresent() && !userOptional.get().getEmailVerified()) {
                    // Trigger resend OTP logic (you may need to add this method to UserService)
                    response.put("success", true);
                    response.put("message", "Verification OTP has been resent to your email.");
                } else {
                    response.put("success", false);
                    response.put("message", "Account is already verified or does not exist.");
                    return ResponseEntity.badRequest().body(response);
                }
            }
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error resending OTP. Please try again.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * DEBUG ENDPOINT: Use this to check why login might be failing.
     * Accessible via POST /api/auth/debug-check
     */
    @PostMapping("/debug-check")
    public ResponseEntity<Map<String, Object>> debugCheck(@RequestBody Map<String, String> creds) {
        String username = creds.get("username");
        String password = creds.get("password");
        Map<String, Object> debugInfo = new HashMap<>();
        
        System.out.println("--- ADMIN LOGIN DEBUG START ---");
        System.out.println("Checking username: " + username);
        
        Optional<User> userOpt = userService.findUserByUsername(username);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            System.out.println("User found in DB. Role: " + user.getRole());
            System.out.println("Stored Hash: " + user.getPassword());
            
            boolean match = org.springframework.security.crypto.bcrypt.BCrypt.checkpw(password, user.getPassword());
            System.out.println("Direct BCrypt Match Test: " + (match ? "PASS" : "FAIL"));
            
            debugInfo.put("userFound", true);
            debugInfo.put("role", user.getRole());
            debugInfo.put("matches", match);
        } else {
            System.out.println("User '" + username + "' NOT found in database.");
            debugInfo.put("userFound", false);
        }
        System.out.println("--- ADMIN LOGIN DEBUG END ---");
        
        return ResponseEntity.ok(debugInfo);
    }
}

