package com.stockmonitor.stock_portfolio_monitoring_app.portfolio.dto;

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
public class HoldingResponse {
    private UUID holdingId;
    private UUID stockId;
    private String symbol;
    private String exchange;
    private String companyName;
    private BigDecimal quantity;
    private BigDecimal avgBuyPrice;
    private BigDecimal investedValue;
    private BigDecimal currentPrice;
    private BigDecimal currentValue;
    private BigDecimal unrealizedPnL;
    private BigDecimal returnPercentage;
}