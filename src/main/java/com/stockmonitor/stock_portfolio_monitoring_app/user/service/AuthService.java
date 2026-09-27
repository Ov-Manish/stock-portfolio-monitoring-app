package com.stockmonitor.stock_portfolio_monitoring_app.user.service;

import com.stockmonitor.stock_portfolio_monitoring_app.activity.entity.Activity;
import com.stockmonitor.stock_portfolio_monitoring_app.activity.repository.ActivityRepository;
import com.stockmonitor.stock_portfolio_monitoring_app.config.JwtService;
import com.stockmonitor.stock_portfolio_monitoring_app.notification.service.EmailService;
import com.stockmonitor.stock_portfolio_monitoring_app.portfolio.entity.Portfolio;
import com.stockmonitor.stock_portfolio_monitoring_app.portfolio.repository.PortfolioRepository;
import com.stockmonitor.stock_portfolio_monitoring_app.user.dto.AuthResponse;
import com.stockmonitor.stock_portfolio_monitoring_app.user.dto.LoginRequest;
import com.stockmonitor.stock_portfolio_monitoring_app.user.dto.RefreshTokenRequest;
import com.stockmonitor.stock_portfolio_monitoring_app.user.dto.RegisterRequest;
import com.stockmonitor.stock_portfolio_monitoring_app.user.entity.RefreshToken;
import com.stockmonitor.stock_portfolio_monitoring_app.user.entity.User;
import com.stockmonitor.stock_portfolio_monitoring_app.user.repository.RefreshTokenRepository;
import com.stockmonitor.stock_portfolio_monitoring_app.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PortfolioRepository portfolioRepository;
    private final ActivityRepository activityRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailService emailService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new IllegalArgumentException("Email is already registered: " + normalizedEmail);
        }

        String verificationToken = UUID.randomUUID().toString();
        Instant expiry = Instant.now().plus(24, ChronoUnit.HOURS);

        User user = User.builder()
                .name(request.getName().trim())
                .email(normalizedEmail)
                .phone(request.getPhone().trim())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .isVerified(false)
                .verificationToken(verificationToken)
                .verificationTokenExpiry(expiry)
                .build();

        User savedUser = userRepository.save(user);

        Portfolio defaultPortfolio = Portfolio.builder()
                .user(savedUser)
                .name("Default Portfolio")
                .build();
        portfolioRepository.save(defaultPortfolio);

        // Dispatch verification email to Mailpit
        emailService.sendVerificationEmail(savedUser.getEmail(), savedUser.getName(), verificationToken);

        Activity registerActivity = Activity.builder()
                .user(savedUser)
                .type("USER_REGISTERED")
                .message("User account created successfully. Verification email sent.")
                .build();
        activityRepository.save(registerActivity);

        String accessToken = jwtService.generateAccessToken(savedUser.getId(), savedUser.getEmail());
        String refreshToken = createAndSaveRefreshToken(savedUser);

        log.info("User registered successfully: id={}, email={}", savedUser.getId(), savedUser.getEmail());

        return AuthResponse.builder()
                .token(accessToken)
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtService.getAccessTokenExpirationMs() / 1000)
                .userId(savedUser.getId())
                .name(savedUser.getName())
                .email(savedUser.getEmail())
                .phone(savedUser.getPhone())
                .build();
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            log.warn("Failed login attempt for email: {}", normalizedEmail);
            throw new BadCredentialsException("Invalid email or password");
        }

        // Verify email status: if account is not verified and was assigned a verification token, block login
        if (!user.isVerified() && user.getVerificationToken() != null) {
            log.warn("Login attempt blocked for unverified email: {}", normalizedEmail);
            throw new BadCredentialsException("Email not verified. Please verify your email before logging in. Check your inbox for the verification link.");
        }

        // Revoke all prior refresh tokens for this user upon new login
        refreshTokenRepository.revokeAllByUser(user);

        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail());
        String refreshToken = createAndSaveRefreshToken(user);

        Activity loginActivity = Activity.builder()
                .user(user)
                .type("USER_LOGGED_IN")
                .message("User logged in successfully")
                .build();
        activityRepository.save(loginActivity);

        log.info("User logged in successfully: id={}, email={}", user.getId(), user.getEmail());

        return AuthResponse.builder()
                .token(accessToken)
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtService.getAccessTokenExpirationMs() / 1000)
                .userId(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .build();
    }

    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        String tokenStr = request.getRefreshToken().trim();

        RefreshToken storedToken = refreshTokenRepository.findByToken(tokenStr)
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));

        if (storedToken.isRevoked()) {
            log.warn("Attempt to use revoked refresh token for user: {}", storedToken.getUser().getEmail());
            throw new BadCredentialsException("Refresh token has been revoked. Please login again.");
        }

        if (storedToken.getExpiryDate().isBefore(Instant.now())) {
            log.warn("Expired refresh token used for user: {}", storedToken.getUser().getEmail());
            throw new BadCredentialsException("Refresh token has expired. Please login again.");
        }

        if (!jwtService.isTokenValid(storedToken.getToken())) {
            log.warn("Invalid JWT signature on refresh token for user: {}", storedToken.getUser().getEmail());
            throw new BadCredentialsException("Invalid refresh token signature.");
        }

        User user = storedToken.getUser();
        String newAccessToken = jwtService.generateAccessToken(user.getId(), user.getEmail());

        log.info("Issued new access token via refresh token for user: {}", user.getEmail());

        return AuthResponse.builder()
                .token(newAccessToken)
                .accessToken(newAccessToken)
                .refreshToken(storedToken.getToken())
                .tokenType("Bearer")
                .expiresIn(jwtService.getAccessTokenExpirationMs() / 1000)
                .userId(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .build();
    }

    @Transactional
    public void logout(RefreshTokenRequest request) {
        String tokenStr = request.getRefreshToken().trim();

        refreshTokenRepository.findByToken(tokenStr).ifPresent(token -> {
            token.setRevoked(true);
            refreshTokenRepository.save(token);

            Activity logoutActivity = Activity.builder()
                    .user(token.getUser())
                    .type("USER_LOGGED_OUT")
                    .message("User logged out and refresh token revoked")
                    .build();
            activityRepository.save(logoutActivity);

            log.info("User logged out successfully: email={}", token.getUser().getEmail());
        });
    }

    @Transactional
    public String verifyEmail(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Verification token must not be blank.");
        }

        User user = userRepository.findByVerificationToken(token.trim())
                .orElseThrow(() -> new IllegalArgumentException("Invalid or already verified email verification token."));

        if (user.getVerificationTokenExpiry() != null && user.getVerificationTokenExpiry().isBefore(Instant.now())) {
            throw new IllegalArgumentException("Verification token has expired. Please request a new verification email.");
        }

        user.setVerified(true);
        user.setVerificationToken(null);
        user.setVerificationTokenExpiry(null);
        userRepository.save(user);

        Activity verifyActivity = Activity.builder()
                .user(user)
                .type("EMAIL_VERIFIED")
                .message("User email verified successfully")
                .build();
        activityRepository.save(verifyActivity);

        log.info("Email verified successfully for user: {}", user.getEmail());
        return "Email verified successfully! You can now log in to your account.";
    }

    @Transactional
    public String resendVerification(String email) {
        String normalizedEmail = email.trim().toLowerCase();
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found with email: " + normalizedEmail));

        if (user.isVerified()) {
            return "Email is already verified. You can log in directly.";
        }

        String verificationToken = UUID.randomUUID().toString();
        user.setVerificationToken(verificationToken);
        user.setVerificationTokenExpiry(Instant.now().plus(24, ChronoUnit.HOURS));
        userRepository.save(user);

        emailService.sendVerificationEmail(user.getEmail(), user.getName(), verificationToken);

        log.info("Resent verification email to: {}", user.getEmail());
        return "Verification email resent successfully. Please check your inbox.";
    }

    private String createAndSaveRefreshToken(User user) {
        String tokenString = jwtService.generateRefreshToken(user.getId(), user.getEmail());
        Instant expiryDate = Instant.now().plusMillis(jwtService.getRefreshTokenExpirationMs());

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(tokenString)
                .expiryDate(expiryDate)
                .revoked(false)
                .build();

        refreshTokenRepository.save(refreshToken);
        return tokenString;
    }
}