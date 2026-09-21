package com.stockmonitor.stock_portfolio_monitoring_app.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    public static final String MARKET_TICKS_TOPIC = "market-ticks";

    @Bean
    public NewTopic makertTicksTopic(){
        return TopicBuilder.name(MARKET_TICKS_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
