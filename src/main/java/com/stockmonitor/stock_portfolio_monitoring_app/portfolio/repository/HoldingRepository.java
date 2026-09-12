package com.stockmonitor.stock_portfolio_monitoring_app.portfolio.repository;


import com.stockmonitor.stock_portfolio_monitoring_app.portfolio.entity.Holding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface HoldingRepository extends JpaRepository<Holding , UUID> {
//    Give me all holdings that belong to this portfolio
    List<Holding> findByPortfolioId(UUID portfolio_id);

// CHeking if the Stock Already exist befor e buying and selling
    Optional<Holding> findByPortfolioIdAndStockId(UUID portfolioId , UUID stockId);

//    User Want to Send the Stock
    void deleteByPortfolioIdAndStockId(UUID portfolioId , UUID stockId);

}
