package com.anvistudio.boutique.service;

import com.anvistudio.boutique.model.Product;
import com.anvistudio.boutique.model.User;
import com.anvistudio.boutique.model.Wishlist;
import com.anvistudio.boutique.repository.ProductRepository;
import com.anvistudio.boutique.repository.UserRepository;
import com.anvistudio.boutique.repository.WishlistRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class WishlistService {

    private final WishlistRepository wishlistRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;

    public WishlistService(WishlistRepository wishlistRepository, UserRepository userRepository, ProductRepository productRepository) {
        this.wishlistRepository = wishlistRepository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
    }

    /**
     * Adds a product to a user's wishlist, ensuring no duplicates.
     * @param username The username of the currently logged-in user.
     * @param productId The ID of the product to add.
     */
    @Transactional
    public void addToWishlist(String username, Long productId) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));

        // Prevent duplicate entries
        if (wishlistRepository.findByUserIdAndProductId(user.getId(), productId).isEmpty()) {
            Wishlist item = new Wishlist();
            item.setUser(user);
            item.setProduct(product);
            wishlistRepository.save(item);
        }
    }

    /**
     * Removes a product from a user's wishlist.
     */
    @Transactional
    public void removeFromWishlist(Long userId, Long productId) {
        wishlistRepository.deleteByUserIdAndProductId(userId, productId);
    }

    /**
     * Retrieves all wishlist items (and associated products) for a user.
     */
    public List<Wishlist> getWishlistItems(Long userId) {
        return wishlistRepository.findByUserId(userId);
    }
}