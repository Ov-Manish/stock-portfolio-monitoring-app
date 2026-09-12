package com.stockmonitor.stock_portfolio_monitoring_app.notification.repository;

import com.stockmonitor.stock_portfolio_monitoring_app.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    List<Notification> findByAlertEventId(UUID alertEventId);

    boolean existsByAlertEventIdAndChannel(UUID alertEventId, String channel);
}