package com.stockmonitor.stock_portfolio_monitoring_app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class StockPortfolioMonitoringAppApplication {

	public static void main(String[] args) {
		SpringApplication.run(StockPortfolioMonitoringAppApplication.class, args);
	}

}
