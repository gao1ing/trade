package com.tradeexample.common.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 为应用创建共享的 Redis 客户端，汇率模块通过它读写快照。 */
@Configuration(proxyBeanMethods = false)
public class RedissonConfig {

    /** 使用配置中的单节点地址建立连接，应用关闭时由 Spring 释放连接和线程。 */
    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient(
            @Value("${tradeexample.redis.address}") String address,
            @Value("${tradeexample.redis.password}") String password) {

        Config config = new Config();
        SingleServerConfig server = config.useSingleServer().setAddress(address);

        // 只有配置了密码，才向 Redis 发送认证信息。
        if (!password.isEmpty()) {
            server.setPassword(password);
        }

        return Redisson.create(config);
    }
}