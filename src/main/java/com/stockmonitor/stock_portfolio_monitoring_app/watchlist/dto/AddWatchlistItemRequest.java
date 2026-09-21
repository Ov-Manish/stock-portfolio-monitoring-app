package com.stockmonitor.stock_portfolio_monitoring_app.watchlist.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddWatchlistItemRequest {

    private UUID stockId;
    private String symbol;
    private String notes;
}