package com.tradeexample.exchange.client;

import java.math.BigDecimal;
import java.util.List;

/** 模拟来源一次返回的全部人民币现汇买入价。 */
public record ExchangeRateSourceResponse(List<Quote> quotes) {

    /** 每百单位外币对应的人民币金额，以及来源发布这条报价的 UTC 时间。 */
    public record Quote(String baseCurrency, BigDecimal cnyPerHundred, String publishedAt) {
    }
}
