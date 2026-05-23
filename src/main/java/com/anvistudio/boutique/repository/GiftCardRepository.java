package com.anvistudio.boutique.repository;

import com.anvistudio.boutique.model.GiftCard;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface GiftCardRepository extends JpaRepository<GiftCard, Long> {
    /**
     * Finds all gift cards owned by a specific user.
     */
    List<GiftCard> findByUserId(Long userId);
}