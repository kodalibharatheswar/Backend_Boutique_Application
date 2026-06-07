package com.anvistudio.boutique.service;

import com.anvistudio.boutique.model.CartItem;
import com.anvistudio.boutique.model.Product;
import com.anvistudio.boutique.model.User;
import com.anvistudio.boutique.repository.CartItemRepository;
import com.anvistudio.boutique.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class CartService {

    private final CartItemRepository cartItemRepository;
    private final UserService userService;
    private final ProductRepository productRepository;

    public CartService(CartItemRepository cartItemRepository, UserService userService,
            ProductRepository productRepository) {
        this.cartItemRepository = cartItemRepository;
        this.userService = userService;
        this.productRepository = productRepository;
    }

    /**
     * Adds a product to the cart or increments quantity if it exists.
     * 
     * @param username  The username of the user.
     * @param productId The ID of the product.
     * @param quantity  The amount to add (usually 1).
     */
    @Transactional
    public void addProductToCart(String username, Long productId, int quantity) {
        if (quantity <= 0)
            return;

        User user = userService.findUserByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found."));

        Optional<CartItem> existingItem = cartItemRepository.findByUserIdAndProductId(user.getId(), productId);

        if (existingItem.isPresent()) {
            CartItem item = existingItem.get();
            item.setQuantity(item.getQuantity() + quantity);
            cartItemRepository.save(item);
        } else {
            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> new IllegalArgumentException("Product not found."));

            CartItem newItem = new CartItem();
            newItem.setUser(user);
            newItem.setProduct(product);
            newItem.setQuantity(quantity);
            cartItemRepository.save(newItem);
        }
    }

    /**
     * Updates the quantity of a specific item in the cart.
     */
    @Transactional
    public void updateItemQuantity(Long itemId, int quantity) {
        CartItem item = cartItemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Cart item not found."));

        if (quantity <= 0) {
            cartItemRepository.delete(item); // Delete if quantity is zero
        } else {
            item.setQuantity(quantity);
            cartItemRepository.save(item);
        }
    }

    /**
     * Removes an item completely from the cart.
     */
    @Transactional
    public void removeItem(Long itemId) {
        cartItemRepository.deleteById(itemId);
    }

    /**
     * Retrieves all cart items for a specific user.
     */
    public List<CartItem> getCartItems(Long userId) {
        return cartItemRepository.findByUserId(userId);
    }

    /**
     * Calculates the total price for all items in the cart.
     * This method automatically uses the updated CartItem.getTotalPrice()
     * calculation.
     */
    public double getCartTotal(Long userId) {
        return getCartItems(userId).stream()
                .mapToDouble(CartItem::getTotalPrice)
                .sum();
    }

    /**
     * NEW: Clears all cart items for a specific user ID.
     */
    @Transactional
    public void clearCart(Long userId) {
        List<CartItem> items = getCartItems(userId);
        cartItemRepository.deleteAll(items);
    }
}