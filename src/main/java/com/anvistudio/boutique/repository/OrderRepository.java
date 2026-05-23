package com.anvistudio.boutique.repository;

import com.anvistudio.boutique.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {
    /**
     * Finds all orders placed by a specific user.
     */
    List<Order> findByUserIdOrderByOrderDateDesc(Long userId);
}