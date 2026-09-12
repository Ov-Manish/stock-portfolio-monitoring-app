package com.stockmonitor.stock_portfolio_monitoring_app.user.service;

import com.stockmonitor.stock_portfolio_monitoring_app.activity.entity.Activity;
import com.stockmonitor.stock_portfolio_monitoring_app.activity.repository.ActivityRepository;
import com.stockmonitor.stock_portfolio_monitoring_app.config.JwtService;
import com.stockmonitor.stock_portfolio_monitoring_app.portfolio.entity.Portfolio;
import com.stockmonitor.stock_portfolio_monitoring_app.portfolio.repository.PortfolioRepository;
import com.stockmonitor.stock_portfolio_monitoring_app.user.dto.AuthResponse;
import com.stockmonitor.stock_portfolio_monitoring_app.user.dto.LoginRequest;
import com.stockmonitor.stock_portfolio_monitoring_app.user.dto.RegisterRequest;
import com.stockmonitor.stock_portfolio_monitoring_app.user.entity.User;
import com.stockmonitor.stock_portfolio_monitoring_app.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PortfolioRepository portfolioRepository;
    private final ActivityRepository activityRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new IllegalArgumentException("Email is already registered: " + normalizedEmail);
        }

        User user = User.builder()
                .name(request.getName().trim())
                .email(normalizedEmail)
                .phone(request.getPhone().trim())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .build();

        User savedUser = userRepository.save(user);

        Portfolio defaultPortfolio = Portfolio.builder()
                .user(savedUser)
                .name("Default Portfolio")
                .build();
        portfolioRepository.save(defaultPortfolio);

        Activity registerActivity = Activity.builder()
                .user(savedUser)
                .type("USER_REGISTERED")
                .message("User account created successfully")
                .build();
        activityRepository.save(registerActivity);

        String token = jwtService.generateToken(savedUser.getId(), savedUser.getEmail());

        return AuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .userId(savedUser.getId())
                .name(savedUser.getName())
                .email(savedUser.getEmail())
                .phone(savedUser.getPhone())
                .build();
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password");
        }

        String token = jwtService.generateToken(user.getId(), user.getEmail());

        return AuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .userId(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .build();
    }
}