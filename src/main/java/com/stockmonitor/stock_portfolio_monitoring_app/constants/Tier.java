package com.stockmonitor.stock_portfolio_monitoring_app.constants;

public enum Tier {
    AUTH,    // Login, registration, password resets (10 req/min)
    HEAVY,   // File uploads, heavy exports (5 req/min)
    PUBLIC   // Public browsing, stock lists (60 req/min)
}
