package com.stockmonitor.stock_portfolio_monitoring_app.watchlist.controller;

import com.stockmonitor.stock_portfolio_monitoring_app.watchlist.dto.AddWatchlistItemRequest;
import com.stockmonitor.stock_portfolio_monitoring_app.watchlist.dto.CreateWatchlistRequest;
import com.stockmonitor.stock_portfolio_monitoring_app.watchlist.dto.WatchlistResponse;
import com.stockmonitor.stock_portfolio_monitoring_app.watchlist.service.WatchlistService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/watchlists")
@RequiredArgsConstructor
public class WatchlistController {

    private final WatchlistService watchlistService;

    @PostMapping
    public ResponseEntity<WatchlistResponse> createWatchlist(@Valid @RequestBody CreateWatchlistRequest request) {
        WatchlistResponse response = watchlistService.createWatchList(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<WatchlistResponse>> getUserWatchlists(@PathVariable UUID userId) {
        List<WatchlistResponse> response = watchlistService.getUserWatchList(userId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<WatchlistResponse> getWatchlistDetails(@PathVariable UUID id) {
        WatchlistResponse response = watchlistService.getWatchListDetails(id);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/stocks")
    public ResponseEntity<WatchlistResponse> addStockToWatchlist(
            @PathVariable UUID id,
            @Valid @RequestBody AddWatchlistItemRequest request) {
        WatchlistResponse response = watchlistService.addStockToWatchList(id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}/stocks/{stockId}")
    public ResponseEntity<String> removeStockFromWatchlist(
            @PathVariable UUID id,
            @PathVariable UUID stockId) {
        watchlistService.removeStockFromWatchlist(id, stockId);
        return ResponseEntity.ok("Stock removed from watchlist successfully");
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteWatchlist(@PathVariable UUID id) {
        watchlistService.deleteWatchlist(id);
        return ResponseEntity.ok("Watchlist deleted successfully");
    }
}