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
    private final OrderProducer orderProducer;

    @KafkaListener(topics = "order-created")
    public void consume(OrderCreatedEvent event) throws InterruptedException {
        Long orderId = event.getOrderId();

        Order retreivedOrder = orderService.getOrderbyId(orderId);
        retreivedOrder.setStatus("PROCESSING");
        logger.info("Processing order: {}" , retreivedOrder.getId());
        orderService.updateOrder(retreivedOrder);

        Thread.sleep(10000);
        boolean simulateError = Math.random() < 0.2;
        if (simulateError) {
            Integer retryCount = retreivedOrder.getRetryCount();
            if (retryCount < 3){
                retreivedOrder.setStatus("RETRY");
                retreivedOrder.setRetryCount(retryCount + 1);
                orderService.updateOrder(retreivedOrder);
                orderProducer.publishMessage(event);
                logger.warn("Retrying order {}. This is attempt: {}", retreivedOrder.getId(), retreivedOrder.getRetryCount());
            }else{
                retreivedOrder.setStatus("FAILED");
                orderService.updateOrder(retreivedOrder);
                logger.error("Failed to process order: {} after 3 attempts", retreivedOrder.getId());
            }
        } else {
            retreivedOrder.setStatus("EXECUTED");
            orderService.updateOrder(retreivedOrder);
            logger.info("Executed order: {}", retreivedOrder.getId());
        }
    }

}
