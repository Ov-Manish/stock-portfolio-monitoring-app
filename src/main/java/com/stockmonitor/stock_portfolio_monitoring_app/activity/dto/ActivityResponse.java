package com.stockmonitor.stock_portfolio_monitoring_app.activity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityResponse {
    private UUID id;
    private UUID userId;
    private String type;
    private String message;
    private String metadata;
    private Instant createdAt;
}
