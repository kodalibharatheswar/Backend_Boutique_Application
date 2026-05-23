package com.anvistudio.boutique.repository;

import com.anvistudio.boutique.model.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CouponRepository extends JpaRepository<Coupon, Long> {
    /**
     * Finds all currently active coupons sorted by expiration date.
     */
    List<Coupon> findByIsActiveTrueOrderByExpirationDateAsc();
}