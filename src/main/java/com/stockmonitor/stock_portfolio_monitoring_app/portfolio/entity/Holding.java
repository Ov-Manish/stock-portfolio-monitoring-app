package com.stockmonitor.stock_portfolio_monitoring_app.portfolio.entity;

import com.stockmonitor.stock_portfolio_monitoring_app.stock.entity.Stock;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "holdings",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_portfolio_stock", columnNames = {"portfolio_id", "stock_id"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Holding {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private Portfolio portfolio;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;


    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal quantity;


    @Column(name = "avg_buy_price", nullable = false, precision = 14, scale = 4)
    private BigDecimal avgBuyPrice;

    @Version
    @Column(nullable = false)
    private Integer version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}