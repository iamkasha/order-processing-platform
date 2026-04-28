package com.kasha.orderprocessing.service;

import com.kasha.orderprocessing.dto.request.CreateOrderRequest;
import com.kasha.orderprocessing.dto.request.OrderItemRequest;
import com.kasha.orderprocessing.dto.response.OrderResponse;
import com.kasha.orderprocessing.entity.Order;
import com.kasha.orderprocessing.entity.Product;
import com.kasha.orderprocessing.entity.User;
import com.kasha.orderprocessing.enums.OrderStatus;
import com.kasha.orderprocessing.enums.Role;
import com.kasha.orderprocessing.exception.InsufficientStockException;
import com.kasha.orderprocessing.exception.OrderNotFoundException;
import com.kasha.orderprocessing.kafka.OrderEventProducer;
import com.kasha.orderprocessing.repository.OrderRepository;
import com.kasha.orderprocessing.repository.ProductRepository;
import com.kasha.orderprocessing.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock OrderRepository orderRepository;
    @Mock ProductRepository productRepository;
    @Mock UserRepository userRepository;
    @Mock OrderEventProducer eventProducer;
    @Mock ProductService productService;

    @InjectMocks OrderService orderService;

    private User user;
    private Product product;

    @BeforeEach
    void setUp() {
        user = User.builder().id(1L).name("Alice").email("alice@example.com")
                .password("encoded").role(Role.USER).createdAt(LocalDateTime.now()).build();

        product = Product.builder().id(1L).name("Laptop").price(new BigDecimal("999.99"))
                .stockQuantity(10).createdAt(LocalDateTime.now()).build();
    }

    @Test
    void createOrder_validRequest_returnsOrderResponse() {
        OrderItemRequest itemRequest = new OrderItemRequest();
        itemRequest.setProductId(1L);
        itemRequest.setQuantity(2);

        CreateOrderRequest request = new CreateOrderRequest();
        request.setItems(List.of(itemRequest));

        Order savedOrder = Order.builder().id(1L).user(user).status(OrderStatus.PENDING)
                .totalAmount(new BigDecimal("1999.98")).items(List.of()).createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now()).build();

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(any())).thenReturn(product);
        when(orderRepository.save(any())).thenReturn(savedOrder);

        OrderResponse response = orderService.createOrder("alice@example.com", request);

        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals(OrderStatus.PENDING, response.getStatus());
        verify(eventProducer).publish(any(), eq("ORDER_CREATED"));
    }

    @Test
    void createOrder_insufficientStock_throwsInsufficientStockException() {
        product.setStockQuantity(1);

        OrderItemRequest itemRequest = new OrderItemRequest();
        itemRequest.setProductId(1L);
        itemRequest.setQuantity(5);

        CreateOrderRequest request = new CreateOrderRequest();
        request.setItems(List.of(itemRequest));

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        assertThrows(InsufficientStockException.class,
                () -> orderService.createOrder("alice@example.com", request));
        verify(orderRepository, never()).save(any());
    }

    @Test
    void getById_existingOrder_returnsOrderResponse() {
        Order order = Order.builder().id(1L).user(user).status(OrderStatus.PENDING)
                .totalAmount(BigDecimal.TEN).items(List.of()).createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now()).build();

        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        OrderResponse response = orderService.getById(1L);

        assertEquals(1L, response.getId());
        assertEquals(OrderStatus.PENDING, response.getStatus());
    }

    @Test
    void getById_nonExistingOrder_throwsOrderNotFoundException() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(OrderNotFoundException.class, () -> orderService.getById(99L));
    }
}
