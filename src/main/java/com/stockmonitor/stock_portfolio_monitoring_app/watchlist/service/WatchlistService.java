package com.stockmonitor.stock_portfolio_monitoring_app.watchlist.service;

import com.stockmonitor.stock_portfolio_monitoring_app.activity.entity.Activity;
import com.stockmonitor.stock_portfolio_monitoring_app.activity.repository.ActivityRepository;
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
    private final ActivityRepository activityRepository;

    @Transactional
    public WatchlistResponse createWatchList(CreateWatchlistRequest request) {
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + request.getUserId()));

        Watchlist watchlist = Watchlist.builder()
                .user(user)
                .name(request.getName().trim())
                .build();

        Watchlist saved = watchlistRepository.save(watchlist);

        Activity activity = Activity.builder()
                .user(user)
                .type("WATCHLIST_CREATED")
                .message(String.format("Created watchlist '%s'", saved.getName()))
                .build();
        activityRepository.save(activity);

        log.info("Watchlist created: name='{}', id={}, user={}", saved.getName(), saved.getId(), user.getEmail());

        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<WatchlistResponse> getUserWatchList(UUID userId) {
        return watchlistRepository.findByUserId(userId).stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public WatchlistResponse getWatchListDetails(UUID watchlistId) {
        Watchlist watchlist = watchlistRepository.findById(watchlistId)
                .orElseThrow(() -> new IllegalArgumentException("Watchlist not found with id: " + watchlistId));

        return mapToResponse(watchlist);
    }

    @Transactional
    public WatchlistResponse addStockToWatchList(UUID watchlistId, AddWatchlistItemRequest request) {
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

        Activity activity = Activity.builder()
                .user(watchlist.getUser())
                .type("WATCHLIST_STOCK_ADDED")
                .message(String.format("Added %s to watchlist '%s'", stock.getSymbol(), watchlist.getName()))
                .build();
        activityRepository.save(activity);

        log.info("Stock added to watchlist: symbol={}, watchlist='{}', user={}",
                stock.getSymbol(), watchlist.getName(), watchlist.getUser().getEmail());

        return mapToResponse(watchlist);
    }

    @Transactional
    public void removeStockFromWatchlist(UUID watchlistId, UUID stockId) {
        watchlistItemRepository.deleteByWatchlistIdAndStockId(watchlistId, stockId);
        log.info("Stock removed from watchlist: stockId={}, watchlistId={}", stockId, watchlistId);
    }

    @Transactional
    public void deleteWatchlist(UUID watchlistId) {
        watchlistRepository.deleteById(watchlistId);
        log.info("Watchlist deleted: id={}", watchlistId);
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
                    .orElseThrow(() -> new IllegalArgumentException("Could not find or discover stock with symbol: " + symbol));
        }
        throw new IllegalArgumentException("Either stockId or symbol must be provided");
    }

    private WatchlistResponse mapToResponse(Watchlist watchlist) {
        List<WatchlistItem> items = watchlist.getItems();
        List<WatchlistItemResponse> itemResponses = new ArrayList<>();

        if (items != null && !items.isEmpty()) {
            List<UUID> stockIds = items.stream().map(i -> i.getStock().getId()).toList();

            Map<UUID, MarketPrice> priceMap = marketPriceRepository.findAllById(stockIds).stream()
                    .collect(Collectors.toMap(MarketPrice::getStockId, Function.identity()));

            List<AlertDefinition> alerts = alertDefinitionRepository.findByUserId(watchlist.getUser().getId());
            Map<UUID, List<AlertDefinition>> alertsByStock = alerts.stream()
                    .filter(AlertDefinition::isActive)
                    .collect(Collectors.groupingBy(a -> a.getStock().getId()));

            for (WatchlistItem item : items) {
                Stock stock = item.getStock();
                MarketPrice mp = priceMap.get(stock.getId());
                List<AlertDefinition> activeAlerts = alertsByStock.getOrDefault(stock.getId(), Collections.emptyList());

                WatchlistItemResponse itemResponse = WatchlistItemResponse.builder()
                        .itemId(item.getId())
                        .stockId(stock.getId())
                        .symbol(stock.getSymbol())
                        .exchange(stock.getExchange())
                        .companyName(stock.getCompanyName())
                        .currentPrice(mp != null ? mp.getPrice() : null)
                        .asOf(mp != null ? mp.getAsOf() : null)
                        .notes(item.getNotes())
                        .addedAt(item.getAddedAt())
                        .build();

                itemResponses.add(itemResponse);
            }
        }

        return WatchlistResponse.builder()
                .id(watchlist.getId())
                .name(watchlist.getName())
                .userId(watchlist.getUser().getId())
                .totalItems(itemResponses.size())
                .items(itemResponses)
                .createdAt(watchlist.getCreatedAt())
                .build();
    }
}
