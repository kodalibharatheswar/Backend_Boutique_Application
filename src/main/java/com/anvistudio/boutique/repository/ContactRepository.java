package com.anvistudio.boutique.repository;

import com.anvistudio.boutique.model.ContactMessage;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for handling persistence operations for contact messages.
 * Extends JpaRepository to inherit methods like save(), findAll(), etc.
 */
public interface ContactRepository extends JpaRepository<ContactMessage, Long> {
    // We do not need to define any custom methods here yet,
    // as the JpaRepository provides save() which is used by ContactService.
}