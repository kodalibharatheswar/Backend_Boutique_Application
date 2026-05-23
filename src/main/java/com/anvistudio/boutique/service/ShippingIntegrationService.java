package com.anvistudio.boutique.service;

import com.anvistudio.boutique.model.Order;
import com.anvistudio.boutique.model.OrderTracking;
import com.anvistudio.boutique.repository.OrderRepository;
import com.anvistudio.boutique.repository.OrderTrackingRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class ShippingIntegrationService {

    private final OrderRepository orderRepository;
    private final OrderTrackingRepository orderTrackingRepository;

    public ShippingIntegrationService(OrderRepository orderRepository, OrderTrackingRepository orderTrackingRepository) {
        this.orderRepository = orderRepository;
        this.orderTrackingRepository = orderTrackingRepository;
    }

    /**
     * Dummy method to simulate sending order details to a shipping partner like Shiprocket.
     */
    public String createShippingLabel(Order order) {
        System.out.println("--- DUMMY SHIPPING API INTEGRATION ---");
        System.out.println("Sending order details to Shipping Partner (e.g. Shiprocket/Delhivery)...");
        System.out.println("Order ID: " + order.getId());
        System.out.println("Customer: " + order.getUser().getUsername());
        
        // Simulate an AWB (Airway Bill) number generation
        String awbNumber = "AWB" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        System.out.println("Received AWB Number: " + awbNumber);
        
        // Log a tracking event for shipping partner integration
        OrderTracking tracking = new OrderTracking(order, Order.OrderStatus.PACKED, "Fulfillment Center", 
            "Package is ready for dispatch. AWB Number assigned: " + awbNumber);
        orderTrackingRepository.save(tracking);

        return awbNumber;
    }
    
    /**
     * Dummy method to simulate a webhook call from the shipping partner updating the status.
     */
    public void simulateWebhookStatusUpdate(Order order, Order.OrderStatus newStatus, String location, String description) {
        System.out.println("--- DUMMY WEBHOOK RECEIVED ---");
        System.out.println("Updating status for Order ID " + order.getId() + " to " + newStatus);
        
        order.setStatus(newStatus);
        orderRepository.save(order);
        
        OrderTracking tracking = new OrderTracking(order, newStatus, location, description);
        orderTrackingRepository.save(tracking);
    }
}
