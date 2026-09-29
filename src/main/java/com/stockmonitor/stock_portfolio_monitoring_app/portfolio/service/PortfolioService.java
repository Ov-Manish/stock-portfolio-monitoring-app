package com.stockmonitor.stock_portfolio_monitoring_app.portfolio.service;


import com.stockmonitor.stock_portfolio_monitoring_app.marketdata.client.YahooFinanceClient;
import com.stockmonitor.stock_portfolio_monitoring_app.marketdata.dto.MarketTickEvent;
import com.stockmonitor.stock_portfolio_monitoring_app.portfolio.entity.PortfolioTransaction;
import com.stockmonitor.stock_portfolio_monitoring_app.portfolio.repository.PortfolioTransactionRepository;
import com.stockmonitor.stock_portfolio_monitoring_app.activity.entity.Activity;
import com.stockmonitor.stock_portfolio_monitoring_app.activity.repository.ActivityRepository;
import com.stockmonitor.stock_portfolio_monitoring_app.marketdata.entity.MarketPrice;
import com.stockmonitor.stock_portfolio_monitoring_app.marketdata.repository.MarketPriceRepository;
import com.stockmonitor.stock_portfolio_monitoring_app.portfolio.dto.*;
import com.stockmonitor.stock_portfolio_monitoring_app.portfolio.entity.Holding;
import com.stockmonitor.stock_portfolio_monitoring_app.portfolio.entity.Portfolio;
import com.stockmonitor.stock_portfolio_monitoring_app.portfolio.repository.HoldingRepository;
import com.stockmonitor.stock_portfolio_monitoring_app.portfolio.repository.PortfolioRepository;
import com.stockmonitor.stock_portfolio_monitoring_app.stock.entity.Stock;
import com.stockmonitor.stock_portfolio_monitoring_app.stock.repository.StockRepository;
import com.stockmonitor.stock_portfolio_monitoring_app.user.entity.User;
import com.stockmonitor.stock_portfolio_monitoring_app.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class PortfolioService {

    private final PortfolioRepository portfolioRepository;
    private final HoldingRepository holdingRepository;
    private final StockRepository stockRepository;
    private final UserRepository userRepository;
    private final MarketPriceRepository marketPriceRepository;
    private final ActivityRepository activityRepository;
    private final PortfolioTransactionRepository portfolioTransactionRepository;
    private final YahooFinanceClient yahooFinanceClient;

    @Transactional
    public PortfolioResponse createPortfolio(CreatePortfolioRequest request) {
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + request.getUserId()));

        Portfolio portfolio = Portfolio.builder()
                .user(user)
                .name(request.getName().trim())
                .build();

        Portfolio saved = portfolioRepository.save(portfolio);

        Activity activity = Activity.builder()
                .user(user)
                .type("PORTFOLIO_CREATED")
                .message(String.format("Created new portfolio '%s'", saved.getName()))
                .build();
        activityRepository.save(activity);

        log.info("Created portfolio: id={}, name='{}', userId={}", saved.getId(), saved.getName(), user.getId());

        return PortfolioResponse.builder()
                .id(saved.getId())
                .userId(user.getId())
                .name(saved.getName())
                .holdingsCount(0)
                .createdAt(saved.getCreatedAt())
                .build();
    }

    @Transactional(readOnly = true)
    public List<PortfolioResponse> getUserPortfolios(UUID userId) {
        List<Portfolio> portfolios = portfolioRepository.findByUserId(userId);

        return portfolios.stream()
                .map(p -> PortfolioResponse.builder()
                        .id(p.getId())
                        .userId(userId)
                        .name(p.getName())
                        .holdingsCount(p.getHoldings() != null ? p.getHoldings().size() : 0)
                        .createdAt(p.getCreatedAt())
                        .build())
                .toList();
    }

    @Transactional
    public HoldingResponse buyStock(BuyStockRequest request) {
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + request.getUserId()));

        Portfolio portfolio = resolvePortfolio(request.getUserId(), request.getPortfolioId(), user);

        Stock stock = stockRepository.findById(request.getStockId())
                .orElseThrow(() -> new IllegalArgumentException("Stock not found with id: " + request.getStockId()));

        Optional<Holding> existingHoldingOpt = holdingRepository.findByPortfolioIdAndStockId(portfolio.getId(), stock.getId());

        Holding holding;

        BigDecimal buyPrice = resolveBuyPrice(request , stock);

        if (existingHoldingOpt.isPresent()) {
            holding = existingHoldingOpt.get();
            BigDecimal existingQty = holding.getQuantity();
            BigDecimal existingAvgPrice = holding.getAvgBuyPrice();

            BigDecimal newQty = request.getQuantity();

            BigDecimal totalQty = existingQty.add(newQty);
            BigDecimal totalCost = (existingQty.multiply(existingAvgPrice)).add(newQty.multiply(buyPrice));
            BigDecimal weightedAvgPrice = totalCost.divide(totalQty, 4, RoundingMode.HALF_UP);

            holding.setQuantity(totalQty);
            holding.setAvgBuyPrice(weightedAvgPrice);
        } else {
            holding = Holding.builder()
                    .portfolio(portfolio)
                    .stock(stock)
                    .quantity(request.getQuantity())
                    .avgBuyPrice(buyPrice)
                    .build();
        }

        Holding savedHolding = holdingRepository.save(holding);

        Activity activity = Activity.builder()
                .user(user)
                .type("STOCK_BOUGHT")
                .message(String.format("Bought %s shares of %s at ₹%s in portfolio '%s'",
                        request.getQuantity(), stock.getSymbol(),buyPrice , portfolio.getName()))
                .build();
        activityRepository.save(activity);

        log.info("Stock bought: user={}, symbol={}, qty={}, price=₹{}, portfolio='{}'",
                user.getEmail(), stock.getSymbol(), request.getQuantity(), buyPrice, portfolio.getName());


        PortfolioTransaction buyStock = PortfolioTransaction.builder()
                .portfolio(portfolio)
                .user(user)
                .stock(stock)
                .transactionType("BUY")
                .quantity(request.getQuantity())
                .price(buyPrice)
                .totalAmount(request.getQuantity().multiply(buyPrice))
                .realizedPnL(null)
                .build();

        portfolioTransactionRepository.save(buyStock);
        return mapToHoldingResponse(savedHolding);
    }

    @Transactional
    public SellStockResponse sellStock(SellStockRequest request) {
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + request.getUserId()));

        Portfolio portfolio = resolvePortfolio(request.getUserId(), request.getPortfolioId(), user);

        Stock stock = stockRepository.findById(request.getStockId())
                .orElseThrow(() -> new IllegalArgumentException("Stock not found with id: " + request.getStockId()));

        Holding holding = holdingRepository.findByPortfolioIdAndStockId(portfolio.getId(), stock.getId())
                .orElseThrow(() -> new IllegalArgumentException("Holding does not exist for stock: " + stock.getSymbol() + " in portfolio: " + portfolio.getName()));

        BigDecimal ownedQty = holding.getQuantity();
        BigDecimal sellQty = request.getQuantity();

        if (sellQty.compareTo(ownedQty) > 0) {
            throw new IllegalArgumentException(String.format("Insufficient quantity: You own %s shares but attempted to sell %s", ownedQty, sellQty));
        }

        // 1. Resolve Selling Price (Request Price -> Live Yahoo API -> DB Cached Price)
        BigDecimal sellPrice = resolveSellPrice(request, stock, holding);

        // 2. Financial Calculations
        BigDecimal avgBuyPrice = holding.getAvgBuyPrice();
        BigDecimal totalInvested = avgBuyPrice.multiply(sellQty).setScale(4, RoundingMode.HALF_UP);
        BigDecimal totalSaleProceeds = sellPrice.multiply(sellQty).setScale(4, RoundingMode.HALF_UP);
        BigDecimal realizedPnL = totalSaleProceeds.subtract(totalInvested).setScale(4, RoundingMode.HALF_UP);

        BigDecimal realizedPnLPercent = (totalInvested.compareTo(BigDecimal.ZERO) > 0)
                ? realizedPnL.divide(totalInvested, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

        String pnlStatus = realizedPnL.compareTo(BigDecimal.ZERO) > 0 ? "PROFIT"
                : (realizedPnL.compareTo(BigDecimal.ZERO) < 0 ? "LOSS" : "BREAK_EVEN");

        // 3. Update or Remove Holding
        BigDecimal remainingQty;
        if (sellQty.compareTo(ownedQty) == 0) {
            holdingRepository.deleteByPortfolioIdAndStockId(portfolio.getId(), stock.getId());
            remainingQty = BigDecimal.ZERO;
        } else {
            remainingQty = ownedQty.subtract(sellQty);
            holding.setQuantity(remainingQty);
            holdingRepository.save(holding);
        }

        // 4. Record Trade in Portfolio Transactions Ledger
        PortfolioTransaction sellTx = PortfolioTransaction.builder()
                .portfolio(portfolio)
                .user(user)
                .stock(stock)
                .transactionType("SELL")
                .quantity(sellQty)
                .price(sellPrice)
                .totalAmount(totalSaleProceeds)
                .realizedPnL(realizedPnL)
                .build();
        PortfolioTransaction savedTx = portfolioTransactionRepository.save(sellTx);

        // 5. Audit Activity Log
        String sign = realizedPnL.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "";
        Activity activity = Activity.builder()
                .user(user)
                .type("STOCK_SOLD")
                .message(String.format("Sold %s shares of %s at ₹%s (Realized PnL: %s₹%s, %s%%)",
                        sellQty, stock.getSymbol(), sellPrice, sign, realizedPnL, realizedPnLPercent))
                .build();
        activityRepository.save(activity);

        log.info("Stock sold: user={}, symbol={}, qty={}, sellPrice=₹{}, buyPrice=₹{}, realizedPnL=₹{} ({}), remaining={}",
                user.getEmail(), stock.getSymbol(), sellQty, sellPrice, avgBuyPrice, realizedPnL, pnlStatus, remainingQty);

        // 6. Return Structured Trade Receipt
        return SellStockResponse.builder()
                .transactionId(savedTx.getId())
                .portfolioId(portfolio.getId())
                .portfolioName(portfolio.getName())
                .stockId(stock.getId())
                .symbol(stock.getSymbol())
                .companyName(stock.getCompanyName())
                .soldQuantity(sellQty)
                .sellPrice(sellPrice)
                .avgBuyPrice(avgBuyPrice)
                .totalInvested(totalInvested)
                .totalSaleProceeds(totalSaleProceeds)
                .realizedPnL(realizedPnL)
                .realizedPnLPercent(realizedPnLPercent)
                .remainingQuantity(remainingQty)
                .pnlStatus(pnlStatus)
                .executedAt(savedTx.getCreatedAt())
                .build();
    }

    @Transactional(readOnly = true)
    public PortfolioSummaryResponse getPortfolioSummary(UUID userId) {
        Portfolio portfolio = portfolioRepository.findFirstByUserIdOrderByCreatedAtAsc(userId)
                .orElseThrow(() -> new IllegalArgumentException("No portfolio found for user: " + userId));

        return buildPortfolioSummary(portfolio);
    }

    @Transactional(readOnly = true)
    public PortfolioSummaryResponse getPortfolioById(UUID portfolioId) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new IllegalArgumentException("Portfolio not found with id: " + portfolioId));

        return buildPortfolioSummary(portfolio);
    }

    private PortfolioSummaryResponse buildPortfolioSummary(Portfolio portfolio) {
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
                .portfolioName(portfolio.getName())
                .userId(portfolio.getUser().getId())
                .totalInvested(totalInvested)
                .totalCurrentValue(totalCurrentValue)
                .totalUnrealizedPnL(totalPnL)
                .totalReturnPercentage(totalReturnPct)
                .holdings(holdingResponses)
                .build();
    }

    private Portfolio resolvePortfolio(UUID userId, UUID portfolioId, User user) {
        if (portfolioId != null) {
            return portfolioRepository.findByIdAndUserId(portfolioId, userId)
                    .orElseThrow(() -> new IllegalArgumentException(String.format("Portfolio not found with id %s for user %s", portfolioId, userId)));
        }

        return portfolioRepository.findFirstByUserIdOrderByCreatedAtAsc(userId)
                .orElseGet(() -> portfolioRepository.save(Portfolio.builder().user(user).name("Default Portfolio").build()));
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

    private BigDecimal resolveSellPrice(SellStockRequest request, Stock stock, Holding holding) {

        if (request.getSellPrice() != null && request.getSellPrice().compareTo(BigDecimal.ZERO) > 0) {
            return request.getSellPrice();
        }


        try {
            Optional<MarketTickEvent> tickOpt = yahooFinanceClient.fetchLatestMarketTick(stock.getSymbol());
            if (tickOpt.isPresent() && tickOpt.get().getPrice() != null && tickOpt.get().getPrice().compareTo(BigDecimal.ZERO) > 0) {
                return tickOpt.get().getPrice();
            }
        } catch (Exception e) {
            log.warn("Could not fetch live tick for {} on sell: {}", stock.getSymbol(), e.getMessage());
        }


        return marketPriceRepository.findById(stock.getId())
                .map(MarketPrice::getPrice)
                .orElse(holding.getAvgBuyPrice());
    }

    @Transactional(readOnly = true)
    public List<PortfolioTransactionResponse> getPortfolioTransactions(UUID portfolioId) {
        return portfolioTransactionRepository.findByPortfolioIdOrderByCreatedAtDesc(portfolioId).stream()
                .map(this::mapToTransactionResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PortfolioTransactionResponse> getUserTransactions(UUID userId) {
        return portfolioTransactionRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::mapToTransactionResponse)
                .toList();
    }

    private PortfolioTransactionResponse mapToTransactionResponse(PortfolioTransaction tx) {
        return PortfolioTransactionResponse.builder()
                .transactionId(tx.getId())
                .portfolioId(tx.getPortfolio().getId())
                .portfolioName(tx.getPortfolio().getName())
                .stockId(tx.getStock().getId())
                .symbol(tx.getStock().getSymbol())
                .companyName(tx.getStock().getCompanyName())
                .transactionType(tx.getTransactionType())
                .quantity(tx.getQuantity())
                .price(tx.getPrice())
                .totalAmount(tx.getTotalAmount())
                .realizedPnL(tx.getRealizedPnL())
                .executedAt(tx.getCreatedAt())
                .build();
    }



    @Transactional
    public void deletePorfolio(UUID portfolioId , UUID userId){
      Portfolio portfolio = portfolioRepository.findById(portfolioId)
              .orElseThrow(()-> new IllegalArgumentException("Portfolio not Found with Id : " +portfolioId));

      if (portfolio != null && !portfolio.getUser().getId().equals(userId)){
          throw new SecurityException("Unauthorized: You do not own this portfolio");
      }

      List<Holding> activeHoldings = holdingRepository.findByPortfolioId(portfolioId);

      if (!activeHoldings.isEmpty()){
          throw new IllegalStateException(String.format(
                  "Cannot delete portfolio '%s' because it still contains %d active stock holdings. Sell all shares before deleting!",
                  portfolio.getName(), activeHoldings.size()));
      }

      List<Portfolio> allUserPortfolios = portfolioRepository.findByUserId(portfolio.getUser().getId());

      if (allUserPortfolios.size() <= 1){
          throw new IllegalStateException("Cannot delete your primary portfolio. You must maintain at least one portfolio.");
      }

      portfolioRepository.delete(portfolio);

        log.info("Portfolio deleted successfully: id={}, name='{}', user={}",
                portfolioId, portfolio.getName(), portfolio.getUser().getEmail());

    }

    private BigDecimal resolveBuyPrice(BuyStockRequest request, Stock stock) {

        BigDecimal livePrice = null;

        // 2. Fetch live price from Yahoo Finance
        try {
            Optional<MarketTickEvent> tickOpt = yahooFinanceClient.fetchLatestMarketTick(stock.getSymbol());
            if (tickOpt.isPresent() && tickOpt.get().getPrice() != null && tickOpt.get().getPrice().compareTo(BigDecimal.ZERO) > 0) {
                log.info("Resolved live market buy price for {}: ₹{}", stock.getSymbol(), tickOpt.get().getPrice());
                 livePrice = tickOpt.get().getPrice();
            }
        } catch (Exception e) {
            log.warn("Could not fetch live tick for {} on buy: {}", stock.getSymbol(), e.getMessage());
        }

        if (livePrice == null){
            livePrice = marketPriceRepository.findById(stock.getId())
                    .map(MarketPrice::getPrice)
                    .orElseThrow(()-> new IllegalStateException("Market Price not Available for : "+stock.getSymbol()));
        }


        // 2. If user intentionally passed a price, validate it!
        if (request.getBuyPrice() !=null){
            BigDecimal diff = request.getBuyPrice().subtract(livePrice).abs();
            BigDecimal allowedTolerance = livePrice.multiply(BigDecimal.valueOf(0.05));

            if (diff.compareTo(allowedTolerance) > 0 ){
                throw new IllegalArgumentException(String.format(
                        "Invalid buy price ₹%s! Real market price is ₹%s ",
                        request.getBuyPrice(), livePrice
                ));
            }

          return request.getBuyPrice();
        }

        return livePrice;
    }
}