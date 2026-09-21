package com.stockmonitor.stock_portfolio_monitoring_app.alert.dto;


import com.stockmonitor.stock_portfolio_monitoring_app.constants.AlertDirection;
import com.stockmonitor.stock_portfolio_monitoring_app.stock.entity.Stock;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateAlertRequest {

    @NotNull(message = "User Can not be NULL")
    private UUID userId;

    @NotNull(message = "Stock Id Can not be null")
    private UUID stockId;

    @NotNull(message = "Direction can not be null")
    private AlertDirection direction;

    @NotNull(message = "Threshold value cannot be null")
    @DecimalMin(value = "0.0001" ,message = "Threshold Should be greater then 0")
    private BigDecimal thresholdValue;

    @Builder.Default
    private Integer cooldownMinutes  = 60;  // default value
}
