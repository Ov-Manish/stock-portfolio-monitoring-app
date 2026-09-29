package com.stockmonitor.stock_portfolio_monitoring_app.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.time.Instant;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    private Instant timeStamp;
    private int status;
    private String error ;
    private String message;
    private String path;
    private Map<String , String> validationErrors;
}
