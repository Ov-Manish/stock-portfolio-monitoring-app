package com.stockmonitor.stock_portfolio_monitoring_app.portfolio.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SellStockRequest {

    @NotNull(message = "User ID cannot be null")
    private UUID userId;

    private UUID portfolioId;

    @NotNull(message = "Stock ID cannot be null")
    private UUID stockId;

    @NotNull(message = "Quantity cannot be null")
    @DecimalMin(value = "0.0001", message = "Quantity must be greater than 0")
    private BigDecimal quantity;

    @DecimalMin(value = "0.0001" , message = "Sell Price must be greater than 0")
    private BigDecimal sellPrice;
}