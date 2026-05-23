package com.anvistudio.boutique.service;

import com.anvistudio.boutique.model.Address;
import com.anvistudio.boutique.model.User;
import com.anvistudio.boutique.repository.AddressRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional; // NEW IMPORT

@Service
public class AddressService {

    private final AddressRepository addressRepository;
    private final UserService userService;

    public AddressService(AddressRepository addressRepository, UserService userService) {
        this.addressRepository = addressRepository;
        this.userService = userService;
    }

    /**
     * Retrieves all saved addresses for the authenticated user.
     */
    public List<Address> getAddressesByUsername(String username) {
        User user = userService.findUserByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
        return addressRepository.findByUserId(user.getId());
    }


    /**
     * NEW: Retrieves a single address by its ID.
     */
    public Optional<Address> getAddressById(Long addressId) {
        return addressRepository.findById(addressId);
    }

    /**
     * Saves a new address or updates an existing one.
     */
    @Transactional
    public Address saveAddress(String username, Address address) {
        User user = userService.findUserByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));

        address.setUser(user);

        // Simple logic to ensure only one address can be default (if setting a new default)
        if (Boolean.TRUE.equals(address.getIsDefault())) {
            // Find current default and unset it
            getAddressesByUsername(username).stream()
                    .filter(Address::getIsDefault)
                    .forEach(addr -> {
                        addr.setIsDefault(false);
                        addressRepository.save(addr);
                    });
        }

        return addressRepository.save(address);
    }

    /**
     * Deletes a saved address by ID.
     */
    @Transactional
    public void deleteAddress(Long addressId) {
        addressRepository.deleteById(addressId);
    }
}