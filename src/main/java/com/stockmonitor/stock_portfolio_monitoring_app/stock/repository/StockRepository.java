package com.stockmonitor.stock_portfolio_monitoring_app.stock.repository;


import com.stockmonitor.stock_portfolio_monitoring_app.constants.StockStatus;
import com.stockmonitor.stock_portfolio_monitoring_app.stock.entity.Stock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StockRepository extends JpaRepository<Stock , UUID> {
    Optional<Stock> findBySymbolAndExchange(String symbol , String exchange);

    List<Stock> findBySymbolContainingIgnoreCase(String symbol);

    List<Stock> findByStatus(StockStatus status);

    boolean existsBySymbolAndExchange(String symbol , String exchange);
}
