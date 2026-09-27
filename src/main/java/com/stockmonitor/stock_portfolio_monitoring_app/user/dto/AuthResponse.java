package com.stockmonitor.stock_portfolio_monitoring_app.user.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthResponse {
    private String token;
    private String tokenType;
    private String accessToken;  // 15-minute short-lived token
    private String refreshToken;
    private Long expiresIn;
    private UUID userId;
    private String name;
    private String email;
    private String phone;
}