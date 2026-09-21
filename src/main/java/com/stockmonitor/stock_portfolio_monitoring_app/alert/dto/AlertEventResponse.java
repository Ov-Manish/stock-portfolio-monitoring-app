package com.stockmonitor.stock_portfolio_monitoring_app.alert.dto;


import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlertEventResponse {
    private UUID eventId;
    private UUID alertDefinitionId;
    private BigDecimal triggeredPrice;
    private BigDecimal percentageMove;
    private Instant triggeredAt;
    private UUID eventSourceId;

}
