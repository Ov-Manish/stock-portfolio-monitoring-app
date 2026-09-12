package com.stockmonitor.stock_portfolio_monitoring_app.marketdata.repository;

import com.stockmonitor.stock_portfolio_monitoring_app.marketdata.entity.MarketPrice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MarketPriceRepository extends JpaRepository<MarketPrice , UUID> {


}
