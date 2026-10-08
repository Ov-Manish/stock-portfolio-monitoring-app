package com.stockmonitor.stock_portfolio_monitoring_app.rateLimit;


import com.stockmonitor.stock_portfolio_monitoring_app.constants.Tier;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimitingService {

    private final Map<String , Bucket> bucketCache = new ConcurrentHashMap<>();

    /**
     * Attempts to consume 1 token for a given IP and Tier.
     * Returns true if allowed, false if limit exceeded.
     */
    public boolean tryConsume(Tier tier , String clientIP){
        String key  = tier.name() + ":" + clientIP;
        Bucket bucket = bucketCache.computeIfAbsent(key , k -> createBucketForTier(tier));
        return bucket.tryConsume(1);
    }


    private Bucket createBucketForTier(Tier tier){
        return switch (tier){
            case AUTH -> Bucket.builder()
                    .addLimit(Bandwidth.classic(10, Refill.greedy(10 , Duration.ofMinutes(1))))
                    .build();

            case HEAVY -> Bucket.builder()
                    .addLimit(Bandwidth.classic(5,Refill.greedy(5, Duration.ofMinutes(1))))
                    .build();

            case PUBLIC -> Bucket.builder()
                    .addLimit(Bandwidth.classic(60 , Refill.greedy(60 , Duration.ofMinutes(1))))
                    .build();
        };
    }

}
