package com.stockmonitor.stock_portfolio_monitoring_app.exception;

public class InsufficientQuantityException extends RuntimeException {
    public InsufficientQuantityException(String message) {
        super(message);
    }
}
