package com.stockmonitor.stock_portfolio_monitoring_app.activity.controller;

import com.stockmonitor.stock_portfolio_monitoring_app.activity.dto.ActivityResponse;
import com.stockmonitor.stock_portfolio_monitoring_app.activity.service.ActivityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/activities")
@RequiredArgsConstructor
public class ActivityController {

    private final ActivityService activityService;

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<ActivityResponse>> getUserActivities(@PathVariable UUID userId) {
        List<ActivityResponse> activities = activityService.getUserActivities(userId);
        return ResponseEntity.ok(activities);
    }
}
