package com.orderSystem.order_system.service;

import com.orderSystem.order_system.dto.CreateOrderRequest;
import com.orderSystem.order_system.kafka.OrderCreatedEvent;
import com.orderSystem.order_system.kafka.OrderProducer;
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
    private final OrderProducer orderProducer;

    @Transactional
    public Order createOrder(CreateOrderRequest request) {
        Order order = new Order();
        order.setSymbol(request.getSymbol());
        order.setSide(request.getSide());
        order.setQuantity(request.getQuantity());
        order.setPrice(request.getPrice());

        Order savedOrder = orderRepository.save(order);

        OrderCreatedEvent event = new OrderCreatedEvent(
                savedOrder.getId(),
                savedOrder.getSymbol(),
                savedOrder.getSide(),
                savedOrder.getQuantity(),
                savedOrder.getPrice()
        );
        orderProducer.publishMessage(event);
        return savedOrder;
    }

    public Order getOrderbyId(Long id){
        return orderRepository.findById(id).orElseThrow(
            () -> new RuntimeException("Order not found")
        );
    }

    public List<Order> getAllOrders(){
        return orderRepository.findAll();
    }

    @Transactional
    public void updateOrder(Order order){
        orderRepository.save(order);
    }
}
