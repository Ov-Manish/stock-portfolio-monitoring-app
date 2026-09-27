package com.stockmonitor.stock_portfolio_monitoring_app.stock.controller;

import com.stockmonitor.stock_portfolio_monitoring_app.marketdata.dto.MarketTickEvent;
import com.stockmonitor.stock_portfolio_monitoring_app.stock.dto.StockSearchResponse;
import com.stockmonitor.stock_portfolio_monitoring_app.stock.entity.Stock;
import com.stockmonitor.stock_portfolio_monitoring_app.stock.repository.StockRepository;
import com.stockmonitor.stock_portfolio_monitoring_app.stock.service.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import com.stockmonitor.stock_portfolio_monitoring_app.marketdata.dto.StockHistoryResponse;
import com.stockmonitor.stock_portfolio_monitoring_app.marketdata.service.PriceHistoryService;
@RestController
@RequestMapping("/api/v1/stocks")
@RequiredArgsConstructor
public class StockController {

    private final StockRepository stockRepository;
    private  final StockService stockService;
    private final PriceHistoryService priceHistoryService;

    @GetMapping
    public ResponseEntity<List<StockSearchResponse>> getAllActiveStocks() {
        List<StockSearchResponse> stocks = stockService.getAllActiveStocksWithPrices();
        return ResponseEntity.ok(stocks);
    }

    @GetMapping("/search")
    public ResponseEntity<List<StockSearchResponse>> searchStocks(@RequestParam String symbol) {
        List<StockSearchResponse> results = stockService.searchStocks(symbol);
        return ResponseEntity.ok(results);
    }

    @PostMapping
    public ResponseEntity<Stock> createStock(@RequestBody Stock stock) {
        Stock saved = stockRepository.save(stock);
        return new ResponseEntity<>(saved, HttpStatus.CREATED);
    }

    @GetMapping("/{symbol}/history")
    public ResponseEntity<StockHistoryResponse> getStockHistory(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "1mo") String range) {
        StockHistoryResponse response = priceHistoryService.getStockHistory(symbol, range);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{symbol}/overview")
    public ResponseEntity<MarketTickEvent> getStockOverview(@PathVariable String symbol){
        return stockService.getStockOverview(symbol)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

}