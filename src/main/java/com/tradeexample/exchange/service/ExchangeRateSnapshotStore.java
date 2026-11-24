package com.tradeexample.exchange.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradeexample.exchange.model.ExchangeRateSnapshot;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Objects;

/** 整体读写汇率刷新状态，写回时检查 Redis 中的原值是否已经变化。 */
@Component
public class ExchangeRateSnapshotStore {

    private final RBucket<String> bucket;
    private final ObjectMapper objectMapper;

    /** 复用原有快照键 */
    public ExchangeRateSnapshotStore(
            RedissonClient redissonClient,
            ObjectMapper objectMapper,
            @Value("${tradeexample.exchange-rate.snapshot-key}") String snapshotKey) {

        this.bucket = redissonClient.getBucket(snapshotKey, StringCodec.INSTANCE);
        this.objectMapper = objectMapper;
    }

    /** 保存最近成功报价和最近尝试时间；首次抓取失败时，成功报价可以为空。 */
    public record RefreshState(
            ExchangeRateSnapshot snapshot,
            Instant lastAttemptAt
    ) {
        /** 每一条已保存的刷新状态都必须能说清何时尝试过。 */
        public RefreshState {
            Objects.requireNonNull(lastAttemptAt, "刷新尝试时间不能为空");
        }
    }

    /** 保存一次 Redis 读取的原始文本和解析结果，原始文本用于后续条件写入。 */
    public record StoredValue(String rawJson, RefreshState state) {
    }

    /** 读取整份刷新状态；旧格式以快照中的尝试时间初始化，键缺失时返回空状态。 */
    public StoredValue read() {
        String rawJson = bucket.get();
        if (rawJson == null) {
            return new StoredValue(null, null);
        }

        try {
            JsonNode root = objectMapper.readTree(rawJson);
            if (root == null || !root.isObject()) {
                throw new IllegalStateException("Redis 中的汇率快照必须是 JSON 对象");
            }

            // 旧格式的 rates 在顶层。直接从原始文本读取，保留 BigDecimal 精度。
            if (root.has("rates")) {
                ExchangeRateSnapshot snapshot =
                        objectMapper.readValue(rawJson, ExchangeRateSnapshot.class);
                return new StoredValue(
                        rawJson, new RefreshState(snapshot, snapshot.lastAttemptAt())
                );
            }

            RefreshState state = objectMapper.readValue(rawJson, RefreshState.class);
            return new StoredValue(rawJson, state);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Redis 中的汇率快照格式错误", e);
        }
    }

    /** 只有 Redis 仍是读取时的原值才整体替换；返回 false 表示其他刷新已先写入。 */
    public boolean replaceIfUnchanged(StoredValue expected, RefreshState next) {
        try {
            String nextJson = objectMapper.writeValueAsString(next);
            return bucket.compareAndSet(expected.rawJson(), nextJson);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("汇率刷新状态无法转换为 JSON", e);
        }
    }
}