package com.tradeexample.exchange.service;

import com.tradeexample.common.exception.BusinessException;
import com.tradeexample.common.exception.ErrorCode;
import com.tradeexample.exchange.client.ExchangeRateSourceClient;
import com.tradeexample.exchange.model.ExchangeRateSnapshot;
import com.tradeexample.exchange.service.ExchangeRateSnapshotStore.RefreshState;
import com.tradeexample.exchange.service.ExchangeRateSnapshotStore.StoredValue;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.time.Instant;

/** 负责后台刷新汇率，并按来源数据年龄提供只读查询。 */
@Service
public class ExchangeRateService {

    private static final Logger log = LoggerFactory.getLogger(ExchangeRateService.class);

    private final ExchangeRateSnapshotStore store;
    private final ExchangeRateSourceClient sourceClient;
    private final RLock refreshLock;
    private final Duration refreshInterval;
    private final Duration maxSourceAge;

    /** 将刷新锁绑定到当前快照键 */
    public ExchangeRateService(
            ExchangeRateSnapshotStore store,
            ExchangeRateSourceClient sourceClient,
            RedissonClient redissonClient,
            @Value("${tradeexample.exchange-rate.snapshot-key}") String snapshotKey,
            @Value("${tradeexample.exchange-rate.refresh-interval}") Duration refreshInterval,
            @Value("${tradeexample.exchange-rate.max-source-age}") Duration maxSourceAge) {

        if (refreshInterval.isZero() || refreshInterval.isNegative()) {
            throw new IllegalArgumentException("汇率刷新间隔必须大于 0");
        }

        this.store = store;
        this.sourceClient = sourceClient;
        this.refreshLock = redissonClient.getLock(snapshotKey + ":lock");
        this.refreshInterval = refreshInterval;
        this.maxSourceAge = maxSourceAge;
    }

    /** 尝试刷新一次。其他实例正在刷新或刚尝试过时，本次直接结束。 */
    public void refresh() {
        if (!refreshLock.tryLock()) {
            return;
        }

        try {
            // 一定在拿到锁后读 Redis，才能看到前一个持锁者刚写入的尝试时间。
            StoredValue previous = store.read();
            RefreshState state = previous.state();
            Instant attemptAt = Instant.now();

            if (state != null
                    && attemptAt.isBefore(state.lastAttemptAt().plus(refreshInterval))) {
                return;
            }

            ExchangeRateSnapshot snapshot;
            try {
                snapshot = sourceClient.fetch();
            } catch (RestClientException e) {
                // 首次失败只记时间；已有成功报价时原样保留其金额、发布时间和成功时间。
                snapshot = state == null ? null : state.snapshot();
                log.warn("本次汇率来源抓取失败，原因：{}", e.getMessage());
            }

            // 无论抓取成功还是失败，只能基于抓取前读到的状态写回。
            RefreshState next = new RefreshState(snapshot, attemptAt);
            if (!store.replaceIfUnchanged(previous, next)) {
                log.info("汇率刷新期间快照已被其他执行者更新，本次结果已丢弃");
            }
        } finally {
            // unlock 会核对锁的持有者；已失去所有权时无需解锁。
            if (refreshLock.isHeldByCurrentThread()) {
                refreshLock.unlock();
            }
        }
    }

    /** 只读 Redis；整批报价都在来源年龄上限内时，返回报价和最近尝试时间。 */
    public ExchangeRateSnapshot query() {
        RefreshState state;
        try {
            state = store.read().state();
        } catch (RedisException e) {
            log.warn("汇率查询读取 Redis 失败", e);
            throw new BusinessException(ErrorCode.EXCHANGE_RATE_REDIS_UNAVAILABLE);
        }

        if (state == null || state.snapshot() == null) {
            throw new BusinessException(ErrorCode.EXCHANGE_RATE_NOT_READY);
        }

        ExchangeRateSnapshot snapshot = state.snapshot();
        Instant oldestAllowed = Instant.now().minus(maxSourceAge);

        // 一份快照整体返回，只要其中一条来源报价过期，就不能把整批作为可用报价返回。
        // 判断依据是每条报价的来源发布时间；一次成功抓取不会延长报价本身的有效期。
        boolean expired = snapshot.publishedAtByCurrency().values().stream()
                .anyMatch(publishedAt -> publishedAt.isBefore(oldestAllowed));
        if (expired) {
            throw new BusinessException(ErrorCode.EXCHANGE_RATE_EXPIRED);
        }

        // 报价和成功时间取自最近成功批次，尝试时间取自最近一次刷新。
        return new ExchangeRateSnapshot(
                snapshot.source(),
                snapshot.targetCurrency(),
                snapshot.rates(),
                snapshot.publishedAtByCurrency(),
                snapshot.fetchedAt(),
                state.lastAttemptAt()
        );
    }
}