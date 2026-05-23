package com.anvistudio.boutique.repository;

import com.anvistudio.boutique.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Finds a User by their username. This is crucial for Spring Security's login process.
     * @param username The user's login name.
     * @return An Optional containing the User if found.
     */
    Optional<User> findByUsername(String username);
}