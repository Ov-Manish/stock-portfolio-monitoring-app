package com.stockmonitor.stock_portfolio_monitoring_app.marketdata.repository;

import com.stockmonitor.stock_portfolio_monitoring_app.marketdata.entity.PriceHistoryDaily;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface PriceHistoryDailyRepository extends JpaRepository<PriceHistoryDaily , UUID> {

    List<PriceHistoryDaily> findByStockIdAndPriceDateBetweenOrderByPriceDateAsc(UUID stockId, LocalDate startDate, LocalDate endDate);

    boolean existsByStockIdAndPriceDate(UUID stockId, LocalDate priceDate);

}
