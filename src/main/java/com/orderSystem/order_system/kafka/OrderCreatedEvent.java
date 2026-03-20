package com.orderSystem.order_system.kafka;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderCreatedEvent {
    private Long orderId;
    private String symbol;
    private String side;
    private Integer quantity;
    private BigDecimal price;
}