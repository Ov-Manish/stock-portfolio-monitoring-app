package com.stockmonitor.stock_portfolio_monitoring_app.watchlist.service;


import com.stockmonitor.stock_portfolio_monitoring_app.alert.entity.AlertDefinition;
import com.stockmonitor.stock_portfolio_monitoring_app.alert.repository.AlertDefinitionRepository;
import com.stockmonitor.stock_portfolio_monitoring_app.marketdata.entity.MarketPrice;
import com.stockmonitor.stock_portfolio_monitoring_app.marketdata.repository.MarketPriceRepository;
import com.stockmonitor.stock_portfolio_monitoring_app.stock.entity.Stock;
import com.stockmonitor.stock_portfolio_monitoring_app.stock.repository.StockRepository;
import com.stockmonitor.stock_portfolio_monitoring_app.stock.service.StockService;
import com.stockmonitor.stock_portfolio_monitoring_app.user.entity.User;
import com.stockmonitor.stock_portfolio_monitoring_app.user.repository.UserRepository;
import com.stockmonitor.stock_portfolio_monitoring_app.watchlist.dto.AddWatchlistItemRequest;
import com.stockmonitor.stock_portfolio_monitoring_app.watchlist.dto.CreateWatchlistRequest;
import com.stockmonitor.stock_portfolio_monitoring_app.watchlist.dto.WatchlistItemResponse;
import com.stockmonitor.stock_portfolio_monitoring_app.watchlist.dto.WatchlistResponse;
import com.stockmonitor.stock_portfolio_monitoring_app.watchlist.entity.Watchlist;
import com.stockmonitor.stock_portfolio_monitoring_app.watchlist.entity.WatchlistItem;
import com.stockmonitor.stock_portfolio_monitoring_app.watchlist.repository.WatchlistItemRepository;
import com.stockmonitor.stock_portfolio_monitoring_app.watchlist.repository.WatchlistRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WatchlistService {

    private final WatchlistRepository watchlistRepository;
    private final WatchlistItemRepository watchlistItemRepository;
    private final StockRepository stockRepository;
    private final StockService stockService;
    private final UserRepository userRepository;
    private final MarketPriceRepository marketPriceRepository;
    private final AlertDefinitionRepository alertDefinitionRepository;


    @Transactional
    public WatchlistResponse createWatchList(CreateWatchlistRequest request){
        User user =  userRepository.findById(request.getUserId())
                .orElseThrow( () -> new IllegalArgumentException("User not found with id: " + request.getUserId()) );

        Watchlist watchlist = Watchlist.builder()
                .user(user)
                .name(request.getName())
                .build();

        Watchlist saved = watchlistRepository.save(watchlist);

        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<WatchlistResponse> getUserWatchList(UUID userId){
        return watchlistRepository.findByUserId(userId).stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public WatchlistResponse getWatchListDetails(UUID watchlistId){
        Watchlist watchlist = watchlistRepository.findById(watchlistId)
                .orElseThrow(() -> new IllegalArgumentException("Watchlist not found with id: " + watchlistId));

        return  mapToResponse(watchlist);
    }

    @Transactional
    public WatchlistResponse addStockToWatchList(UUID watchlistId , AddWatchlistItemRequest request){
        Watchlist watchlist = watchlistRepository.findById(watchlistId)
                .orElseThrow(() -> new IllegalArgumentException("Watchlist not found with id: " + watchlistId));
        Stock stock = resolveStock(request);
        // Check if already in watchlist
        Optional<WatchlistItem> existing = watchlistItemRepository.findByWatchlistIdAndStockId(watchlistId, stock.getId());
        if (existing.isPresent()) {
            throw new IllegalArgumentException("Stock " + stock.getSymbol() + " is already in this watchlist");
        }
        WatchlistItem item = WatchlistItem.builder()
                .watchlist(watchlist)
                .stock(stock)
                .notes(request.getNotes())
                .build();
        watchlistItemRepository.save(item);
        watchlist.getItems().add(item);
        return mapToResponse(watchlist);
    }
    @Transactional
    public void removeStockFromWatchlist(UUID watchlistId, UUID stockId) {
        watchlistItemRepository.deleteByWatchlistIdAndStockId(watchlistId, stockId);
    }
    @Transactional
    public void deleteWatchlist(UUID watchlistId) {
        watchlistRepository.deleteById(watchlistId);
    }

    private Stock resolveStock(AddWatchlistItemRequest request) {
        if (request.getStockId() != null) {
            return stockRepository.findById(request.getStockId())
                    .orElseThrow(() -> new IllegalArgumentException("Stock not found with id: " + request.getStockId()));
        }
        if (request.getSymbol() != null && !request.getSymbol().trim().isEmpty()) {
            String symbol = request.getSymbol().trim().toUpperCase();
            List<Stock> stocks = stockRepository.findAllBySymbol(symbol);
            if (!stocks.isEmpty()) {
                return stocks.get(0);
            }
            // Trigger auto-discovery if not in DB
            stockService.searchStocks(symbol);
            return stockRepository.findAllBySymbol(symbol).stream()
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Could not find or auto-discover stock: " + symbol));
        }
        throw new IllegalArgumentException("Either stockId or symbol must be provided");
    }

//    MAP TO RESPONSE
    private WatchlistResponse mapToResponse(Watchlist watchlist){
        List<WatchlistItem> watchlistItems = watchlist.getItems();
        List<WatchlistItemResponse> itemResponses = new ArrayList<>();

        if (watchlistItems != null && !watchlistItems.isEmpty()){
            List<UUID> stockIds = watchlistItems.stream().map( i -> i.getStock().getId()).toList();

//           fetching live prices
            Map<UUID, MarketPrice> priceMap = marketPriceRepository.findAllById(stockIds).stream()
                    .collect(Collectors.toMap(MarketPrice::getStockId , Function.identity()));

            // 2. Batch fetch active user alerts for these stocks

            List<AlertDefinition> alerts = alertDefinitionRepository.findByUserId(watchlist.getUser().getId());
            Map<UUID, AlertDefinition> stockAlertMap = alerts.stream()
                    .filter(a -> a.isActive() && a.getStock() != null)
                    .collect(Collectors.toMap(a -> a.getStock().getId(), Function.identity(), (a1, a2) -> a1));


            for (WatchlistItem watchlistItem : watchlistItems){
                Stock stock = watchlistItem.getStock();
                MarketPrice marketPrice = priceMap.get(stock.getId());
                AlertDefinition alert = stockAlertMap.get(stock.getId());

                itemResponses.add(WatchlistItemResponse.builder()
                        .itemId(watchlistItem.getId())
                        .stockId(stock.getId())
                        .symbol(stock.getSymbol())
                        .companyName(stock.getCompanyName())
                        .exchange(stock.getExchange())
                        .currentPrice(marketPrice != null ? marketPrice.getPrice() : null)
                        .asOf(marketPrice != null ? marketPrice.getAsOf() : null)
                        .notes(watchlistItem.getNotes())
                        .addedAt(watchlistItem.getAddedAt())
                        .hasActiveAlert(alert != null)
                        .alertTargetPrice(alert != null ? alert.getThresholdValue() : null)
                        .build());
            }

        }

            return WatchlistResponse.builder()
                    .id(watchlist.getId())
                    .userId(watchlist.getUser().getId())
                    .name(watchlist.getName())
                    .totalItems(itemResponses.size())
                    .createdAt(watchlist.getCreatedAt())
                    .updatedAt(watchlist.getUpdatedAt())
                    .items(itemResponses)
                    .build();
    }
}
