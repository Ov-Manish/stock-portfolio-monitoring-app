package com.stockmonitor.stock_portfolio_monitoring_app.alert.service;

import com.stockmonitor.stock_portfolio_monitoring_app.alert.entity.AlertDefinition;
import com.stockmonitor.stock_portfolio_monitoring_app.alert.entity.AlertEvent;
import com.stockmonitor.stock_portfolio_monitoring_app.alert.repository.AlertDefinitionRepository;
import com.stockmonitor.stock_portfolio_monitoring_app.alert.repository.AlertEventRepository;
import com.stockmonitor.stock_portfolio_monitoring_app.config.KafkaConfig;
import com.stockmonitor.stock_portfolio_monitoring_app.config.RabbitMQConfig;
import com.stockmonitor.stock_portfolio_monitoring_app.constants.AlertDirection;
import com.stockmonitor.stock_portfolio_monitoring_app.marketdata.dto.AlertBreachEvent;
import com.stockmonitor.stock_portfolio_monitoring_app.marketdata.dto.MarketTickEvent;
import com.stockmonitor.stock_portfolio_monitoring_app.marketdata.entity.MarketPrice;
import com.stockmonitor.stock_portfolio_monitoring_app.marketdata.repository.MarketPriceRepository;
import com.stockmonitor.stock_portfolio_monitoring_app.stock.entity.Stock;
import com.stockmonitor.stock_portfolio_monitoring_app.stock.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertEngineService {

    private final StockRepository stockRepository;
    private final MarketPriceRepository marketPriceRepository;
    private final AlertDefinitionRepository alertDefinitionRepository;
    private final AlertEventRepository alertEventRepository;
    private final RabbitTemplate rabbitTemplate;

    @KafkaListener(topics = KafkaConfig.MARKET_TICKS_TOPIC, groupId = "stock-portfolio-group")
    @Transactional
    public void processMarketTick(MarketTickEvent tickEvent){
        log.info("AlertEngine received tick: {} @ ₹{}", tickEvent.getSymbol(), tickEvent.getPrice());

        List<Stock> stocks = stockRepository.findAllBySymbol(tickEvent.getSymbol());

        if (stocks.isEmpty()){
            log.warn("Stock symbol {} not found in database. Skipping.", tickEvent.getSymbol());
            return;
        }

        for (Stock stock : stocks) {
            // 1. Update Market Price in Database
            updateMarketPrice(stock, tickEvent);
            // 2. Evaluate Active Alert Rules
            evaluateAlerts(stock, tickEvent);
        }
    }

    private void updateMarketPrice(Stock stock, MarketTickEvent tickEvent){
        Instant asOf = tickEvent.getTimestamp() != null
                ? Instant.ofEpochMilli(tickEvent.getTimestamp())
                : Instant.now();

        marketPriceRepository.upsertMarketPrice(
                stock.getId(),
                tickEvent.getPrice(),
                asOf,
                "YAHOO_FINANCE"
        );
    }

    private void evaluateAlerts(Stock stock , MarketTickEvent tickEvent){
        List<AlertDefinition> activeAlerts = alertDefinitionRepository.findByStockIdAndIsActiveTrue(stock.getId());

        for (AlertDefinition alert : activeAlerts){
            boolean isBreached = false;

            if (alert.getDirection() == AlertDirection.ABOVE){
                isBreached = tickEvent.getPrice().compareTo(alert.getThresholdValue()) >= 0;
            } else if(alert.getDirection() == AlertDirection.BELOW){
                isBreached = tickEvent.getPrice().compareTo(alert.getThresholdValue()) <= 0;
            }


            if (isBreached){
                handleBreach(alert , stock , tickEvent);
            }
        }
    }


    private void handleBreach(AlertDefinition alert, Stock stock, MarketTickEvent tickEvent) {
        // Cooldown check (prevent spamming the user)
        if (alert.getLastTriggeredAt() != null) {
            long minutesSinceLastTrigger = Duration.between(alert.getLastTriggeredAt(), Instant.now()).toMinutes();
            if (minutesSinceLastTrigger < alert.getCooldownMinutes()) {
                log.info("Alert {} for {} is in cooldown ({} mins remaining). Skipping trigger.",
                        alert.getId(), stock.getSymbol(), alert.getCooldownMinutes() - minutesSinceLastTrigger);
                return;
            }
        }
        log.warn(" ALERT BREACH! Stock: {}, Price: ₹{}, Threshold: ₹{}, Direction: {}",
                stock.getSymbol(), tickEvent.getPrice(), alert.getThresholdValue(), alert.getDirection());
        // Calculate percentage move from reference price
        BigDecimal percentageMove = BigDecimal.ZERO;
        if (alert.getReferencePrice() != null && alert.getReferencePrice().compareTo(BigDecimal.ZERO) > 0) {
            percentageMove = tickEvent.getPrice().subtract(alert.getReferencePrice())
                    .divide(alert.getReferencePrice(), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        }
        // Record breach in alert_events table
        AlertEvent event = AlertEvent.builder()
                .alertDefinition(alert)
                .truggeredPrice(tickEvent.getPrice())
                .percentageMove(percentageMove)
                .triggeredAt(Instant.now())
                .eventSourceId(UUID.randomUUID())
                .build();
        alertEventRepository.save(event);
        // Update alert definition's lastTriggeredAt
        alert.setLastTriggeredAt(Instant.now());
        alert.setActive(false);
        alertDefinitionRepository.save(alert);
        // Dispatch AlertBreachEvent to RabbitMQ
        AlertBreachEvent breachEvent = AlertBreachEvent.builder()
                .alertEventId(event.getId())
                .alertId(alert.getId())
                .userId(alert.getUser().getId())
                .userEmail(alert.getUser().getEmail())
                .symbol(stock.getSymbol())
                .direction(alert.getDirection())
                .thresholdValue(alert.getThresholdValue())
                .triggerPrice(tickEvent.getPrice())
                .triggeredAt(Instant.now())
                .build();
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.ALERTS_EXCHANGE,
                RabbitMQConfig.ALERTS_ROUTING_KEY,
                breachEvent
        );
        log.info("Dispatched AlertBreachEvent to RabbitMQ for alert ID: {}", alert.getId());
    }


}
