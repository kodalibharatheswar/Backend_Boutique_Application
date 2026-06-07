package com.anvistudio.boutique.service;

import com.anvistudio.boutique.model.CartItem;
import com.anvistudio.boutique.model.Order;
import com.anvistudio.boutique.model.OrderTracking;
import com.anvistudio.boutique.model.User;
import com.anvistudio.boutique.repository.OrderRepository;
import com.anvistudio.boutique.repository.OrderTrackingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class OrderService {

    private final OrderRepository orderRepository; // Changed from public to private
    private final UserService userService;
    private final OrderTrackingRepository orderTrackingRepository;

    // Standard 7-day return window in milliseconds
    private static final long RETURN_WINDOW_MILLIS = TimeUnit.DAYS.toMillis(7);

    public OrderService(OrderRepository orderRepository, UserService userService,
            OrderTrackingRepository orderTrackingRepository) {
        this.orderRepository = orderRepository;
        this.userService = userService;
        this.orderTrackingRepository = orderTrackingRepository;
    }

    /**
     * NEW: Retrieves all orders regardless of user (for Admin dashboard).
     */
    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }

    /**
     * Retrieves all orders for the authenticated user.
     */
    public List<Order> getOrdersByUsername(String username) {
        User user = userService.findUserByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
        return orderRepository.findByUserIdOrderByOrderDateDesc(user.getId());
    }

    /**
     * Retrieves tracking history for an order.
     */
    public List<OrderTracking> getTrackingHistory(Long orderId) {
        return orderTrackingRepository.findByOrderIdOrderByTimestampAsc(orderId);
    }

    /**
     * Retrieves a single order by its ID.
     */
    public Optional<Order> getOrderById(Long orderId) {
        return orderRepository.findById(orderId);
    }

    /**
     * NEW: Utility method to save an Order (used by AdminController for status
     * updates).
     */
    @Transactional
    public Order saveOrder(Order order) {
        Order savedOrder = orderRepository.save(order);
        // Log the status update
        OrderTracking tracking = new OrderTracking(savedOrder, savedOrder.getStatus(), "Processing Center",
                "Order status updated to " + savedOrder.getStatus());
        orderTrackingRepository.save(tracking);
        return savedOrder;
    }

    /**
     * Handles immediate order cancellation logic (for PENDING/PROCESSING orders).
     */
    @Transactional
    public void cancelOrder(Long orderId) {
        Order order = getOrderById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

        if (order.getStatus() != Order.OrderStatus.PENDING && order.getStatus() != Order.OrderStatus.PROCESSING) {
            throw new IllegalStateException("Order status is " + order.getStatus() + ". Cannot be cancelled.");
        }

        order.setStatus(Order.OrderStatus.CANCELLED);
        Order savedOrder = orderRepository.save(order);

        OrderTracking tracking = new OrderTracking(savedOrder, Order.OrderStatus.CANCELLED, "",
                "Order cancelled by user.");
        orderTrackingRepository.save(tracking);

        System.out.println(
                "LOG: Order " + orderId + " cancelled. Initiating refund for amount: " + order.getTotalAmount());
        // TODO: Trigger Refund Process (Stripe API call would happen here)
    }

    /**
     * NEW: Handles return request logic (for DELIVERED orders).
     */
    @Transactional
    public void returnOrder(Long orderId) {
        Order order = getOrderById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

        if (order.getStatus() != Order.OrderStatus.DELIVERED) {
            throw new IllegalStateException("Order status must be DELIVERED to request a return.");
        }

        // 1. Check Return Window (Simulated 7-day policy)
        long timeSinceOrder = new Date().getTime() - order.getOrderDate().getTime();

        if (timeSinceOrder > RETURN_WINDOW_MILLIS) {
            throw new IllegalStateException("Return window has closed (7 days allowed).");
        }

        // 2. Set Status to Return Requested
        order.setStatus(Order.OrderStatus.RETURN_REQUESTED);
        Order savedOrder = orderRepository.save(order);

        OrderTracking tracking = new OrderTracking(savedOrder, Order.OrderStatus.RETURN_REQUESTED, "",
                "Return requested by customer.");
        orderTrackingRepository.save(tracking);

        System.out.println("LOG: Order " + orderId + " return requested. Awaiting admin approval.");
    }

    @Transactional
    public Order createOrderFromCart(Long userId, List<CartItem> cartItems, Long addressId) {
        if (cartItems.isEmpty()) {
            throw new IllegalStateException("Cannot create an order from an empty cart.");
        }

        User user = cartItems.get(0).getUser();

        BigDecimal totalAmount = cartItems.stream()
                .map(item -> BigDecimal.valueOf(item.getTotalPrice()))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, BigDecimal.ROUND_HALF_UP);

        // Format: <QTY>x <NAME> [ID:<ID>] [IMG:<IMG_URL>] (₹<PRICE>); ...
        String orderItemsSnapshot = cartItems.stream()
                .map(item -> String.format("%dx %s [ID:%d] [IMG:%s] (₹%.2f)",
                        item.getQuantity(),
                        item.getProduct().getName(),
                        item.getProduct().getId(),
                        item.getProduct().getImageUrl() != null ? item.getProduct().getImageUrl() : "",
                        item.getTotalPrice()))
                .collect(Collectors.joining("; "));

        // For demo purposes, we can assume addressId comes from frontend.
        // In a real app we'd fetch the Address entity.
        String shippingAddressSnapshot = "Shipping Address ID: " + addressId;

        Order newOrder = new Order();
        newOrder.setUser(user);
        newOrder.setOrderDate(new Date());
        newOrder.setTotalAmount(totalAmount);
        newOrder.setStatus(Order.OrderStatus.PENDING);
        newOrder.setShippingAddressSnapshot(shippingAddressSnapshot);
        newOrder.setOrderItemsSnapshot(orderItemsSnapshot);

        Order savedOrder = orderRepository.saveAndFlush(newOrder);

        OrderTracking tracking = new OrderTracking(savedOrder, Order.OrderStatus.PENDING, "",
                "Order placed successfully.");
        orderTrackingRepository.saveAndFlush(tracking);

        // Auto-move to processing for demo
        savedOrder.setStatus(Order.OrderStatus.PROCESSING);
        savedOrder = orderRepository.saveAndFlush(savedOrder);

        OrderTracking processingTracking = new OrderTracking(savedOrder, Order.OrderStatus.PROCESSING,
                "Fulfillment Center", "Order is being processed.");
        orderTrackingRepository.saveAndFlush(processingTracking);

        return savedOrder;
    }

    public void populateDummyOrders(User user) {
        // Mock implementation unchanged
        if (orderRepository.findByUserIdOrderByOrderDateDesc(user.getId()).isEmpty()) {
            // In a real application, this logic would handle actual placed orders.
        }
    }
}