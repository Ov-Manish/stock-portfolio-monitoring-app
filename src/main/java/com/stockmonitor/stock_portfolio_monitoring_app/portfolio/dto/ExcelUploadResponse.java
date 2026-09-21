package com.stockmonitor.stock_portfolio_monitoring_app.portfolio.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExcelUploadResponse {
    private int totalRows;
    private int successCount;
    private int failedCount;
    private List<HoldingResponse> importedHoldings;
    private List<ExcelRowError> errors;
}