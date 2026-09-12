package com.stockmonitor.stock_portfolio_monitoring_app.marketdata.entity;

import com.stockmonitor.stock_portfolio_monitoring_app.stock.entity.Stock;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "price_history_daily",
        uniqueConstraints = {
            @UniqueConstraint( name = "uk_stock_price_date", columnNames = {"stock_id", "price_date"})
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriceHistoryDaily {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY , optional = false)
    @JoinColumn(name = "stock_id" , nullable = false)
    private Stock stock;


    @Column(name = "price_date" , nullable = false)
    private LocalDate priceDate;

    @Column(nullable = false, precision = 14 , scale = 4)
    private BigDecimal high;

    @Column(nullable = false, precision = 14 , scale = 4)
    private BigDecimal low;

    @Column(nullable = false, precision = 14 , scale = 4)
    private BigDecimal open;

    @Column(nullable = false, precision = 14 , scale = 4)
    private BigDecimal close;

    private Long volume;

    @CreationTimestamp
    @Column(nullable = false , updatable = false)
    private Instant createdAt;

}
