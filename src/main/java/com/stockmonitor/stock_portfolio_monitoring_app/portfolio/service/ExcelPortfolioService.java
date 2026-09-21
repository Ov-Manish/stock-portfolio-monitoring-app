package com.stockmonitor.stock_portfolio_monitoring_app.portfolio.service;

import com.stockmonitor.stock_portfolio_monitoring_app.portfolio.dto.BuyStockRequest;
import com.stockmonitor.stock_portfolio_monitoring_app.portfolio.dto.ExcelRowError;
import com.stockmonitor.stock_portfolio_monitoring_app.portfolio.dto.ExcelUploadResponse;
import com.stockmonitor.stock_portfolio_monitoring_app.portfolio.dto.HoldingResponse;
import com.stockmonitor.stock_portfolio_monitoring_app.stock.entity.Stock;
import com.stockmonitor.stock_portfolio_monitoring_app.stock.repository.StockRepository;
import com.stockmonitor.stock_portfolio_monitoring_app.stock.service.StockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExcelPortfolioService {

    private final StockRepository stockRepository;
    private final StockService stockService;
    private final PortfolioService portfolioService;

    public ExcelUploadResponse importPortfolioFromExcel(MultipartFile file, UUID userId) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded Excel file is empty");
        }

        List<HoldingResponse> importedHoldings = new ArrayList<>();
        List<ExcelRowError> errors = new ArrayList<>();
        int totalRows = 0;

        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(inputStream)) {

            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null || sheet.getLastRowNum() < 1) {
                throw new IllegalArgumentException("Excel sheet has no data rows");
            }

            // 1. Identify Column Indexes from Header Row (Row 0)
            Row headerRow = sheet.getRow(0);
            int symbolCol = -1;
            int qtyCol = -1;
            int priceCol = -1;

            for (Cell cell : headerRow) {
                String header = getCellStringValue(cell).trim().toLowerCase();
                if (header.contains("symbol") || header.contains("ticker") || header.contains("stock")) {
                    symbolCol = cell.getColumnIndex();
                } else if (header.contains("qty") || header.contains("quantity") || header.contains("shares")) {
                    qtyCol = cell.getColumnIndex();
                } else if (header.contains("price") || header.contains("cost") || header.contains("avg")) {
                    priceCol = cell.getColumnIndex();
                }
            }

            if (symbolCol == -1 || qtyCol == -1 || priceCol == -1) {
                throw new IllegalArgumentException(
                        "Excel sheet must contain headers for Symbol, Quantity, and Buy Price");
            }

            // 2. Iterate Data Rows (Row 1 to last)
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null || isRowEmpty(row)) {
                    continue;
                }

                totalRows++;
                int displayRow = r + 1; // 1-indexed for user readability
                String symbol = null;

                try {
                    symbol = getCellStringValue(row.getCell(symbolCol)).trim().toUpperCase();
                    if (symbol.isEmpty()) {
                        errors.add(new ExcelRowError(displayRow, "UNKNOWN", "Symbol cannot be empty"));
                        continue;
                    }

                    BigDecimal quantity = getCellNumericValue(row.getCell(qtyCol));
                    if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
                        errors.add(new ExcelRowError(displayRow, symbol, "Quantity must be greater than 0"));
                        continue;
                    }

                    BigDecimal buyPrice = getCellNumericValue(row.getCell(priceCol));
                    if (buyPrice == null || buyPrice.compareTo(BigDecimal.ZERO) <= 0) {
                        errors.add(new ExcelRowError(displayRow, symbol, "Buy price must be greater than 0"));
                        continue;
                    }

                    // 3. Resolve Stock (or Auto-Discover from Yahoo Finance)
                    Stock stock = resolveStock(symbol);

                    // 4. Buy Stock into Portfolio (with Weighted Average Cost)
                    BuyStockRequest buyRequest = BuyStockRequest.builder()
                            .userId(userId)
                            .stockId(stock.getId())
                            .quantity(quantity)
                            .buyPrice(buyPrice)
                            .build();

                    HoldingResponse holding = portfolioService.buyStock(buyRequest);
                    importedHoldings.add(holding);

                    // Optional polite pause to prevent Yahoo rate-limiting if multiple new stocks are discovered
                    Thread.sleep(100);

                } catch (Exception e) {
                    log.error("Failed to import row {}: {}", displayRow, e.getMessage());
                    errors.add(new ExcelRowError(displayRow, symbol != null ? symbol : "UNKNOWN", e.getMessage()));
                }
            }

        } catch (Exception e) {
            log.error("Error reading Excel workbook: {}", e.getMessage());
            throw new RuntimeException("Failed to process Excel file: " + e.getMessage(), e);
        }

        return ExcelUploadResponse.builder()
                .totalRows(totalRows)
                .successCount(importedHoldings.size())
                .failedCount(errors.size())
                .importedHoldings(importedHoldings)
                .errors(errors)
                .build();
    }

    private Stock resolveStock(String symbol) {
        List<Stock> stocks = stockRepository.findAllBySymbol(symbol);
        if (!stocks.isEmpty()) {
            return stocks.get(0);
        }

        // Auto-discover from Yahoo Finance if not in DB!
        log.info("Auto-discovering stock '{}' during Excel import...", symbol);
        stockService.searchStocks(symbol);

        return stockRepository.findAllBySymbol(symbol).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Stock not found or delisted: " + symbol));
    }

    private String getCellStringValue(Cell cell) {
        if (cell == null) return "";
        if (cell.getCellType() == CellType.STRING) {
            return cell.getStringCellValue();
        } else if (cell.getCellType() == CellType.NUMERIC) {
            return String.valueOf((long) cell.getNumericCellValue());
        }
        return "";
    }

    private BigDecimal getCellNumericValue(Cell cell) {
        if (cell == null) return null;
        if (cell.getCellType() == CellType.NUMERIC) {
            return BigDecimal.valueOf(cell.getNumericCellValue());
        } else if (cell.getCellType() == CellType.STRING) {
            try {
                return new BigDecimal(cell.getStringCellValue().trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private boolean isRowEmpty(Row row) {
        for (int c = row.getFirstCellNum(); c < row.getLastCellNum(); c++) {
            Cell cell = row.getCell(c);
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                return false;
            }
        }
        return true;
    }
}