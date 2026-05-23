package com.anvistudio.boutique.repository;

import com.anvistudio.boutique.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query; // NEW
import org.springframework.data.repository.query.Param; // NEW
import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * Finds products by category (useful for filtering the customer view).
     */
    List<Product> findByCategory(String category);

    /**
     * Finds the latest products, useful for the New Arrivals section.
     * This method now resolves correctly against the updated Product entity.
     */
    List<Product> findTop8ByOrderByDateCreatedDesc();

    /**
     * NEW: Finds products matching a keyword across name, category, SKU, and color.
     */
    @Query("SELECT p FROM Product p WHERE " +
            "LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(p.category) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(p.sku) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(p.productColor) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Product> searchByKeyword(@Param("keyword") String keyword); //
}