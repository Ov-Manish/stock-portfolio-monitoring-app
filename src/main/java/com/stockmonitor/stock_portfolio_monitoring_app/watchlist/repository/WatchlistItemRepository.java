package com.stockmonitor.stock_portfolio_monitoring_app.watchlist.repository;

import com.stockmonitor.stock_portfolio_monitoring_app.watchlist.entity.WatchlistItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WatchlistItemRepository extends JpaRepository<WatchlistItem, UUID> {
    Optional<WatchlistItem> findByWatchlistIdAndStockId(UUID watchlistId, UUID stockId);
    void deleteByWatchlistIdAndStockId(UUID watchlistId, UUID stockId);
}