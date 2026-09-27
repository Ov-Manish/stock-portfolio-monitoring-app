package com.stockmonitor.stock_portfolio_monitoring_app.portfolio.repository;

import com.stockmonitor.stock_portfolio_monitoring_app.portfolio.entity.PortfolioTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PortfolioTransactionRepository extends JpaRepository<PortfolioTransaction, UUID> {
    List<PortfolioTransaction> findByPortfolioIdOrderByCreatedAtDesc(UUID portfolioId);
    List<PortfolioTransaction> findByUserIdOrderByCreatedAtDesc(UUID userId);
}