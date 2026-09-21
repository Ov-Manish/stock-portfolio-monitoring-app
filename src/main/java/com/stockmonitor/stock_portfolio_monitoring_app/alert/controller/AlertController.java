package com.stockmonitor.stock_portfolio_monitoring_app.alert.controller;


import com.stockmonitor.stock_portfolio_monitoring_app.alert.dto.AlertDefinitionResponse;
import com.stockmonitor.stock_portfolio_monitoring_app.alert.dto.AlertEventResponse;
import com.stockmonitor.stock_portfolio_monitoring_app.alert.dto.CreateAlertRequest;
import com.stockmonitor.stock_portfolio_monitoring_app.alert.service.AlertService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService alertService;


    @PostMapping
    public ResponseEntity<AlertDefinitionResponse> createAlert(@Valid @RequestBody CreateAlertRequest request){
        AlertDefinitionResponse response = alertService.createAlert(request);
        return new ResponseEntity<>(response , HttpStatus.CREATED);
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<AlertDefinitionResponse>> getUserAlerts( @PathVariable UUID userId){
        List<AlertDefinitionResponse> alerts = alertService.getUserAlerts(userId);
        return ResponseEntity.ok(alerts);
    }

    @PatchMapping("/{alertId}/toggle")
    public ResponseEntity<AlertDefinitionResponse> toggleAlert(@PathVariable UUID alertId){
        AlertDefinitionResponse response = alertService.toggleAlert(alertId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{alertId}/history")
    public ResponseEntity<List<AlertEventResponse>> getAlertHistory(@PathVariable UUID alertId){
        List<AlertEventResponse> alertHistoryResponses = alertService.getAlertHistory(alertId);

        return ResponseEntity.ok(alertHistoryResponses);
    }

    @DeleteMapping("/{alertId}")
    public ResponseEntity<String> deleteAlert(@PathVariable UUID alertId) {
        alertService.deleteAlert(alertId);
        return ResponseEntity.ok("Alert deleted successfully");
    }

}
