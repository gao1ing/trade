package com.tradeexample.exchange.client;

import com.tradeexample.exchange.client.ExchangeRateSourceResponse.Quote;
import com.tradeexample.exchange.model.ExchangeRateSnapshot;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** 从模拟来源抓取报价，整批校验通过后生成汇率快照。 */
@Component
public class ExchangeRateSourceClient {

    private static final Set<String> CURRENCIES = Set.of("USD", "EUR", "GBP", "HKD");
    private final RestClient client;
    private final String sourceUrl;
    private final Duration maxSourceAge;

    /** 设置 HTTP 超时和来源报价最大允许年龄。 */
    public ExchangeRateSourceClient(RestClient.Builder builder,
            @Value("${tradeexample.exchange-rate.source-url}") String sourceUrl,
            @Value("${tradeexample.exchange-rate.connect-timeout-ms}") int connectTimeoutMs,
            @Value("${tradeexample.exchange-rate.read-timeout-ms}") int readTimeoutMs,
            @Value("${tradeexample.exchange-rate.max-source-age}") Duration maxSourceAge) {
        if (connectTimeoutMs <= 0 || readTimeoutMs <= 0) {
            throw new IllegalArgumentException("汇率 HTTP 超时必须大于 0");
        }
        if (maxSourceAge.isNegative() || maxSourceAge.isZero()) {
            throw new IllegalArgumentException("汇率来源最大允许年龄必须大于 0");
        }

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        this.client = builder.requestFactory(factory).build();
        this.sourceUrl = sourceUrl;
        this.maxSourceAge = maxSourceAge;
    }

    /** 任一报价无效时整批失败，避免查询读到部分新报价。 */
    public ExchangeRateSnapshot fetch() {
        Instant attemptAt = Instant.now();
        ExchangeRateSourceResponse response = client.get().uri(sourceUrl)
                .accept(MediaType.APPLICATION_JSON).retrieve().body(ExchangeRateSourceResponse.class);
        if (response == null || response.quotes() == null || response.quotes().isEmpty()) {
            throw new RestClientException("汇率来源未返回报价");
        }

        // 以同一接收时间判断整批报价是否过期。
        Instant receivedAt = Instant.now();
        Instant oldestAllowed = receivedAt.minus(maxSourceAge);
        Map<String, BigDecimal> rates = new HashMap<>();
        Map<String, Instant> publishedTimes = new HashMap<>();
        for (Quote quote : response.quotes()) {
            if (quote == null || quote.baseCurrency() == null) {
                throw new RestClientException("汇率报价缺少币种");
            }
            String currency = quote.baseCurrency();
            if (!CURRENCIES.contains(currency) || rates.containsKey(currency)) {
                throw new RestClientException("汇率币种不受支持或重复：" + currency);
            }

            // 来源为每百单位报价，统一换算为每单位人民币价并检查舍入结果。
            if (quote.cnyPerHundred() == null || quote.cnyPerHundred().signum() <= 0) {
                throw new RestClientException(currency + " 的报价必须大于 0");
            }
            BigDecimal rate = quote.cnyPerHundred().movePointLeft(2).setScale(8, RoundingMode.HALF_UP);
            if (rate.signum() == 0) {
                throw new RestClientException(currency + " 的单位报价舍入后为 0");
            }

            // 每种货币独立发布时间，整批抓取成功也可能包含一条已过期报价。
            String timestamp = quote.publishedAt();
            if (timestamp == null || !timestamp.endsWith("Z")) {
                throw new RestClientException(currency + " 的发布时间必须为 UTC 时间");
            }
            Instant publishedAt;
            try {
                publishedAt = OffsetDateTime.parse(timestamp).toInstant();
            } catch (DateTimeParseException e) {
                throw new RestClientException(currency + " 的发布时间无效", e);
            }
            if (publishedAt.isBefore(oldestAllowed) || publishedAt.isAfter(receivedAt)) {
                throw new RestClientException(currency + " 的发布时间超出允许范围");
            }
            rates.put(currency, rate);
            publishedTimes.put(currency, publishedAt);
        }

        // 美元是必需币种，其他币种仅保存本批次实际返回的报价。
        if (!rates.containsKey("USD")) {
            throw new RestClientException("汇率来源缺少必需币种 USD");
        }
        return new ExchangeRateSnapshot("LOCAL_MOCK", "CNY", rates, publishedTimes, receivedAt, attemptAt);
    }
}
