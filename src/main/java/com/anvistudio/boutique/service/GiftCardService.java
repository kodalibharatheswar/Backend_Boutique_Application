package com.anvistudio.boutique.service;

import com.anvistudio.boutique.model.GiftCard;
import com.anvistudio.boutique.model.User;
import com.anvistudio.boutique.repository.GiftCardRepository;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class GiftCardService {

    private final GiftCardRepository giftCardRepository;
    private final UserService userService;

    public GiftCardService(GiftCardRepository giftCardRepository, UserService userService) {
        this.giftCardRepository = giftCardRepository;
        this.userService = userService;
    }

    /**
     * Retrieves all gift cards associated with the authenticated user.
     */
    public List<GiftCard> getGiftCardsByUsername(String username) {
        User user = userService.findUserByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
        return giftCardRepository.findByUserId(user.getId());
    }
}