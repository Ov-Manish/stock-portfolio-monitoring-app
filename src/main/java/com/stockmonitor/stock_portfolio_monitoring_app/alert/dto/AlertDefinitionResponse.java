package com.stockmonitor.stock_portfolio_monitoring_app.alert.dto;

import com.stockmonitor.stock_portfolio_monitoring_app.constants.AlertDirection;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlertDefinitionResponse {

    private UUID alertId;
    private UUID userId;
    private UUID stockId;
    private String symbol;
    private String exchange;
    private AlertDirection direction;
    private BigDecimal thresholdValue;
    private BigDecimal referencePrice;
    private boolean isActive;
    private Integer cooldownMinutes;
    private Instant lastTriggeredAt;
    private Instant createdAt;
}
