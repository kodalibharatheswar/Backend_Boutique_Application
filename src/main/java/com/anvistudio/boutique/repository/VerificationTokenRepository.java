package com.anvistudio.boutique.repository;

import com.anvistudio.boutique.model.VerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Repository for managing verification tokens.
 */
public interface VerificationTokenRepository extends JpaRepository<VerificationToken, Long> {

    /**
     * Finds a token by its unique string value.
     */
    Optional<VerificationToken> findByToken(String token);

    /**
     * Finds a token associated with a specific user.
     */
    Optional<VerificationToken> findByUserId(Long userId);

    /**
     * Delete a token associated with a specific user.
     */
    void deleteByUserId(Long userId);
}