package com.stockmonitor.stock_portfolio_monitoring_app.alert.repository;

import com.stockmonitor.stock_portfolio_monitoring_app.alert.entity.AlertEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AlertEventRepository extends JpaRepository<AlertEvent, UUID> {
    List<AlertEvent> findByAlertDefinitionIdOrderByTriggeredAtDesc(UUID alertDefinitionId);

    boolean existsByEventSourceId(String eventSourceId);
}