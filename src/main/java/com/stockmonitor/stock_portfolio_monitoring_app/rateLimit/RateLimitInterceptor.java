package com.stockmonitor.stock_portfolio_monitoring_app.rateLimit;


import com.stockmonitor.stock_portfolio_monitoring_app.exception.RateLimitExceededException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.impl.STPrintErrorImpl;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {
    private final RateLimitingService rateLimitingService;

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler) throws Exception {

        if (!(handler instanceof HandlerMethod handlerMethod)){
            return  true;
        }

        // 2. Checking if the controller method has the @RateLimited annotation
        RateLimited rateLimited = handlerMethod.getMethodAnnotation(RateLimited.class);
        if (rateLimited == null){
            return true;  // If nott rate limited, proceed normally!
        }


        // 3. Extracts the real client IP (hooring reverse proxies & load balancer)
        String clientIP = extractClientIP(request);

        // 4. Try consuming a token from the bucket
        boolean allowed = rateLimitingService.tryConsume(rateLimited.tier(), clientIP);

        if (!allowed){
            response.setHeader("Retry-After","60");

            throw new RateLimitExceededException(
                    "Rate limit exceeded for " + rateLimited.tier() + " tier. Please wait before retrying."
            );
        }
        return true; // Token available -> proceed to Controller
    }

//    Extrat Client IP from the Request , checking X-Forwarded-For first.
    private String extractClientIP(HttpServletRequest request){
        String xForwardFor = request.getHeader("X-Forwarded-For");
        if (xForwardFor != null && !xForwardFor.isBlank()){
            return xForwardFor.split(",")[0].trim();
        }

        return request.getRemoteAddr();
    }
}
