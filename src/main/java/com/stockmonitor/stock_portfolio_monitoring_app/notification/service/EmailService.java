package com.stockmonitor.stock_portfolio_monitoring_app.notification.service;

import com.stockmonitor.stock_portfolio_monitoring_app.constants.AlertDirection;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    public void sendAlertBreachEmail(
            String toEmail,
            String symbol,
            AlertDirection direction,
            BigDecimal triggerPrice,
            BigDecimal thresholdValue
    ) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom("alerts@stockmonitor.com");
            helper.setTo(toEmail);
            helper.setSubject("Stock Alert: " + symbol + " has breached your " + direction + " target!");

            String directionText = direction == AlertDirection.ABOVE ? "risen above" : "dropped below";
            String color = direction == AlertDirection.ABOVE ? "#16a34a" : "#dc2626";

            String htmlBody = """
                <!DOCTYPE html>
                <html>
                <body style="font-family: Arial, sans-serif; background-color: #f4f4f5; padding: 20px;">
                    <div style="max-width: 600px; margin: 0 auto; background: #ffffff; border-radius: 8px; padding: 24px; box-shadow: 0 2px 4px rgba(0,0,0,0.1);">
                        <h2 style="color: %s; margin-top: 0;">🚨 Price Alert Triggered!</h2>
                        <p>Hello,</p>
                        <p>Your price alert for <strong>%s</strong> has been triggered.</p>
                        <div style="background-color: #f8fafc; border-left: 4px solid %s; padding: 12px 16px; margin: 20px 0;">
                            <p style="margin: 4px 0;"><strong>Stock Symbol:</strong> %s</p>
                            <p style="margin: 4px 0;"><strong>Triggered Price:</strong> ₹%s</p>
                            <p style="margin: 4px 0;"><strong>Target Threshold:</strong> ₹%s (%s)</p>
                            <p style="margin: 4px 0;"><strong>Condition:</strong> Price has %s your threshold.</p>
                        </div>
                        <p style="color: #64748b; font-size: 12px; margin-top: 30px;">
                            This is an automated notification from your Stock Portfolio Monitoring App.
                        </p>
                    </div>
                </body>
                </html>
                """.formatted(
                    color,
                    symbol,
                    color,
                    symbol,
                    triggerPrice.toPlainString(),
                    thresholdValue.toPlainString(),
                    direction,
                    directionText
            );

            helper.setText(htmlBody, true);
            mailSender.send(message);

            log.info("Alert email successfully dispatched to {} for stock {}", toEmail, symbol);

        } catch (MessagingException e) {
            log.error("Failed to compose or send email to {}: {}", toEmail, e.getMessage());
            throw new RuntimeException("Email dispatch failed", e);
        }
    }

    public void sendVerificationEmail(String toEmail, String userName, String token) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom("auth@stockmonitor.com");
            helper.setTo(toEmail);
            helper.setSubject("Verify your Stock Portfolio Monitoring Account");

            String verificationUrl = "http://localhost:8080/api/v1/auth/verify-email?token=" + token;

            String htmlBody = """
                <!DOCTYPE html>
                <html>
                <body style="font-family: Arial, sans-serif; background-color: #f4f4f5; padding: 20px;">
                    <div style="max-width: 600px; margin: 0 auto; background: #ffffff; border-radius: 8px; padding: 24px; box-shadow: 0 2px 4px rgba(0,0,0,0.1);">
                        <h2 style="color: #2563eb; margin-top: 0;">Welcome to Stock Portfolio Monitor!</h2>
                        <p>Hello %s,</p>
                        <p>Thank you for signing up. Please verify your email address to activate your account and start monitoring your stocks.</p>
                        <div style="text-align: center; margin: 30px 0;">
                            <a href="%s" style="background-color: #2563eb; color: #ffffff; padding: 12px 24px; text-decoration: none; border-radius: 6px; font-weight: bold; display: inline-block;">Verify Email Address</a>
                        </div>
                        <p style="color: #64748b; font-size: 13px;">Or copy and paste this link into your browser or Postman:</p>
                        <p style="background: #f1f5f9; padding: 10px; border-radius: 4px; font-size: 12px; word-break: break-all; color: #334155;">%s</p>
                        <p style="color: #94a3b8; font-size: 12px; margin-top: 24px;">This verification link will expire in 24 hours.</p>
                    </div>
                </body>
                </html>
                """.formatted(userName, verificationUrl, verificationUrl);

            helper.setText(htmlBody, true);
            mailSender.send(message);
            log.info("Sent verification email to: {}", toEmail);
        } catch (MessagingException e) {
            log.error("Failed to send verification email to {}: {}", toEmail, e.getMessage());
        }
    }
}
