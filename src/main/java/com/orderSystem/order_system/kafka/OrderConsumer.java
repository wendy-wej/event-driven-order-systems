package com.orderSystem.order_system.kafka;

import com.orderSystem.order_system.model.Order;
import com.orderSystem.order_system.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderConsumer {

    private final OrderService orderService;
    private static final Logger logger = LoggerFactory.getLogger(OrderConsumer.class);

    @KafkaListener(topics = "order-created")
    public void consume(OrderCreatedEvent event) throws InterruptedException {
        Long orderId = event.getOrderId();

        Order retreivedOrder = orderService.getOrderbyId(orderId);
        retreivedOrder.setStatus("PROCESSING");
        logger.info("Processing order: {}" , retreivedOrder.getId());
        orderService.updateOrder(retreivedOrder);

        Thread.sleep(10000);
        retreivedOrder.setStatus("EXECUTED");
        orderService.updateOrder(retreivedOrder);
        logger.info("Executed order: {}", retreivedOrder.getId());
    }

}
