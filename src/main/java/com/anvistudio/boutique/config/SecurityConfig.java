package com.anvistudio.boutique.config;

import com.anvistudio.boutique.model.User;
import com.anvistudio.boutique.repository.UserRepository;
import com.anvistudio.boutique.service.CustomOAuth2UserService;
import jakarta.servlet.http.Cookie;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Autowired
    @Lazy
    private UserDetailsService userDetailsService;

    @Autowired
    private CustomOAuth2UserService customOAuth2UserService;

    @Autowired
    private JwtAuthenticationFilter jwtAuthFilter;

    @Autowired
    private RateLimitingFilter rateLimitingFilter;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserRepository userRepository;

    @Value("${app.oauth2.success-redirect-url:http://localhost:3000/oauth2/redirect}")
    private String oauth2SuccessRedirectUrl;

    @Value("${app.oauth2.failure-redirect-url:http://localhost:3000/login?error=oauth2}")
    private String oauth2FailureRedirectUrl;

    @Value("${frontend.url:http://localhost:3000}")
    private String frontendUrl;

    /**
     * Bean to handle X-Forwarded-* headers from Nginx.
     * This is crucial for preventing redirect loops when behind a proxy.
     */
    @Bean
    public org.springframework.web.filter.ForwardedHeaderFilter forwardedHeaderFilter() {
        return new org.springframework.web.filter.ForwardedHeaderFilter();
    }

    /**
     * Expose AuthenticationManager as a Bean.
     * Spring Security 6+ auto-configures DaoAuthenticationProvider when it
     * detects a UserDetailsService and PasswordEncoder bean - no need to
     * manually create one.
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * Custom failure handler for form-based login (Thymeleaf pages).
     */
    @Bean
    public AuthenticationFailureHandler authenticationFailureHandler() {
        return (request, response, exception) -> {
            String redirectUrl = "/login?error";
            if (exception instanceof DisabledException) {
                redirectUrl = "/confirm-otp?email=" + request.getParameter("username") + "&error=unverified";
            } else if (exception.getMessage() != null && exception.getMessage().contains("Bad credentials")) {
                redirectUrl = "/login?error=bad_credentials";
            }
            response.sendRedirect(redirectUrl);
        };
    }

    /**
     * CORS configuration permitting the React frontend at localhost:3000
     * to send requests (including cookies) to the backend.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(frontendUrl, "http://localhost:3000"));
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        config.setExposedHeaders(Arrays.asList("Authorization", "Content-Type", "X-Total-Count"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // ── CORS ──────────────────────────────────────────────────────────
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // ── CSRF: disable for API routes only ────────────────────────────
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers("/api/**", "/oauth2/**"))

                // ── Session: keep sessions for OAuth2 redirect dance ─────────────
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))

                // ── JWT filter wiring ─────────────────────────────────────────────
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(rateLimitingFilter, JwtAuthenticationFilter.class)

                // ── Request authorisations ────────────────────────────────────────
                .authorizeHttpRequests(auth -> auth
                        // Always allow pre-flight
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // Public API endpoints
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/public/**").permitAll()
                        .requestMatchers("/api/ws-live/**").permitAll()

                        // OAuth2 endpoints (must be open for redirect flow)
                        .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()

                        // Protected API routes
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/customer/**").hasRole("CUSTOMER")

                        // Traditional Thymeleaf admin/customer routes
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers("/customer/**").hasRole("CUSTOMER")

                        // Public Thymeleaf pages
                        .requestMatchers(
                                "/", "/login", "/register", "/about", "/contact",
                                "/products", "/products/**",
                                "/wishlist-unauth", "/cart-unauth",
                                "/custom-request", "/confirm-otp",
                                "/forgot-password", "/reset-otp", "/reset-password",
                                "/newsletter/subscribe",
                                "/policy_return", "/policy_privacy", "/policy_terms", "/policy_shipping",
                                "/customer/profile/verify-new-email",
                                "/css/**", "/js/**", "/images/**", "/static/**",
                                "/favicon.ico")
                        .permitAll()

                        .anyRequest().authenticated())

                // ── Form login (Thymeleaf pages) ──────────────────────────────────
                .formLogin(form -> form
                        .loginPage("/login")
                        .permitAll()
                        .failureHandler(authenticationFailureHandler())
                        .successHandler((request, response, authentication) -> {
                            boolean isAdmin = authentication.getAuthorities().stream()
                                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
                            response.sendRedirect(isAdmin ? "/admin/dashboard" : "/");
                        }))

                // ── OAuth2 login (Google etc.) ────────────────────────────────────
                .oauth2Login(oauth2 -> oauth2
                        .loginPage("/login")
                        .userInfoEndpoint(userInfo -> userInfo
                                .userService(customOAuth2UserService))
                        .successHandler((request, response, authentication) -> {
                            // Extract email from the OAuth2 user
                            String email = null;
                            if (authentication.getPrincipal() instanceof OAuth2User oauthUser) {
                                email = (String) oauthUser.getAttributes().get("email");
                            }
                            if (email == null) {
                                email = authentication.getName();
                            }

                            // Find the user in DB (created by CustomOAuth2UserService)
                            Optional<User> userOpt = userRepository.findByUsername(email);
                            if (userOpt.isPresent()) {
                                User user = userOpt.get();
                                String role = user.getRole() != null ? user.getRole().toString() : "CUSTOMER";

                                // Build a UserDetails for JWT generation
                                org.springframework.security.core.userdetails.UserDetails ud = org.springframework.security.core.userdetails.User
                                        .withUsername(user.getUsername())
                                        .password("{noop}OAUTH2")
                                        .roles(role)
                                        .build();

                                // Generate JWT token with role claim
                                java.util.Map<String, Object> extraClaims = new java.util.HashMap<>();
                                extraClaims.put("role", role);
                                String accessToken = jwtUtil.generateToken(extraClaims, ud);

                                // IMPORTANT: Pass token as URL param because the backend (port 8080)
                                // and frontend (port 3000) are on different origins. A cookie set in
                                // this redirect response would be stored under localhost:8080 and
                                // would NOT be sent when the React proxy forwards requests from 3000.
                                response.sendRedirect(
                                        oauth2SuccessRedirectUrl + "?token=" + accessToken);
                            } else {
                                System.err.println("OAuth2 success handler: user not found in DB for email: " + email);
                                response.sendRedirect(oauth2FailureRedirectUrl);
                            }
                        })
                        .failureHandler((request, response, exception) -> {
                            System.err.println("OAuth2 login failed: " + exception.getMessage());
                            response.sendRedirect(oauth2FailureRedirectUrl);
                        }))

                // ── Logout ────────────────────────────────────────────────────────
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .permitAll()
                        .logoutSuccessHandler((request, response, authentication) -> {
                            response.setStatus(200);
                        })
                        .deleteCookies("JSESSIONID", "accessToken", "refreshToken"))

                // ── Exception handling: return 401 JSON for API callers ───────────
                .exceptionHandling(exceptions -> exceptions
                        // For /api/** requests: return HTTP 401 — never redirect
                        .defaultAuthenticationEntryPointFor(
                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                                request -> request.getServletPath().startsWith("/api/")))

                // ── Security headers ──────────────────────────────────────────────
                .headers(headers -> headers
                        .frameOptions(frame -> frame.sameOrigin()));

        return http.build();
    }
}
