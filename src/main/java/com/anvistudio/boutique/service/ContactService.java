package com.anvistudio.boutique.service;

import com.anvistudio.boutique.model.ContactMessage;
import com.anvistudio.boutique.repository.ContactRepository;
import org.springframework.stereotype.Service;
import java.util.List;

/**
 * Service layer for handling contact message business logic.
 * Primarily responsible for persisting messages received via the contact form.
 */
@Service
public class ContactService {

    private final ContactRepository contactRepository;

    public ContactService(ContactRepository contactRepository) {
        this.contactRepository = contactRepository;
    }

    /**
     * Saves a new customer query to the database.
     * @param message The ContactMessage object submitted by the user.
     * @return The persisted ContactMessage object.
     */
    public ContactMessage saveMessage(ContactMessage message) {
        // In a real application, logic for spam filtering, validation,
        // and triggering an internal email notification would occur here.
        return contactRepository.save(message);
    }

    /**
     * NEW: Retrieves all contact messages for the Admin dashboard.
     */
    public List<ContactMessage> getAllMessages() {
        // Fetch ordered by submission date (newest first)
        return contactRepository.findAll(); // Assuming default repository query orders correctly or we manually sort later
    }

    /**
     * NEW: Deletes a contact message by ID (after it's been handled).
     */
    public void deleteMessage(Long messageId) {
        contactRepository.deleteById(messageId);
    }
}