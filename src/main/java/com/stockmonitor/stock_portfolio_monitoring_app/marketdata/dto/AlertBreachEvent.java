package com.stockmonitor.stock_portfolio_monitoring_app.marketdata.dto;

import com.stockmonitor.stock_portfolio_monitoring_app.constants.AlertDirection;
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
public class AlertBreachEvent {
    private UUID alertEventId;
    private UUID alertId;
    private UUID userId;
    private String userEmail;
    private String symbol;
    private AlertDirection direction;
    private BigDecimal thresholdValue;
    private BigDecimal triggerPrice;
    private Instant triggeredAt;
}