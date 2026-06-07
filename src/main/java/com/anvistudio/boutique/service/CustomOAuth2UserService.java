package com.anvistudio.boutique.service;

import com.anvistudio.boutique.model.Customer;
import com.anvistudio.boutique.model.User;
import com.anvistudio.boutique.repository.CustomerRepository;
import com.anvistudio.boutique.repository.UserRepository;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;

    public CustomOAuth2UserService(UserRepository userRepository, CustomerRepository customerRepository) {
        this.userRepository = userRepository;
        this.customerRepository = customerRepository;
    }

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        return processOAuth2User(registrationId, oAuth2User);
    }

    private OAuth2User processOAuth2User(String registrationId, OAuth2User oAuth2User) {
        Map<String, Object> attributes = oAuth2User.getAttributes();
        String email = (String) attributes.get("email");
        String providerId = oAuth2User.getName(); // 'sub' claim from Google

        if (email == null) {
            throw new OAuth2AuthenticationException("Email not found from OAuth2 provider");
        }

        // Extract clean name fields directly from Google's attributes
        String givenName = (String) attributes.get("given_name");
        String familyName = (String) attributes.get("family_name");
        String fullName = (String) attributes.get("name");

        // Derive clean firstName / lastName
        String firstName = (givenName != null && !givenName.isBlank()) ? givenName.trim()
                : (fullName != null && !fullName.isBlank()) ? fullName.trim()
                        : "Social";
        String lastName = (familyName != null && !familyName.isBlank()) ? familyName.trim()
                : "User";

        Optional<User> userOptional = userRepository.findByUsername(email);
        User user;

        if (userOptional.isPresent()) {
            // --- Existing user: update provider info if needed ---
            user = userOptional.get();
            boolean changed = false;
            if (user.getAuthProvider() == null) {
                user.setAuthProvider(registrationId.toUpperCase());
                user.setProviderId(providerId);
                changed = true;
            }
            if (changed) {
                userRepository.save(user);
            }

            // Always refresh the customer profile name from Google's fresh data
            // (this also fixes any previously-corrupted firstName values)
            customerRepository.findByUserId(user.getId()).ifPresent(customer -> {
                customer.setFirstName(firstName);
                customer.setLastName(lastName);
                customerRepository.save(customer);
                System.out.println("OAuth2: Updated customer profile for " + email
                        + " → " + firstName + " " + lastName);
            });

        } else {
            // --- New user: create User + Customer profile ---
            user = new User();
            user.setUsername(email);
            user.setPassword(""); // No local password for social users
            user.setRole("CUSTOMER");
            user.setEmailVerified(true); // Google verifies emails
            user.setAuthProvider(registrationId.toUpperCase());
            user.setProviderId(providerId);
            user = userRepository.save(user);

            Customer customer = new Customer();
            customer.setUser(user);
            customer.setFirstName(firstName);
            customer.setLastName(lastName);
            customer.setPhoneNumber("0000000000"); // Placeholder — user should update
            customer.setTermsAccepted(true); // Implicitly accepted via social login

            customerRepository.save(customer);
            System.out.println("OAuth2: Registered new social user " + email
                    + " (" + registrationId + ") → " + firstName + " " + lastName);
        }

        return oAuth2User;
    }
}
