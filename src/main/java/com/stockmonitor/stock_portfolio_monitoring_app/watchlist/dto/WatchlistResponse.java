package com.stockmonitor.stock_portfolio_monitoring_app.watchlist.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WatchlistResponse {

    private UUID id;
    private UUID userId;
    private String name;
    private int totalItems;
    private Instant createdAt;
    private Instant updatedAt;
    private List<WatchlistItemResponse> items;
}