package com.stockmonitor.stock_portfolio_monitoring_app.stock.service;


import com.stockmonitor.stock_portfolio_monitoring_app.constants.StockStatus;
import com.stockmonitor.stock_portfolio_monitoring_app.marketdata.client.YahooFinanceClient;
import com.stockmonitor.stock_portfolio_monitoring_app.marketdata.dto.MarketTickEvent;
import com.stockmonitor.stock_portfolio_monitoring_app.marketdata.entity.MarketPrice;
import com.stockmonitor.stock_portfolio_monitoring_app.marketdata.repository.MarketPriceRepository;
import com.stockmonitor.stock_portfolio_monitoring_app.stock.dto.StockSearchResponse;
import com.stockmonitor.stock_portfolio_monitoring_app.stock.entity.Stock;
import com.stockmonitor.stock_portfolio_monitoring_app.stock.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockService {

    private final MarketPriceRepository marketPriceRepository;
    private final StockRepository stockRepository;
    private final YahooFinanceClient yahooFinanceClient;

    @Transactional
   public List<StockSearchResponse> searchStocks(String query){
        if (query == null || query.trim().isEmpty()){
            return Collections.emptyList();
        }

        String symbol = query.trim().toUpperCase();


        // 1. Search existing stocks in DB
        List<Stock> existingStocks = stockRepository.findBySymbolContainingIgnoreCase(symbol);

        if (!existingStocks.isEmpty()){
            return mapStocksToResponse(existingStocks);
        }

        // 2. Not found in DB -> Auto-discover from Yahoo Finance
        log.info("Stock '{}' not found in database. Attempting auto-discovery via Yahoo Finance...", symbol);
        Optional<MarketTickEvent> tickOpt = yahooFinanceClient.fetchLatestMarketTick(symbol);


        if (tickOpt.isEmpty()) {
            log.warn("Symbol '{}' not found on Yahoo Finance.", symbol);
            return Collections.emptyList();
        }


        MarketTickEvent tick = tickOpt.get();
        // 3. Auto-register new stock into database
        Stock newStock = Stock.builder()
                .symbol(symbol)
                .exchange("NSE")
                .companyName(tick.getCompanyName() != null ? tick.getCompanyName() : symbol)
                .currency("INR")
                .status(StockStatus.ACTIVE)
                .build();

        Stock savedStock = stockRepository.save(newStock);
        log.info("Auto-registered new stock into database: {} ({})", savedStock.getSymbol(), savedStock.getCompanyName());
        // 4. Save initial market price
        Instant asOf = tick.getTimestamp() != null ? Instant.ofEpochMilli(tick.getTimestamp()) : Instant.now();
        marketPriceRepository.upsertMarketPrice(
                savedStock.getId(),
                tick.getPrice(),
                asOf,
                "YAHOO_FINANCE"
        );

        StockSearchResponse response = StockSearchResponse.builder()
                .id(savedStock.getId())
                .symbol(savedStock.getSymbol())
                .exchange(savedStock.getExchange())
                .companyName(savedStock.getCompanyName())
                .sector(savedStock.getSector())
                .currency(savedStock.getCurrency())
                .currentPrice(tick.getPrice())
                .dayChange(tick.getChange())
                .dayChangePercent(tick.getChangePercent())
                .dayHigh(tick.getDayHigh())
                .dayLow(tick.getDayLow())
                .volume(tick.getVolume())
                .asOf(asOf)
                .build();
        return List.of(response);

    }


//    Get All Active Stocks
    @Transactional(readOnly = true)
    public List<StockSearchResponse> getAllActiveStocksWithPrices(){
        List<Stock> activeStocks = stockRepository.findByStatus(StockStatus.ACTIVE);
        return mapStocksToResponse(activeStocks);
    }




//    Map Response Method
    private List<StockSearchResponse> mapStocksToResponse(List<Stock> stocks){
        // Collect all IDs to fetch prices in one single batch query

        List<UUID> stocksId = stocks.stream().map(Stock::getId).toList();
        Map<UUID, MarketPrice> priceMap = marketPriceRepository.findAllById(stocksId).stream()
                .collect(Collectors.toMap(MarketPrice::getStockId , p->p));

        return stocks.stream().map(stock -> {
            MarketPrice price = priceMap.get(stock.getId());

            return  StockSearchResponse.builder()
                    .id(stock.getId())
                    .symbol(stock.getSymbol())
                    .exchange(stock.getExchange())
                    .companyName(stock.getCompanyName())
                    .sector(stock.getSector())
                    .currency(stock.getCurrency())
                    .currentPrice(price !=null ? price.getPrice() : null)
                    .asOf(price != null ? price.getAsOf() : null)
                    .build();
        }).toList();
    }
}
