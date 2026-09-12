package com.stockmonitor.stock_portfolio_monitoring_app.stock.controller;

import com.stockmonitor.stock_portfolio_monitoring_app.constants.StockStatus;
import com.stockmonitor.stock_portfolio_monitoring_app.stock.entity.Stock;
import com.stockmonitor.stock_portfolio_monitoring_app.stock.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/stocks")
@RequiredArgsConstructor
public class StockController {

    private final StockRepository stockRepository;

    @GetMapping
    public ResponseEntity<List<Stock>> getAllActiveStocks() {
        List<Stock> stocks = stockRepository.findByStatus(StockStatus.ACTIVE);
        return ResponseEntity.ok(stocks);
    }

    @GetMapping("/search")
    public ResponseEntity<List<Stock>> searchStocks(@RequestParam String symbol) {
        List<Stock> results = stockRepository.findBySymbolContainingIgnoreCase(symbol);
        return ResponseEntity.ok(results);
    }

    @PostMapping
    public ResponseEntity<Stock> createStock(@RequestBody Stock stock) {
        Stock saved = stockRepository.save(stock);
        return new ResponseEntity<>(saved, HttpStatus.CREATED);
    }
}