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
}
