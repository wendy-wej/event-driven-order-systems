package com.orderSystem.order_system.kafka;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;


@Component
@RequiredArgsConstructor
public class OrderProducer {

    private final KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate;
    private static final String TOPIC = "order-created";
    private static final Logger logger = LoggerFactory.getLogger(OrderProducer.class);


    public void publishMessage(OrderCreatedEvent message) {
        logger.info("Kakfa event for order {} published", message.getOrderId());
        kafkaTemplate.send(TOPIC, message);
    }
}
