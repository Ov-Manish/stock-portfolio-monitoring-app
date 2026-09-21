package com.stockmonitor.stock_portfolio_monitoring_app.alert.service;


import com.stockmonitor.stock_portfolio_monitoring_app.alert.dto.AlertDefinitionResponse;
import com.stockmonitor.stock_portfolio_monitoring_app.alert.dto.AlertEventResponse;
import com.stockmonitor.stock_portfolio_monitoring_app.alert.dto.CreateAlertRequest;
import com.stockmonitor.stock_portfolio_monitoring_app.alert.entity.AlertDefinition;
import com.stockmonitor.stock_portfolio_monitoring_app.alert.entity.AlertEvent;
import com.stockmonitor.stock_portfolio_monitoring_app.alert.repository.AlertDefinitionRepository;
import com.stockmonitor.stock_portfolio_monitoring_app.alert.repository.AlertEventRepository;
import com.stockmonitor.stock_portfolio_monitoring_app.constants.AlertScope;
import com.stockmonitor.stock_portfolio_monitoring_app.marketdata.entity.MarketPrice;
import com.stockmonitor.stock_portfolio_monitoring_app.marketdata.repository.MarketPriceRepository;
import com.stockmonitor.stock_portfolio_monitoring_app.notification.entity.Notification;
import com.stockmonitor.stock_portfolio_monitoring_app.notification.repository.NotificationRepository;
import com.stockmonitor.stock_portfolio_monitoring_app.stock.entity.Stock;
import com.stockmonitor.stock_portfolio_monitoring_app.stock.repository.StockRepository;
import com.stockmonitor.stock_portfolio_monitoring_app.user.entity.User;
import com.stockmonitor.stock_portfolio_monitoring_app.user.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AlertService {

    private final AlertDefinitionRepository alertDefinitionRepository;
    private final AlertEventRepository alertEventRepository;
    private final StockRepository stockRepository;
    private final UserRepository userRepository;
    private final MarketPriceRepository marketPriceRepository;
    private final NotificationRepository notificationRepository;


    @Transactional
    public AlertDefinitionResponse createAlert(CreateAlertRequest request){
        User user = userRepository.findById(request.getUserId()).orElseThrow( ()-> new RuntimeException("User not Found"));
        Stock stock = stockRepository.findById(request.getStockId()).orElseThrow(()-> new RuntimeException("Stock not Found"));

        MarketPrice marketPrice = marketPriceRepository.findById(stock.getId()).orElse(null);

        BigDecimal refPrice = (marketPrice != null) ? marketPrice.getPrice() : BigDecimal.ZERO;

        AlertDefinition alert = AlertDefinition.builder()
                .user(user)
                .stock(stock)
                .scope(AlertScope.STOCK)
                .direction(request.getDirection())
                .thresholdValue(request.getThresholdValue())
                .cooldownMinutes(request.getCooldownMinutes())
                .referencePrice(refPrice)
                .isActive(true)
                .build();


        AlertDefinition savedAlert = alertDefinitionRepository.save(alert);
    return  mapToResponse(savedAlert);
    }


    public AlertDefinitionResponse mapToResponse(AlertDefinition alertDefinition){
        AlertDefinitionResponse alertResponse = AlertDefinitionResponse.builder()
                .alertId(alertDefinition.getId())
                .userId(alertDefinition.getUser().getId())
                .stockId(alertDefinition.getStock().getId())
                .symbol(alertDefinition.getStock().getSymbol())
                .exchange(alertDefinition.getStock().getExchange())
                .direction(alertDefinition.getDirection())
                .thresholdValue(alertDefinition.getThresholdValue())
                .referencePrice(alertDefinition.getReferencePrice())
                .isActive(alertDefinition.isActive())
                .cooldownMinutes(alertDefinition.getCooldownMinutes())
                .lastTriggeredAt(alertDefinition.getLastTriggeredAt())
                .createdAt(alertDefinition.getCreatedAt())
                .build();

        return alertResponse;
    }


    @Transactional()
    public List<AlertDefinitionResponse> getUserAlerts(UUID userId){
        return alertDefinitionRepository.findByUserId(userId).stream()
                .map(this::mapToResponse)
                .toList();
    }


    @Transactional
    public AlertDefinitionResponse toggleAlert(UUID alertId){
        AlertDefinition alert = alertDefinitionRepository.findById(alertId)
                .orElseThrow(() -> new IllegalArgumentException("Alert not found with id: " + alertId));

        alert.setActive(!alert.isActive());

        AlertDefinition updated = alertDefinitionRepository.save(alert);

        return mapToResponse(updated);
    }


    @Transactional
    public List<AlertEventResponse> getAlertHistory(UUID alertDefinitionId){
        List<AlertEvent> events = alertEventRepository.findByAlertDefinitionIdOrderByTriggeredAtDesc(alertDefinitionId);

        List<AlertEventResponse> responses= new ArrayList<>();
        for (AlertEvent event : events){
            AlertEventResponse response = AlertEventResponse.builder()
                    .eventId(event.getId())
                    .alertDefinitionId(event.getAlertDefinition().getId())
                    .triggeredPrice(event.getTruggeredPrice())
                    .percentageMove(event.getPercentageMove())
                    .triggeredAt(event.getTriggeredAt())
                    .eventSourceId(event.getEventSourceId())
                    .build();

            responses.add(response);
        }

        return responses;
    }

    @Transactional
    public void deleteAlert(UUID alertId) {
        AlertDefinition alert = alertDefinitionRepository.findById(alertId)
                .orElseThrow(() -> new IllegalArgumentException("Alert not found with id: " + alertId));

        List<AlertEvent> events = alertEventRepository.findByAlertDefinitionIdOrderByTriggeredAtDesc(alertId);
        if (!events.isEmpty()) {
            for (AlertEvent event : events) {
                List<Notification> notifications = notificationRepository.findByAlertEventId(event.getId());
                if (!notifications.isEmpty()) {
                    notificationRepository.deleteAll(notifications);
                }
            }
            alertEventRepository.deleteAll(events);
        }

        alertDefinitionRepository.delete(alert);
    }

}
