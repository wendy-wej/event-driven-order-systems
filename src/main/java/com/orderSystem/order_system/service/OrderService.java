package com.orderSystem.order_system.service;

import com.orderSystem.order_system.model.Order;
import com.orderSystem.order_system.repository.OrderRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService {
    private final OrderRepository orderRepository;

    @Transactional
    public Order createOrder(Order order) {
        return orderRepository.save(order);
    }

    public Order getOrderbyId(Long id){
        return orderRepository.findById(id).orElseThrow(
            () -> new RuntimeException("Order not found")
        );
    }

    public List<Order> getAllOrders(){
        return orderRepository.findAll();
    }
}
