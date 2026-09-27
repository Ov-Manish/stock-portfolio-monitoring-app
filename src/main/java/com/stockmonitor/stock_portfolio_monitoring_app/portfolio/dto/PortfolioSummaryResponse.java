package com.stockmonitor.stock_portfolio_monitoring_app.portfolio.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioSummaryResponse {
    private UUID portfolioId;
    private String portfolioName;
    private UUID userId;
    private BigDecimal totalInvested;
    private BigDecimal totalCurrentValue;
    private BigDecimal totalUnrealizedPnL;
    private BigDecimal totalReturnPercentage;
    private List<HoldingResponse> holdings;
}