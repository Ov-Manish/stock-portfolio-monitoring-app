package com.stockmonitor.stock_portfolio_monitoring_app.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class JwtService {

    @Value("${spring.jwt.secret:defaultSecretKeyForStockPortfolioMonitoringAppMustBe32BytesLong!}")
    private String secretKey;

    @Value("${spring.jwt.access-token-expiration-ms:900000}")
    private long accessTokenExpirationMs; // 15 minutes by default

    @Value("${spring.jwt.refresh-token-expiration-ms:604800000}")
    private long refreshTokenExpirationMs; // 7 days by default

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(UUID userId, String email) {
        return buildToken(userId, email, accessTokenExpirationMs, "ACCESS");
    }

    public String generateRefreshToken(UUID userId, String email) {
        return buildToken(userId, email, refreshTokenExpirationMs, "REFRESH");
    }

    // Kept for backward compatibility
    public String generateToken(UUID userId, String email) {
        return generateAccessToken(userId, email);
    }

    private String buildToken(UUID userId, String email, long expirationMs, String tokenType) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId.toString());
        claims.put("email", email);
        claims.put("type", tokenType);

        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .claims(claims)
                .subject(email)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    public long getAccessTokenExpirationMs() {
        return accessTokenExpirationMs;
    }

    public long getRefreshTokenExpirationMs() {
        return refreshTokenExpirationMs;
    }

    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractEmail(String token) {
        return extractAllClaims(token).getSubject();
    }

    public UUID extractUserId(String token) {
        String userIdStr = extractAllClaims(token).get("userId", String.class);
        return UUID.fromString(userIdStr);
    }

    public boolean isTokenValid(String token) {
        try {
            Date expiration = extractAllClaims(token).getExpiration();
            return !expiration.before(new Date());
        } catch (Exception e) {
            return false;
        }
    }
}