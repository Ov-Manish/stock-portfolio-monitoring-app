package com.stockmonitor.stock_portfolio_monitoring_app.portfolio.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SellStockResponse {

    private UUID transactionId;
    private UUID portfolioId;
    private String portfolioName;
    private UUID stockId;
    private String symbol;
    private String companyName;

    private BigDecimal soldQuantity;
    private BigDecimal sellPrice;
    private BigDecimal avgBuyPrice;

    private BigDecimal totalInvested;       // Cost of sold shares: (avgBuyPrice * soldQuantity)
    private BigDecimal totalSaleProceeds;   // Cash received: (sellPrice * soldQuantity)
    private BigDecimal realizedPnL;         // Net Profit or Loss: (totalSaleProceeds - totalInvested)
    private BigDecimal realizedPnLPercent;  // Return %: ((sellPrice - avgBuyPrice) / avgBuyPrice) * 100

    private BigDecimal remainingQuantity;   // Shares still owned after this sale
    private String pnlStatus;               // "PROFIT", "LOSS", or "BREAK_EVEN"
    private Instant executedAt;
}