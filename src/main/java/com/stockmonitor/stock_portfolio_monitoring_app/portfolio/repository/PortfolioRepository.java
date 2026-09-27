package com.stockmonitor.stock_portfolio_monitoring_app.portfolio.repository;

import com.stockmonitor.stock_portfolio_monitoring_app.portfolio.entity.Portfolio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PortfolioRepository extends JpaRepository<Portfolio, UUID> {

    List<Portfolio> findByUserId(UUID userId);

    Optional<Portfolio> findByIdAndUserId(UUID id, UUID userId);

    Optional<Portfolio> findFirstByUserIdOrderByCreatedAtAsc(UUID userId);

    boolean existsByUserId(UUID userId);
}
