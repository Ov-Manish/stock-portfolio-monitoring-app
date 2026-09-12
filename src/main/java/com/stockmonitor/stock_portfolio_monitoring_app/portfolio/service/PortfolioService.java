package com.stockmonitor.stock_portfolio_monitoring_app.portfolio.service;

import com.stockmonitor.stock_portfolio_monitoring_app.activity.entity.Activity;
import com.stockmonitor.stock_portfolio_monitoring_app.activity.repository.ActivityRepository;
import com.stockmonitor.stock_portfolio_monitoring_app.marketdata.entity.MarketPrice;
import com.stockmonitor.stock_portfolio_monitoring_app.marketdata.repository.MarketPriceRepository;
import com.stockmonitor.stock_portfolio_monitoring_app.portfolio.dto.BuyStockRequest;
import com.stockmonitor.stock_portfolio_monitoring_app.portfolio.dto.HoldingResponse;
import com.stockmonitor.stock_portfolio_monitoring_app.portfolio.dto.PortfolioSummaryResponse;
import com.stockmonitor.stock_portfolio_monitoring_app.portfolio.dto.SellStockRequest;
import com.stockmonitor.stock_portfolio_monitoring_app.portfolio.entity.Holding;
import com.stockmonitor.stock_portfolio_monitoring_app.portfolio.entity.Portfolio;
import com.stockmonitor.stock_portfolio_monitoring_app.portfolio.repository.HoldingRepository;
import com.stockmonitor.stock_portfolio_monitoring_app.portfolio.repository.PortfolioRepository;
import com.stockmonitor.stock_portfolio_monitoring_app.stock.entity.Stock;
import com.stockmonitor.stock_portfolio_monitoring_app.stock.repository.StockRepository;
import com.stockmonitor.stock_portfolio_monitoring_app.user.entity.User;
import com.stockmonitor.stock_portfolio_monitoring_app.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PortfolioService {

    private final PortfolioRepository portfolioRepository;
    private final HoldingRepository holdingRepository;
    private final StockRepository stockRepository;
    private final UserRepository userRepository;
    private final MarketPriceRepository marketPriceRepository;
    private final ActivityRepository activityRepository;

    @Transactional
    public HoldingResponse buyStock(BuyStockRequest request) {
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + request.getUserId()));

        Portfolio portfolio = portfolioRepository.findByUserId(request.getUserId())
                .orElseGet(() -> portfolioRepository.save(Portfolio.builder().user(user).name("Default Portfolio").build()));

        Stock stock = stockRepository.findById(request.getStockId())
                .orElseThrow(() -> new IllegalArgumentException("Stock not found with id: " + request.getStockId()));

        Optional<Holding> existingHoldingOpt = holdingRepository.findByPortfolioIdAndStockId(portfolio.getId(), stock.getId());

        Holding holding;
        if (existingHoldingOpt.isPresent()) {
            holding = existingHoldingOpt.get();
            BigDecimal existingQty = holding.getQuantity();
            BigDecimal existingAvgPrice = holding.getAvgBuyPrice();

            BigDecimal newQty = request.getQuantity();
            BigDecimal newBuyPrice = request.getBuyPrice();

            BigDecimal totalQty = existingQty.add(newQty);
            BigDecimal totalCost = (existingQty.multiply(existingAvgPrice)).add(newQty.multiply(newBuyPrice));
            BigDecimal weightedAvgPrice = totalCost.divide(totalQty, 4, RoundingMode.HALF_UP);

            holding.setQuantity(totalQty);
            holding.setAvgBuyPrice(weightedAvgPrice);
        } else {
            holding = Holding.builder()
                    .portfolio(portfolio)
                    .stock(stock)
                    .quantity(request.getQuantity())
                    .avgBuyPrice(request.getBuyPrice())
                    .build();
        }

        Holding savedHolding = holdingRepository.save(holding);

        Activity activity = Activity.builder()
                .user(user)
                .type("STOCK_BOUGHT")
                .message(String.format("Bought %s shares of %s at ₹%s", request.getQuantity(), stock.getSymbol(), request.getBuyPrice()))
                .build();
        activityRepository.save(activity);

        return mapToHoldingResponse(savedHolding);
    }

    @Transactional
    public void sellStock(SellStockRequest request) {
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + request.getUserId()));

        Portfolio portfolio = portfolioRepository.findByUserId(request.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("Portfolio not found for user: " + request.getUserId()));

        Stock stock = stockRepository.findById(request.getStockId())
                .orElseThrow(() -> new IllegalArgumentException("Stock not found with id: " + request.getStockId()));

        Holding holding = holdingRepository.findByPortfolioIdAndStockId(portfolio.getId(), stock.getId())
                .orElseThrow(() -> new IllegalArgumentException("Holding does not exist for stock: " + stock.getSymbol()));

        BigDecimal ownedQty = holding.getQuantity();
        BigDecimal sellQty = request.getQuantity();

        if (sellQty.compareTo(ownedQty) > 0) {
            throw new IllegalArgumentException(String.format("Insufficient quantity: You own %s shares but attempted to sell %s", ownedQty, sellQty));
        }

        if (sellQty.compareTo(ownedQty) == 0) {
            holdingRepository.deleteByPortfolioIdAndStockId(portfolio.getId(), stock.getId());
        } else {
            BigDecimal remainingQty = ownedQty.subtract(sellQty);
            holding.setQuantity(remainingQty);
            holdingRepository.save(holding);
        }

        Activity activity = Activity.builder()
                .user(user)
                .type("STOCK_SOLD")
                .message(String.format("Sold %s shares of %s", sellQty, stock.getSymbol()))
                .build();
        activityRepository.save(activity);
    }

    @Transactional(readOnly = true)
    public PortfolioSummaryResponse getPortfolioSummary(UUID userId) {
        Portfolio portfolio = portfolioRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Portfolio not found for user: " + userId));

        List<Holding> holdings = holdingRepository.findByPortfolioId(portfolio.getId());

        List<UUID> stockIds = holdings.stream().map(h -> h.getStock().getId()).toList();
        Map<UUID, MarketPrice> priceMap = marketPriceRepository.findAllById(stockIds).stream()
                .collect(Collectors.toMap(MarketPrice::getStockId, Function.identity()));

        BigDecimal totalInvested = BigDecimal.ZERO;
        BigDecimal totalCurrentValue = BigDecimal.ZERO;
        List<HoldingResponse> holdingResponses = new ArrayList<>();

        for (Holding h : holdings) {
            Stock stock = h.getStock();
            BigDecimal qty = h.getQuantity();
            BigDecimal avgPrice = h.getAvgBuyPrice();
            BigDecimal invested = qty.multiply(avgPrice);

            MarketPrice mp = priceMap.get(stock.getId());
            BigDecimal currentPrice = (mp != null) ? mp.getPrice() : avgPrice;
            BigDecimal currentValue = qty.multiply(currentPrice);
            BigDecimal unrealizedPnL = currentValue.subtract(invested);
            BigDecimal returnPct = (invested.compareTo(BigDecimal.ZERO) > 0)
                    ? unrealizedPnL.divide(invested, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                    : BigDecimal.ZERO;

            totalInvested = totalInvested.add(invested);
            totalCurrentValue = totalCurrentValue.add(currentValue);

            holdingResponses.add(HoldingResponse.builder()
                    .holdingId(h.getId())
                    .stockId(stock.getId())
                    .symbol(stock.getSymbol())
                    .exchange(stock.getExchange())
                    .companyName(stock.getCompanyName())
                    .quantity(qty)
                    .avgBuyPrice(avgPrice)
                    .investedValue(invested)
                    .currentPrice(currentPrice)
                    .currentValue(currentValue)
                    .unrealizedPnL(unrealizedPnL)
                    .returnPercentage(returnPct)
                    .build());
        }

        BigDecimal totalPnL = totalCurrentValue.subtract(totalInvested);
        BigDecimal totalReturnPct = (totalInvested.compareTo(BigDecimal.ZERO) > 0)
                ? totalPnL.divide(totalInvested, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

        return PortfolioSummaryResponse.builder()
                .portfolioId(portfolio.getId())
                .userId(userId)
                .totalInvested(totalInvested)
                .totalCurrentValue(totalCurrentValue)
                .totalUnrealizedPnL(totalPnL)
                .totalReturnPercentage(totalReturnPct)
                .holdings(holdingResponses)
                .build();
    }

    private HoldingResponse mapToHoldingResponse(Holding holding) {
        BigDecimal invested = holding.getQuantity().multiply(holding.getAvgBuyPrice());
        return HoldingResponse.builder()
                .holdingId(holding.getId())
                .stockId(holding.getStock().getId())
                .symbol(holding.getStock().getSymbol())
                .exchange(holding.getStock().getExchange())
                .companyName(holding.getStock().getCompanyName())
                .quantity(holding.getQuantity())
                .avgBuyPrice(holding.getAvgBuyPrice())
                .investedValue(invested)
                .currentPrice(holding.getAvgBuyPrice())
                .currentValue(invested)
                .unrealizedPnL(BigDecimal.ZERO)
                .returnPercentage(BigDecimal.ZERO)
                .build();
    }
}