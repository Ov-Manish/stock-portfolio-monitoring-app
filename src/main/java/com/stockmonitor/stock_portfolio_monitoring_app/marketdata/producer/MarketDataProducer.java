package com.stockmonitor.stock_portfolio_monitoring_app.marketdata.producer;

import com.stockmonitor.stock_portfolio_monitoring_app.config.KafkaConfig;
import com.stockmonitor.stock_portfolio_monitoring_app.marketdata.dto.MarketTickEvent;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MarketDataProducer {
    private final KafkaTemplate<String , Object> kafkaTemplate;
    public void publishMarketTick(MarketTickEvent event){
        log.info("Publishing tick to Kafka for {}: Price = ₹{}" , event.getSymbol() , event.getPrice());
        kafkaTemplate.send(KafkaConfig.MARKET_TICKS_TOPIC , event.getSymbol() , event);
    }
}
