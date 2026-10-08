package com.stockmonitor.stock_portfolio_monitoring_app.rateLimit;

import com.stockmonitor.stock_portfolio_monitoring_app.constants.Tier;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimited {
    Tier tier() default Tier.PUBLIC; // default value
}
