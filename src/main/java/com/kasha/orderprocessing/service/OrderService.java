package com.kasha.orderprocessing.service;

import com.kasha.orderprocessing.dto.request.CreateOrderRequest;
import com.kasha.orderprocessing.dto.request.UpdateOrderStatusRequest;
import com.kasha.orderprocessing.dto.response.OrderItemResponse;
import com.kasha.orderprocessing.dto.response.OrderResponse;
import com.kasha.orderprocessing.entity.Order;
import com.kasha.orderprocessing.entity.OrderItem;
import com.kasha.orderprocessing.entity.Product;
import com.kasha.orderprocessing.entity.User;
import com.kasha.orderprocessing.enums.OrderStatus;
import com.kasha.orderprocessing.exception.InsufficientStockException;
import com.kasha.orderprocessing.exception.OrderNotFoundException;
import com.kasha.orderprocessing.exception.ProductNotFoundException;
import com.kasha.orderprocessing.kafka.OrderEventProducer;
import com.kasha.orderprocessing.repository.OrderRepository;
import com.kasha.orderprocessing.repository.ProductRepository;
import com.kasha.orderprocessing.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final OrderEventProducer eventProducer;
    private final ProductService productService;

    @Transactional
    public OrderResponse createOrder(String userEmail, CreateOrderRequest request) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + userEmail));

        Order order = Order.builder()
                .user(user)
                .status(OrderStatus.PENDING)
                .totalAmount(BigDecimal.ZERO)
                .build();

        BigDecimal total = BigDecimal.ZERO;

        for (var itemRequest : request.getItems()) {
            Product product = productRepository.findById(itemRequest.getProductId())
                    .orElseThrow(() -> new ProductNotFoundException(itemRequest.getProductId()));

            if (product.getStockQuantity() < itemRequest.getQuantity()) {
                throw new InsufficientStockException(product.getName(), product.getStockQuantity());
            }

            product.setStockQuantity(product.getStockQuantity() - itemRequest.getQuantity());
            productRepository.save(product);
            productService.evictCache(product.getId());

            OrderItem item = OrderItem.builder()
                    .product(product)
                    .quantity(itemRequest.getQuantity())
                    .unitPrice(product.getPrice())
                    .build();

            order.addItem(item);
            total = total.add(product.getPrice().multiply(BigDecimal.valueOf(itemRequest.getQuantity())));
        }

        order.setTotalAmount(total);
        Order saved = orderRepository.save(order);
        eventProducer.publish(saved, "ORDER_CREATED");
        return toResponse(saved);
    }

    public OrderResponse getById(Long id) {
        return toResponse(orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id)));
    }

    public List<OrderResponse> getByUser(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + userEmail));
        return orderRepository.findByUserId(user.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public OrderResponse updateStatus(Long id, UpdateOrderStatusRequest request) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
        order.setStatus(request.getStatus());
        Order saved = orderRepository.save(order);
        eventProducer.publish(saved, "ORDER_STATUS_UPDATED");
        return toResponse(saved);
    }

    private OrderResponse toResponse(Order order) {
        List<OrderItemResponse> items = order.getItems().stream()
                .map(item -> OrderItemResponse.builder()
                        .productId(item.getProduct().getId())
                        .productName(item.getProduct().getName())
                        .quantity(item.getQuantity())
                        .unitPrice(item.getUnitPrice())
                        .subtotal(item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                        .build())
                .toList();

        return OrderResponse.builder()
                .id(order.getId())
                .userId(order.getUser().getId())
                .status(order.getStatus())
                .items(items)
                .totalAmount(order.getTotalAmount())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }
}
