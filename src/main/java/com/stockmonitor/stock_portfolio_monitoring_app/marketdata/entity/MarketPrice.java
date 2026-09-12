package com.stockmonitor.stock_portfolio_monitoring_app.marketdata.entity;

import com.stockmonitor.stock_portfolio_monitoring_app.stock.entity.Stock;
import jakarta.persistence.*;
import lombok.*;

import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;


@Entity
@Table(name = "market_prices")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketPrice {
    @Id
    @Column(name = "stock_id")
    private UUID stockId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id")
    private Stock stock;

    @Column(nullable = false , precision = 14 , scale = 4)
    private BigDecimal price;


    @Column(name = "as_of" ,nullable = false)
    private Instant asOf;

    @Column(nullable = false , length = 30)
    private String source;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

}
