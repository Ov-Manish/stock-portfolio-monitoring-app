package com.stockmonitor.stock_portfolio_monitoring_app.portfolio.dto;

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
public class PortfolioResponse {
    private UUID id;
    private UUID userId;
    private String name;
    private int holdingsCount;
    private Instant createdAt;
}
