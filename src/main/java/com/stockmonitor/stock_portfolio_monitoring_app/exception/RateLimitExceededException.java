package com.stockmonitor.stock_portfolio_monitoring_app.exception;

public class RateLimitExceededException extends RuntimeException {
    public RateLimitExceededException(String message) {
        super(message);
    }
}
