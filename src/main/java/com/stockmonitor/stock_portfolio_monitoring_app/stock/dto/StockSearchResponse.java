package com.stockmonitor.stock_portfolio_monitoring_app.stock.dto;

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
public class StockSearchResponse {
    private UUID id;
    private String symbol;
    private String exchange;
    private String companyName;
    private String sector;
    private String currency;

    // Real-Time Market Data
    private BigDecimal currentPrice;
    private BigDecimal dayChange;
    private BigDecimal dayChangePercent;
    private BigDecimal dayHigh;
    private BigDecimal dayLow;
    private Long volume;
    private Instant asOf;
}