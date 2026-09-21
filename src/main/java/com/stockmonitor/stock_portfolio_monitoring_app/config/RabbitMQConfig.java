package com.stockmonitor.stock_portfolio_monitoring_app.config;



import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {
    public static final String ALERT_QUEUES = "stock.alerts.queue";
    public static final String ALERTS_EXCHANGE = "stock.alerts.exchange";
    public static final String ALERTS_ROUTING_KEY  = "stock.alerts.key";

    @Bean
    public Queue alertQueue(){
        return QueueBuilder.durable(ALERT_QUEUES).build();
    }

    @Bean
    public DirectExchange alertExchange(){
        return new DirectExchange(ALERTS_EXCHANGE);
    }

    @Bean
    public Binding alertsBinding(Queue alertQueue , DirectExchange alertExchange){
        return BindingBuilder.bind(alertQueue).to(alertExchange).with(ALERTS_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter(){
        return new JacksonJsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory){
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }
}
