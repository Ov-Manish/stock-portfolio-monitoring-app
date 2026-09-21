package com.stockmonitor.stock_portfolio_monitoring_app.marketdata.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockHistoryResponse {
    private String symbol;
    private String companyName;
    private String range;
    private int totalCandles;
    private List<DailyPriceResponse> data;
}