package com.stockmonitor.stock_portfolio_monitoring_app.watchlist.dto;

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
public class WatchlistItemResponse {

    private UUID itemId;
    private UUID stockId;
    private String symbol;
    private String companyName;
    private String exchange;

    // Real-time market data
    private BigDecimal currentPrice;
    private Instant asOf;

    // Watchlist metadata
    private String notes;
    private Instant addedAt;

    // Alert indicator
    private boolean hasActiveAlert;
    private BigDecimal alertTargetPrice;
}