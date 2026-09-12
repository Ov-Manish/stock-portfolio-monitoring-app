package com.stockmonitor.stock_portfolio_monitoring_app.alert.repository;

import com.stockmonitor.stock_portfolio_monitoring_app.alert.entity.AlertDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AlertDefinitionRepository extends JpaRepository<AlertDefinition , UUID> {

    List<AlertDefinition> findByUserId(UUID userId);
    List<AlertDefinition> findByStockIdAndIsActiveTrue(UUID stock_id);

}
