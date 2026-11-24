package com.tradeexample.exchange.mock;

import com.tradeexample.exchange.client.ExchangeRateSourceResponse;
import com.tradeexample.exchange.client.ExchangeRateSourceResponse.Quote;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** 仅在 local 环境提供按每百单位外币报价的模拟来源。 */
@Profile("local")
@RestController
public class MockExchangeRateController {

    /** 用当前小时的整点模拟来源发布时间。 */
    @GetMapping("/api/v1/mock/exchange-rates")
    public ExchangeRateSourceResponse latest() {
        String publishedAt = Instant.now().truncatedTo(ChronoUnit.HOURS).toString();
        return new ExchangeRateSourceResponse(List.of(
                new Quote("USD", new BigDecimal("712.34"), publishedAt),
                new Quote("EUR", new BigDecimal("771.26"), publishedAt),
                new Quote("GBP", new BigDecimal("913.58"), publishedAt),
                new Quote("HKD", new BigDecimal("91.42"), publishedAt)
        ));
    }
}
