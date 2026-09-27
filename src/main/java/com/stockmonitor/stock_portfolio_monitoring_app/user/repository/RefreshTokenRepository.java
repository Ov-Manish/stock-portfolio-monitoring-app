package com.stockmonitor.stock_portfolio_monitoring_app.user.repository;

import com.stockmonitor.stock_portfolio_monitoring_app.user.entity.RefreshToken;
import com.stockmonitor.stock_portfolio_monitoring_app.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    // Find token entity to validate existence, expiry, and revocation
    Optional<RefreshToken> findByToken(String token);

    // Revoke all existing refresh tokens for a user (called during new login or logout)
    @Modifying
    @Query("UPDATE RefreshToken r SET r.revoked = true WHERE r.user = :user")
    void revokeAllByUser(@Param("user") User user);

    // Optional hard-delete helper if you wish to purge tokens
    @Modifying
    @Query("DELETE FROM RefreshToken r WHERE r.user = :user")
    void deleteByUser(@Param("user") User user);
}