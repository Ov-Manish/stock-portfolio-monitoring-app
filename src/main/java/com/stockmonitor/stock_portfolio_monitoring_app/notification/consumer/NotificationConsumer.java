package com.stockmonitor.stock_portfolio_monitoring_app.notification.consumer;

import com.stockmonitor.stock_portfolio_monitoring_app.alert.entity.AlertEvent;
import com.stockmonitor.stock_portfolio_monitoring_app.alert.repository.AlertEventRepository;
import com.stockmonitor.stock_portfolio_monitoring_app.config.RabbitMQConfig;
import com.stockmonitor.stock_portfolio_monitoring_app.marketdata.dto.AlertBreachEvent;
import com.stockmonitor.stock_portfolio_monitoring_app.notification.entity.Notification;
import com.stockmonitor.stock_portfolio_monitoring_app.notification.repository.NotificationRepository;
import com.stockmonitor.stock_portfolio_monitoring_app.notification.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationConsumer {

    private final NotificationRepository notificationRepository;
    private final AlertEventRepository alertEventRepository;
    private final EmailService emailService;

    @RabbitListener(queues = RabbitMQConfig.ALERT_QUEUES)
    public void handleAlertBreach(AlertBreachEvent event) {
        log.info("NotificationConsumer received AlertBreachEvent for alert ID: {}, symbol: {}",
                event.getAlertId(), event.getSymbol());

        // 1. Deduplication check:-> Has an email notification already been created for this evetn
        if (event.getAlertEventId() != null &&
                notificationRepository.existsByAlertEventIdAndChannel(event.getAlertEventId(), "EMAIL")) {
            log.warn("Notification already exists for alertEventId: {}. Skipping.", event.getAlertEventId());
            return;
        }

        // 2. Resolve AlertEvent entity
        AlertEvent alertEvent = null;
        if (event.getAlertEventId() != null) {
            alertEvent = alertEventRepository.findById(event.getAlertEventId()).orElse(null);
        }

        if (alertEvent == null) {
            log.warn("AlertEvent not found for ID: {}. Cannot persist notification.", event.getAlertEventId());
            return;
        }

        // 3. Create Notification record with PENDING status
        Notification notification = Notification.builder()
                .alertEvent(alertEvent)
                .channel("EMAIL")
                .status("PENDING")
                .attemptCount(1)
                .build();

        notification = notificationRepository.save(notification);

        // 4. Dispatch Email to User
        try {
            emailService.sendAlertBreachEmail(
                    event.getUserEmail(),
                    event.getSymbol(),
                    event.getDirection(),
                    event.getTriggerPrice(),
                    event.getThresholdValue()
            );

            notification.setStatus("SENT");
            notification.setSentAt(Instant.now());
            notificationRepository.save(notification);

            log.info("Notification marked as SENT for user: {}", event.getUserEmail());

        } catch (Exception e) {
            log.error("Failed to deliver alert email to {}: {}", event.getUserEmail(), e.getMessage());
            notification.setStatus("FAILED");
            notification.setErrorMessage(e.getMessage());
            notificationRepository.save(notification);
        }
    }
}
