package com.orderSystem.order_system.kafka;

import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;


@Component
@RequiredArgsConstructor
public class OrderProducer {

    private final KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate;
    private static final String TOPIC = "order-created";

    public void publishMessage(OrderCreatedEvent message) {
        kafkaTemplate.send(TOPIC, message);
    }
}
