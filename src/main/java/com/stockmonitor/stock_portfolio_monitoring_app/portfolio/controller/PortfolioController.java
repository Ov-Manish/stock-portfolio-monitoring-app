package com.stockmonitor.stock_portfolio_monitoring_app.portfolio.controller;

import com.stockmonitor.stock_portfolio_monitoring_app.portfolio.dto.*;
import com.stockmonitor.stock_portfolio_monitoring_app.portfolio.service.ExcelPortfolioService;
import com.stockmonitor.stock_portfolio_monitoring_app.portfolio.service.PortfolioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/portfolio")
@RequiredArgsConstructor
public class PortfolioController {

    private final PortfolioService portfolioService;
    private final ExcelPortfolioService excelPortfolioService;

    @PostMapping("/buy")
    public ResponseEntity<HoldingResponse> buyStock(@Valid @RequestBody BuyStockRequest request) {
        HoldingResponse response = portfolioService.buyStock(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PostMapping("/sell")
    public ResponseEntity<String> sellStock(@Valid @RequestBody SellStockRequest request) {
        portfolioService.sellStock(request);
        return ResponseEntity.ok("Stock sold successfully");
    }

    @GetMapping("/summary/{userId}")
    public ResponseEntity<PortfolioSummaryResponse> getPortfolioSummary(@PathVariable UUID userId) {
        PortfolioSummaryResponse summary = portfolioService.getPortfolioSummary(userId);
        return ResponseEntity.ok(summary);
    }

    @PostMapping(value = "/upload-excel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ExcelUploadResponse> uploadPortfolioExcel(
            @RequestParam("file") MultipartFile file,
            @RequestParam("userId") UUID userId) {
        ExcelUploadResponse response = excelPortfolioService.importPortfolioFromExcel(file, userId);
        return ResponseEntity.ok(response);
    }
}