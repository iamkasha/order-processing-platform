package com.kasha.orderprocessing.repository;

import com.kasha.orderprocessing.entity.Order;
import com.kasha.orderprocessing.enums.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByUserId(Long userId);
    List<Order> findByStatus(OrderStatus status);
}
