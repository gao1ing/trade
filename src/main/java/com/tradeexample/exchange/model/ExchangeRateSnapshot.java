package com.tradeexample.exchange.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/** 保存同一来源、同一次成功抓取得到的全部报价，以及最近一次刷新尝试时间。 */
public record ExchangeRateSnapshot(

        // 报价来源标识，例如后续开发使用的本地模拟来源。
        String source,

        // 所有报价共同兑换到的目标币种，例如 CNY。
        String targetCurrency,

        // 键是基础币种，值是 1 单位该币种可兑换的目标币种数量。
        Map<String, BigDecimal> rates,

        // 键与 rates 对应，值是来源发布该币种报价的时间。
        Map<String, Instant> publishedAtByCurrency,

        // 本应用最近一次成功取得这份完整报价的时间。
        Instant fetchedAt,

        // 最近一次刷新开始的时间。后续刷新失败时，它仍然可以向前推进。
        Instant lastAttemptAt
) {

    /** 固定这份快照中的报价和发布时间，避免调用方继续修改传入的 Map。 */
    public ExchangeRateSnapshot {
        Objects.requireNonNull(source, "汇率来源不能为空");
        Objects.requireNonNull(targetCurrency, "目标币种不能为空");
        Objects.requireNonNull(fetchedAt, "成功抓取时间不能为空");
        Objects.requireNonNull(lastAttemptAt, "刷新尝试时间不能为空");

        rates = Map.copyOf(rates);
        publishedAtByCurrency = Map.copyOf(publishedAtByCurrency);

        // 每个实际保存的报价都要能找到自己的来源发布时间。
        if (rates.isEmpty() || !rates.keySet().equals(publishedAtByCurrency.keySet())) {
            throw new IllegalArgumentException("汇率快照至少需要一条报价，且每条报价都要有对应的发布时间");
        }
    }
}