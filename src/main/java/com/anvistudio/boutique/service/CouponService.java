package com.anvistudio.boutique.service;

import com.anvistudio.boutique.model.Coupon;
import com.anvistudio.boutique.repository.CouponRepository;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class CouponService {

    private final CouponRepository couponRepository;

    public CouponService(CouponRepository couponRepository) {
        this.couponRepository = couponRepository;
    }

    /**
     * Retrieves all active coupons for display to the user.
     */
    public List<Coupon> getAllActiveCoupons() {
        return couponRepository.findByIsActiveTrueOrderByExpirationDateAsc();
    }
}