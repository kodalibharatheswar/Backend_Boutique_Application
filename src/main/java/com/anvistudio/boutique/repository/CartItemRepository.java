package com.anvistudio.boutique.repository;

import com.anvistudio.boutique.model.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying; // NEW
import org.springframework.transaction.annotation.Transactional; // NEW
import java.util.List;
import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    /**
     * Finds all items in the cart for a specific user ID.
     */
    List<CartItem> findByUserId(Long userId);

    /**
     * Finds a specific item in the cart by user ID and product ID.
     */
    Optional<CartItem> findByUserIdAndProductId(Long userId, Long productId);

    /**
     * Deletes all items in a user's cart.
     */
    @Modifying
    @Transactional
    void deleteByUserId(Long userId);

    /**
     * NEW: Cleans up all cart items referencing a specific product ID.
     * This is required before deleting the product itself.
     */
    @Modifying
    @Transactional
    void deleteByProductId(Long productId);
}