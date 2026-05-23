package com.anvistudio.boutique.repository;

import com.anvistudio.boutique.model.Address;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AddressRepository extends JpaRepository<Address, Long> {
    /**
     * Finds all saved addresses for a specific user.
     */
    List<Address> findByUserId(Long userId);
}