package com.anvistudio.boutique.repository;

import com.anvistudio.boutique.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    /**
     * Finds customer details linked to a specific User ID.
     */
    Optional<Customer> findByUserId(Long userId);

    /**
     * NEW: Finds a customer by their unique phone number.
     */
    Optional<Customer> findByPhoneNumber(String phoneNumber);
}