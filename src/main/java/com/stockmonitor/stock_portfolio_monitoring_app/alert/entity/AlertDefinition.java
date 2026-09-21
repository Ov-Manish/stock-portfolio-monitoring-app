package com.stockmonitor.stock_portfolio_monitoring_app.alert.entity;


import com.stockmonitor.stock_portfolio_monitoring_app.constants.AlertDirection;
import com.stockmonitor.stock_portfolio_monitoring_app.constants.AlertScope;
import com.stockmonitor.stock_portfolio_monitoring_app.portfolio.entity.Portfolio;
import com.stockmonitor.stock_portfolio_monitoring_app.stock.entity.Stock;
import com.stockmonitor.stock_portfolio_monitoring_app.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "alert_definitions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY , optional = false)
    @JoinColumn(name = "user_id" , nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false , length = 10)
    private AlertScope scope;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id")
    private Stock stock;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portfolio_id")
    private Portfolio portfolio;

    @Enumerated(EnumType.STRING)
    @Column(length = 10, nullable = false)
    private AlertDirection direction;

    @Column(nullable = false , precision = 8 , scale = 4)
    private BigDecimal thresholdValue;

    @Column(nullable = false , precision = 14 , scale = 4)
    private BigDecimal referencePrice;


    @Column(nullable = false)
    @Builder.Default
    private boolean isActive = true;

    @Column(nullable = false)
    @Builder.Default
    private Integer cooldownMinutes = 60;

    @Column(name = "last_triggered_at")
    private Instant lastTriggeredAt ;


    @CreationTimestamp
    @Column(nullable = false , updatable = false)
    private Instant createdAt;


}
