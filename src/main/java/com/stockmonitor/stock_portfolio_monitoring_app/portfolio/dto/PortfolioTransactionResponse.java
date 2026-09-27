package com.stockmonitor.stock_portfolio_monitoring_app.portfolio.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioTransactionResponse {
    private UUID transactionId;
    private UUID portfolioId;
    private String portfolioName;
    private UUID stockId;
    private String symbol;
    private String companyName;
    private String transactionType; // "BUY" or "SELL"
    private BigDecimal quantity;
    private BigDecimal price;
    private BigDecimal totalAmount;
    private BigDecimal realizedPnL;
    private Instant executedAt;
}