package com.anvistudio.boutique.repository;

import com.anvistudio.boutique.model.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Repository for managing product reviews.
 */
public interface ReviewRepository extends JpaRepository<Review, Long> {

    /**
     * Finds all approved reviews for a specific product.
     */
    List<Review> findByProductIdAndIsApprovedTrueOrderByDatePostedDesc(Long productId);

    /**
     * Finds a user's existing review for a specific product.
     */
    Optional<Review> findByUserIdAndProductId(Long userId, Long productId);

    /**
     * Calculates the average rating for a specific product based on approved reviews.
     */
    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.product.id = :productId AND r.isApproved = true")
    Optional<Double> findAverageRatingByProductId(@Param("productId") Long productId);

    /**
     * Counts the total number of approved reviews for a product.
     */
    long countByProductIdAndIsApprovedTrue(Long productId);

    /**
     * Finds all unapproved reviews (for admin moderation).
     */
    List<Review> findByIsApprovedFalseOrderByDatePostedAsc();
}