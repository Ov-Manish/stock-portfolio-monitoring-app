package com.stockmonitor.stock_portfolio_monitoring_app.marketdata.client;

import com.stockmonitor.stock_portfolio_monitoring_app.marketdata.dto.MarketTickEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import com.stockmonitor.stock_portfolio_monitoring_app.marketdata.dto.DailyPriceResponse;
import java.time.Duration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Optional;

@Slf4j
@Component
public class YahooFinanceClient {

    private final RestClient restClient;

    private static final String YAHOO_URL = "https://query1.finance.yahoo.com/v8/finance/chart/{symbol}.NS?interval=1d";
    private static final String YAHOO_HISTORY_URL = "https://query1.finance.yahoo.com/v8/finance/chart/{symbol}.NS?range={range}&interval=1d";
    YahooFinanceClient(){
//        this.restClient = RestClient.builder()
//                .defaultHeader(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
//                .build();

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(4));
        requestFactory.setReadTimeout(Duration.ofSeconds(6));
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .defaultHeader(HttpHeaders.ACCEPT,"application/json")
                .build();
    }

    public Optional<MarketTickEvent> fetchLatestMarketTick(String symbol){
        try {
            JsonNode root = restClient.get()
                    .uri(YAHOO_URL , symbol)
                    .retrieve()
                    .body(JsonNode.class);

            if (root == null || !root.has("chart") || root.path("chart").path("result").isNull()){
                log.warn("Empty response from Yahoo Finance for symbol: {}", symbol);
                return Optional.empty();
            }


            JsonNode meta = root.path("chart").path("result").path(0).path("meta");
            if (meta.isMissingNode() || !meta.has("regularMarketPrice")) {
                log.warn("Meta or price node missing for symbol: {}", symbol);
                return Optional.empty();
            }

            String companyName = meta.has("shortName") && !meta.path("shortName").isNull()
                    ? meta.path("shortName").asText()
                    : symbol;

//            Fetching the curretn and previous value
            BigDecimal currentPrice = BigDecimal.valueOf(meta.path("regularMarketPrice").asDouble());
            BigDecimal previousClose = meta.has("chartPreviousClose") ?
                    BigDecimal.valueOf(meta.path("chartPreviousClose").asDouble())
                    : currentPrice;


//           Daily Percentage Change
            BigDecimal change = currentPrice.subtract(previousClose);
            BigDecimal  changePercent = previousClose.compareTo(BigDecimal.ZERO) != 0
                    ? change.divide(previousClose , 4 , RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                    : BigDecimal.ZERO;

//  Calculates the Day high and day low

            BigDecimal dayHigh = meta.has("regularMarketDayHigh")
                    ? BigDecimal.valueOf(meta.path("regularMarketDayHigh").asDouble())
                    :currentPrice;

            BigDecimal dayLow = meta.has("regularMarketDayLow")
                    ? BigDecimal.valueOf(meta.path("regularMarketDayLow").asDouble())
                    : currentPrice;


            Long volume = meta.has("regularMarketVolume") ? meta.path("regularMarketVolume").asLong()
                    : 0L;



//            52 Weeks High
            BigDecimal fiftyTwoWeeksHigh = meta.has("fiftyTwoWeekHigh") && !meta.path("fiftyTwoWeekHigh").isNull()
                    ? BigDecimal.valueOf(meta.path("fiftyTwoWeekHigh").asDouble()) :null;

//            52 Weeks low
            BigDecimal fiftyTwoWeekLow = meta.has("fiftyTwoWeekLow") && !meta.path("fiftyTwoWeekLow").isNull()
                    ? BigDecimal.valueOf(meta.path("fiftyTwoWeekLow").asDouble()) : null;


//            Open Price

            BigDecimal openPrice = null;

            JsonNode quoteNode =
                    root.path("chart").path("result").path(0).path("indicators").path("quote").path(0);
            if (quoteNode.has("open") && quoteNode.path("open").isArray() && !quoteNode.path("open").isEmpty() && !quoteNode.path("open").get(0).isNull()) {
                openPrice = BigDecimal.valueOf(quoteNode.path("open").get(0).asDouble());
            }

//            Makeing the tickEvent
            MarketTickEvent tickEvent = MarketTickEvent.builder()
                    .companyName(companyName)
                    .symbol(symbol)
                    .price(currentPrice)
                    .change(change)
                    .changePercent(changePercent)
                    .dayHigh(dayHigh)
                    .dayLow(dayLow)
                    .volume(volume)
                    .fiftyTwoWeekHigh(fiftyTwoWeeksHigh)
                    .fiftyTwoWeekLow(fiftyTwoWeekLow)
                    .openPrice(openPrice)
                    .timestamp(Instant.now().toEpochMilli())
                    .build();

            return  Optional.of(tickEvent);
        }catch (Exception e ){
            log.error("Failed to Fetch market Data from Yahoo Finance for Symbol {}:{}",symbol,e.getMessage());
            return Optional.empty();
        }
    }

    public List<DailyPriceResponse> fetchHistoricalDailyPrices(String symbol, String range) {
        try {
            JsonNode root = restClient.get()
                    .uri(YAHOO_HISTORY_URL, symbol, range)
                    .retrieve()
                    .body(JsonNode.class);

            if (root == null || !root.has("chart") || root.path("chart").path("result").isNull()) {
                log.warn("No historical data found on Yahoo Finance for symbol: {}", symbol);
                return Collections.emptyList();
            }

            JsonNode result = root.path("chart").path("result").path(0);
            JsonNode timestamps = result.path("timestamp");
            JsonNode quote = result.path("indicators").path("quote").path(0);

            if (timestamps.isMissingNode() || quote.isMissingNode()) {
                log.warn("Missing timestamp or quote node for symbol: {}", symbol);
                return Collections.emptyList();
            }

            JsonNode opens = quote.path("open");
            JsonNode highs = quote.path("high");
            JsonNode lows = quote.path("low");
            JsonNode closes = quote.path("close");
            JsonNode volumes = quote.path("volume");

            List<DailyPriceResponse> dailyPrices = new ArrayList<>();

            for (int i = 0; i < timestamps.size(); i++) {
                // 1. Skip if open is null (holiday)
                if (opens.path(i).isNull()) {
                    continue;
                }

                long epochSeconds = timestamps.path(i).asLong();
                LocalDate priceDate = Instant.ofEpochSecond(epochSeconds)
                        .atZone(ZoneId.of("Asia/Kolkata"))
                        .toLocalDate();

                BigDecimal open = BigDecimal.valueOf(opens.path(i).asDouble()).setScale(4, RoundingMode.HALF_UP);
                BigDecimal high = !highs.path(i).isNull()
                        ? BigDecimal.valueOf(highs.path(i).asDouble()).setScale(4, RoundingMode.HALF_UP)
                        : open;
                BigDecimal low = !lows.path(i).isNull()
                        ? BigDecimal.valueOf(lows.path(i).asDouble()).setScale(4, RoundingMode.HALF_UP)
                        : open;

                // 2. If close is null (unsettled EOD candle), fallback to regularMarketPrice from meta!
                BigDecimal close;
                if (!closes.path(i).isNull()) {
                    close = BigDecimal.valueOf(closes.path(i).asDouble()).setScale(4, RoundingMode.HALF_UP);
                } else {
                    double fallback = result.path("meta").path("regularMarketPrice").asDouble(open.doubleValue());
                    close = BigDecimal.valueOf(fallback).setScale(4, RoundingMode.HALF_UP);
                }

                Long volume = !volumes.path(i).isNull() ? volumes.path(i).asLong(0L) : 0L;

                dailyPrices.add(DailyPriceResponse.builder()
                        .date(priceDate)
                        .open(open)
                        .high(high)
                        .low(low)
                        .close(close)
                        .volume(volume)
                        .build());
            }

            log.info("Successfully fetched {} historical daily candles for symbol: {}", dailyPrices.size(), symbol);
            return dailyPrices;

        } catch (Exception e) {
            log.error("Failed to fetch historical data from Yahoo Finance for symbol {}: {}", symbol, e.getMessage());
            return Collections.emptyList();
        }
    }
}
