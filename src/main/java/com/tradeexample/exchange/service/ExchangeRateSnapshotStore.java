package com.tradeexample.exchange.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradeexample.exchange.model.ExchangeRateSnapshot;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;

/** 将整份汇率快照保存在一个 Redis 字符串键中，每次查询都从 Redis 读取。 */
@Component
public class ExchangeRateSnapshotStore {

    private final RBucket<String> bucket;
    private final ObjectMapper objectMapper;

    /** 固定快照的 Redis 键和字符串编码，读写共用同一个配置。 */
    public ExchangeRateSnapshotStore(
            RedissonClient redissonClient,
            ObjectMapper objectMapper,
            @Value("${tradeexample.exchange-rate.snapshot-key}") String snapshotKey) {

        this.bucket = redissonClient.getBucket(snapshotKey, StringCodec.INSTANCE);
        this.objectMapper = objectMapper;
    }

    /** 读取最近保存的完整快照。Redis 中还没有这个键时返回空。 */
    public Optional<ExchangeRateSnapshot> read() {
        String json = bucket.get();
        if (json == null) {
            return Optional.empty();
        }

        try {
            // 已有键却读不出快照，说明存储内容有问题，调用方需要看到这个错误。
            ExchangeRateSnapshot snapshot =
                    objectMapper.readValue(json, ExchangeRateSnapshot.class);
            if (snapshot == null) {
                throw new IllegalStateException("Redis 中的汇率快照内容为 JSON null");
            }
            return Optional.of(snapshot);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Redis 中的汇率快照格式错误", e);
        }
    }

    /** 先生成完整 JSON，再用一次 Redis 写入替换整份快照。 */
    public void save(ExchangeRateSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "待保存的汇率快照不能为空");

        String json;
        try {
            // 转换失败时尚未写 Redis，之前保存的报价仍可读取。
            json = objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("汇率快照无法转换为 JSON", e);
        }

        bucket.set(json);
    }
}