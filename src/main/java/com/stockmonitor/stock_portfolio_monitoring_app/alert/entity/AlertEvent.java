package com.stockmonitor.stock_portfolio_monitoring_app.alert.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "alert_events")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY , optional = false)
    @JoinColumn(name = "aleart_defination_db" , nullable = false)
    private  AlertDefinition alertDefinition;

    @Column(name = "triggered_price", nullable = false , precision = 14 , scale = 4)
    private BigDecimal truggeredPrice;


    @Column(name = "percentageMove", nullable = false , precision = 14 , scale = 4)
    private BigDecimal percentage_move;


    @Column(name = "triggered_at" , nullable = false)
    private Instant triggeredAt;

    @Column(name = "evenSourceId")
    private UUID eventSourceId;

}
