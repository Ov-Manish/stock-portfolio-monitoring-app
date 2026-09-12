package com.stockmonitor.stock_portfolio_monitoring_app.stock.entity;

import com.stockmonitor.stock_portfolio_monitoring_app.constants.StockStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "stocks",
        uniqueConstraints ={
                @UniqueConstraint(name = "stocks" , columnNames = {"symbol" , "exchange"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Stock {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false , length = 20)
    private String symbol;

    @Column(nullable = false,length = 10)
    private String exchange;

    @Column(name = "company_name" , nullable = false)
    private String companyName;

    @Column(length = 100)
    private String sector;

    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "INR";

    @Column(length = 12)
    private String isin;


    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private StockStatus status = StockStatus.ACTIVE;

    @Column(name = "week52_high" , precision = 14 , scale = 4)
    private BigDecimal week52High;

    @Column(name = "week52_low" , precision = 14 , scale = 4)
    private BigDecimal week52Low;

    @CreationTimestamp
    @Column(name = "created_at" , nullable = false , updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at" , nullable = false)
    private LocalDateTime updatedAt;
}
