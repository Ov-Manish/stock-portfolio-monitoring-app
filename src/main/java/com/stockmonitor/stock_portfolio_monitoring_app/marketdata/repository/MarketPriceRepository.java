package com.stockmonitor.stock_portfolio_monitoring_app.marketdata.repository;

import com.stockmonitor.stock_portfolio_monitoring_app.marketdata.entity.MarketPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public interface MarketPriceRepository extends JpaRepository<MarketPrice , UUID> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        INSERT INTO market_prices (stock_id, price, as_of, source, updated_at)
        VALUES (:stockId, :price, :asOf, :source, CURRENT_TIMESTAMP)
        ON CONFLICT (stock_id) 
        DO UPDATE SET 
            price = EXCLUDED.price,
            as_of = EXCLUDED.as_of,
            source = EXCLUDED.source,
            updated_at = CURRENT_TIMESTAMP
        """, nativeQuery = true)
    void upsertMarketPrice(@Param("stockId") UUID stockId,
                           @Param("price") BigDecimal price,
                           @Param("asOf") Instant asOf,
                           @Param("source") String source);
}
