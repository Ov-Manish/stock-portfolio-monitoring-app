package com.stockmonitor.stock_portfolio_monitoring_app.activity.service;

import com.stockmonitor.stock_portfolio_monitoring_app.activity.dto.ActivityResponse;
import com.stockmonitor.stock_portfolio_monitoring_app.activity.entity.Activity;
import com.stockmonitor.stock_portfolio_monitoring_app.activity.repository.ActivityRepository;
import com.stockmonitor.stock_portfolio_monitoring_app.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityService {

    private final ActivityRepository activityRepository;

    @Transactional(readOnly = true)
    public List<ActivityResponse> getUserActivities(UUID userId) {
        log.info("Fetching activity feed for user ID: {}", userId);
        List<Activity> activities = activityRepository.findByUserIdOrderByCreatedAtDesc(userId);
        return activities.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public void logActivity(User user, String type, String message, String metadata) {
        Activity activity = Activity.builder()
                .user(user)
                .type(type)
                .message(message)
                .metadata(metadata)
                .build();
        activityRepository.save(activity);
        log.info("Logged activity [{}] for user: {}", type, user.getEmail());
    }

    private ActivityResponse mapToResponse(Activity activity) {
        return ActivityResponse.builder()
                .id(activity.getId())
                .userId(activity.getUser() != null ? activity.getUser().getId() : null)
                .type(activity.getType())
                .message(activity.getMessage())
                .metadata(activity.getMetadata())
                .createdAt(activity.getCreatedAt())
                .build();
    }
}
