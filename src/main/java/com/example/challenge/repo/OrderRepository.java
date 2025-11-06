package com.example.challenge.repo;

import com.example.challenge.domain.Order;
import com.example.challenge.domain.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, String> {
  List<Order> findByStatus(OrderStatus status);
}
