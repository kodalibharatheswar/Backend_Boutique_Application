package com.anvistudio.boutique.repository;

import com.anvistudio.boutique.model.NewsletterSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface NewsletterSubscriptionRepository extends JpaRepository<NewsletterSubscription, Long> {

    /**
     * Finds a subscription by email address.
     */
    Optional<NewsletterSubscription> findByEmail(String email);

    /**
     * Finds all active subscribers for batch mailing.
     */
    List<NewsletterSubscription> findByIsActiveTrue();
}