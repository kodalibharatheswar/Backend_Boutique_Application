package com.anvistudio.boutique.service;

import com.anvistudio.boutique.model.Review;
import com.anvistudio.boutique.model.Product;
import com.anvistudio.boutique.model.User;
import com.anvistudio.boutique.repository.ReviewRepository;
import com.anvistudio.boutique.repository.UserRepository;
import com.anvistudio.boutique.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service layer for handling product reviews and ratings.
 */
@Service
public class ReviewService {

    private final ReviewRepository reviewRepository; // CRITICAL FIX: Made private
    private final UserRepository userRepository;
    private final ProductRepository productRepository;

    public ReviewService(ReviewRepository reviewRepository, UserRepository userRepository, ProductRepository productRepository) {
        this.reviewRepository = reviewRepository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
    }

    /**
     * Submits a new review or updates an existing one by a customer.
     * @param username The authenticated user's username.
     * @param productId The ID of the product being reviewed.
     * @param rating The rating (1-5).
     * @param comment The review text.
     * @return The persisted Review entity.
     */
    @Transactional
    public Review submitReview(String username, Long productId, int rating, String comment) {
        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("Rating must be between 1 and 5.");
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));

        // Check if the user already submitted a review for this product
        Optional<Review> existingReviewOptional = reviewRepository.findByUserIdAndProductId(user.getId(), productId);

        Review review;

        if (existingReviewOptional.isPresent()) {
            // Update existing review
            review = existingReviewOptional.get();
            review.setRating(rating);
            review.setComment(comment);
            review.setIsApproved(false); // Reset approval status upon update (requires admin re-review)
        } else {
            // Create new review
            review = new Review();
            review.setUser(user);
            review.setProduct(product);
            review.setRating(rating);
            review.setComment(comment);
            review.setIsApproved(false); // New reviews require approval
        }

        // Save the review
        return reviewRepository.save(review);
    }

    // Add this method to ReviewService.java

    /**
     * NEW: Checks if a user has already reviewed a specific product.
     * @param username The user's username (email).
     * @param productId The product ID.
     * @return true if the user has already reviewed this product, false otherwise.
     */
    public boolean hasUserReviewedProduct(String username, Long productId) {
        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null) {
            return false;
        }

        return reviewRepository.findByUserIdAndProductId(user.getId(), productId).isPresent();
    }

    /**
     * Retrieves the average rating for a given product ID.
     */
    public double getAverageRating(Long productId) {
        return reviewRepository.findAverageRatingByProductId(productId).orElse(0.0);
    }

    /**
     * Retrieves the count of approved reviews for a given product ID.
     */
    public long getReviewCount(Long productId) {
        return reviewRepository.countByProductIdAndIsApprovedTrue(productId);
    }

    /**
     * Retrieves all approved reviews for a given product ID.
     */
    public List<Review> getApprovedReviewsForProduct(Long productId) {
        return reviewRepository.findByProductIdAndIsApprovedTrueOrderByDatePostedDesc(productId);
    }

    /**
     * NEW: Retrieves all unapproved reviews for Admin Moderation.
     */
    public List<Review> getUnapprovedReviews() {
        return reviewRepository.findByIsApprovedFalseOrderByDatePostedAsc();
    }

    // --- Admin-only methods ---

    /**
     * Finds a review by its ID (typically for admin moderation).
     */
    public Optional<Review> getReviewById(Long reviewId) {
        return reviewRepository.findById(reviewId);
    }

    /**
     * Approves a specific review.
     */
    @Transactional
    public void approveReview(Long reviewId) {
        Review review = getReviewById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("Review not found."));
        review.setIsApproved(true);
        reviewRepository.save(review);
    }

    /**
     * Deletes a review.
     */
    @Transactional
    public void deleteReview(Long reviewId) {
        reviewRepository.deleteById(reviewId);
    }
}