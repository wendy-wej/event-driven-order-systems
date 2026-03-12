package com.orderSystem.order_system.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
public class CreateOrderRequest {

    @NotBlank
    private String symbol;

    @NotBlank
    private String side;

    @NotNull
    @Min(1)
    private Integer quantity;

    @NotNull
    @Min(1)
    private BigDecimal price;
}

