package com.stockmonitor.stock_portfolio_monitoring_app.portfolio.controller;

import com.stockmonitor.stock_portfolio_monitoring_app.portfolio.dto.*;
import com.stockmonitor.stock_portfolio_monitoring_app.portfolio.repository.PortfolioRepository;
import com.stockmonitor.stock_portfolio_monitoring_app.portfolio.service.ExcelPortfolioService;
import com.stockmonitor.stock_portfolio_monitoring_app.portfolio.service.PortfolioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.parameters.P;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/portfolio")
@RequiredArgsConstructor
public class PortfolioController {

    private final PortfolioService portfolioService;
    private final ExcelPortfolioService excelPortfolioService;
    private final PortfolioRepository portfolioRepository;

    @PostMapping
    public ResponseEntity<PortfolioResponse> createPortfolio(@Valid @RequestBody CreatePortfolioRequest request) {
        PortfolioResponse response = portfolioService.createPortfolio(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<PortfolioResponse>> getUserPortfolios(@PathVariable UUID userId) {
        List<PortfolioResponse> portfolios = portfolioService.getUserPortfolios(userId);
        return ResponseEntity.ok(portfolios);
    }

    @GetMapping("/{portfolioId}")
    public ResponseEntity<PortfolioSummaryResponse> getPortfolioById(@PathVariable UUID portfolioId) {
        PortfolioSummaryResponse summary = portfolioService.getPortfolioById(portfolioId);
        return ResponseEntity.ok(summary);
    }

    @PostMapping("/buy")
    public ResponseEntity<HoldingResponse> buyStock(@Valid @RequestBody BuyStockRequest request) {
        HoldingResponse response = portfolioService.buyStock(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PostMapping("/sell")
    public ResponseEntity<SellStockResponse> sellStock(@Valid @RequestBody SellStockRequest request) {
        SellStockResponse sellStockResponse = portfolioService.sellStock(request);
        return ResponseEntity.ok(sellStockResponse);
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

    @GetMapping("/{portfolioId}/transactions")
    public ResponseEntity<List<PortfolioTransactionResponse>> getPortfolioTransactions(@PathVariable UUID portfolioId){
        List<PortfolioTransactionResponse> transactions = portfolioService.getPortfolioTransactions(portfolioId);
        return ResponseEntity.ok(transactions);
    }

    @GetMapping("/transactions/user/{userId}")
    public ResponseEntity<List<PortfolioTransactionResponse>> getUserTransactions(@PathVariable UUID userId){
        List<PortfolioTransactionResponse> transactions = portfolioService.getUserTransactions(userId);

        return ResponseEntity.ok(transactions);
    }

    @DeleteMapping("/{portfolioId}")
    public ResponseEntity<String> deletePortfolio(@PathVariable UUID portfolioId,
        @RequestParam(required = true) UUID userId){
        portfolioService.deletePorfolio(portfolioId , userId);

        return ResponseEntity.ok("Portfolio deleted successfully");
    }
}