package com.stockmonitor.stock_portfolio_monitoring_app.marketdata.producer;


import com.stockmonitor.stock_portfolio_monitoring_app.constants.StockStatus;
import com.stockmonitor.stock_portfolio_monitoring_app.marketdata.client.YahooFinanceClient;
import com.stockmonitor.stock_portfolio_monitoring_app.stock.entity.Stock;
import com.stockmonitor.stock_portfolio_monitoring_app.stock.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class MarketDataScheduler {

    private final StockRepository stockRepository;
    private final YahooFinanceClient yahooFinanceClient;
    private final  MarketDataProducer marketDataProducer;

//    Runs Every 10 Seconds

    @Scheduled(fixedDelay = 5000)
    public void pollMarketData(){
        List<Stock> activeStocks = stockRepository.findByStatus(StockStatus.ACTIVE);

        if (activeStocks.isEmpty()){
            return;
        }

        // 1. Get unique symbols
        List<String> distinctSymbols = activeStocks.stream()
                .map(Stock::getSymbol)
                .distinct()
                .toList();

        log.debug("Polling live market data for {} active stocks...", distinctSymbols.size());

        // 2. Poll each stock with a 300ms polite pause to avoid Yahoo Finance rate limits
        for (String symbol : distinctSymbols) {
            yahooFinanceClient.fetchLatestMarketTick(symbol)
                    .ifPresent(marketDataProducer::publishMarketTick);
            try {
                Thread.sleep(150); // 150ms delay between API calls
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}
