package com.stockmonitor.stock_portfolio_monitoring_app.marketdata.service;

import com.stockmonitor.stock_portfolio_monitoring_app.marketdata.client.YahooFinanceClient;
import com.stockmonitor.stock_portfolio_monitoring_app.marketdata.dto.DailyPriceResponse;
import com.stockmonitor.stock_portfolio_monitoring_app.marketdata.dto.StockHistoryResponse;
import com.stockmonitor.stock_portfolio_monitoring_app.marketdata.entity.PriceHistoryDaily;
import com.stockmonitor.stock_portfolio_monitoring_app.marketdata.repository.PriceHistoryDailyRepository;
import com.stockmonitor.stock_portfolio_monitoring_app.stock.entity.Stock;
import com.stockmonitor.stock_portfolio_monitoring_app.stock.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PriceHistoryService {

    private final PriceHistoryDailyRepository priceHistoryDailyRepository;
    private final StockRepository stockRepository;
    private final YahooFinanceClient yahooFinanceClient;

    @Transactional
    public StockHistoryResponse getStockHistory(String symbolQuery, String range) {
        String symbol = symbolQuery.trim().toUpperCase();
        String validRange = (range != null && !range.trim().isEmpty()) ? range.trim().toLowerCase() : "1mo";

        // 1. Locate the stock in our master catalog
        Stock stock = stockRepository.findAllBySymbol(symbol).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Stock not found with symbol: " + symbol));

        // 2. Compute date range
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = calculateStartDate(validRange, endDate);

        // 3. Query PostgreSQL for cached history
        List<PriceHistoryDaily> cachedHistory = priceHistoryDailyRepository
                .findByStockIdAndPriceDateBetweenOrderByPriceDateAsc(stock.getId(), startDate, endDate);

        // 4. If cache is empty OR outdated, sync missing days from Yahoo Finance
        // 4. Sync if cache is empty, or missing older history, or missing latest days
        boolean needsSync = cachedHistory.isEmpty()
                || cachedHistory.get(0).getPriceDate().isAfter(startDate.plusDays(7))
                || cachedHistory.get(cachedHistory.size() - 1).getPriceDate().isBefore(endDate.minusDays(3));

        if (needsSync) {
            log.info("Syncing latest historical data from Yahoo Finance for symbol '{}'...", symbol);
            syncHistoricalDataFromYahoo(stock, validRange);

            // Re-fetch cached data after sync
            cachedHistory = priceHistoryDailyRepository
                    .findByStockIdAndPriceDateBetweenOrderByPriceDateAsc(stock.getId(), startDate, endDate);
        }

        // 5. Map DB entities to DailyPriceResponse DTOs
        List<DailyPriceResponse> candles = cachedHistory.stream()
                .map(this::mapToDailyResponse)
                .collect(Collectors.toList());

        return StockHistoryResponse.builder()
                .symbol(stock.getSymbol())
                .companyName(stock.getCompanyName())
                .range(validRange)
                .totalCandles(candles.size())
                .data(candles)
                .build();
    }

    private void syncHistoricalDataFromYahoo(Stock stock, String range) {
        List<DailyPriceResponse> yahooPrices = yahooFinanceClient.fetchHistoricalDailyPrices(stock.getSymbol(), range);

        List<PriceHistoryDaily> entitiesToSave = new ArrayList<>();

        for (DailyPriceResponse priceDto : yahooPrices) {
            // Guard against duplicates before persisting
            boolean alreadyExists = priceHistoryDailyRepository.existsByStockIdAndPriceDate(stock.getId(), priceDto.getDate());
            if (!alreadyExists) {
                PriceHistoryDaily entity = PriceHistoryDaily.builder()
                        .stock(stock)
                        .priceDate(priceDto.getDate())
                        .open(priceDto.getOpen())
                        .high(priceDto.getHigh())
                        .low(priceDto.getLow())
                        .close(priceDto.getClose())
                        .volume(priceDto.getVolume())
                        .build();

                entitiesToSave.add(entity);
            }
        }

        if (!entitiesToSave.isEmpty()) {
            priceHistoryDailyRepository.saveAll(entitiesToSave);
            log.info("Persisted {} new daily candles into price_history_daily for symbol: {}",
                    entitiesToSave.size(), stock.getSymbol());
        }
    }

    private LocalDate calculateStartDate(String range, LocalDate endDate) {
        return switch (range) {
            case "5d" -> endDate.minusDays(7);
            case "1mo" -> endDate.minusMonths(1);
            case "3mo" -> endDate.minusMonths(3);
            case "6mo" -> endDate.minusMonths(6);
            case "1y" -> endDate.minusYears(1);
            case "5y" -> endDate.minusYears(5);
            default -> endDate.minusMonths(1);
        };
    }

    private DailyPriceResponse mapToDailyResponse(PriceHistoryDaily entity) {
        return DailyPriceResponse.builder()
                .date(entity.getPriceDate())
                .open(entity.getOpen())
                .high(entity.getHigh())
                .low(entity.getLow())
                .close(entity.getClose())
                .volume(entity.getVolume())
                .build();
    }
}