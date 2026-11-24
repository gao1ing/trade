package com.tradeexample.exchange.config;

import com.tradeexample.exchange.service.ExchangeRateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.time.Duration;
import java.time.Instant;

/** 应用就绪后启动汇率预热，并在后台按固定间隔刷新。 */
@Configuration(proxyBeanMethods = false)
public class ExchangeRateScheduleConfig {

    private static final Logger log = LoggerFactory.getLogger(ExchangeRateScheduleConfig.class);

    /** 由 Spring 管理单线程调度器，一次刷新异常后仍可执行下一周期。 */
    @Bean
    public ThreadPoolTaskScheduler exchangeRateScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setThreadNamePrefix("exchange-rate-");
        scheduler.setPoolSize(1);
        scheduler.setErrorHandler(error -> log.error("后台汇率刷新未完成", error));
        return scheduler;
    }

    /** 将启动监听器绑定到汇率专用调度器。 */
    @Bean
    public RefreshStarter exchangeRateRefreshStarter(
            @Qualifier("exchangeRateScheduler") ThreadPoolTaskScheduler scheduler,
            ExchangeRateService service,
            @Value("${tradeexample.exchange-rate.refresh-interval}") Duration interval) {
        return new RefreshStarter(scheduler, service, interval);
    }

    /** 等本地 HTTP 接口就绪后注册后台刷新任务。 */
    public record RefreshStarter(ThreadPoolTaskScheduler scheduler, ExchangeRateService service,
                                 Duration interval) {

        /** 首次立即预热，后续每轮从上一次执行结束后计算等待时间。 */
        @EventListener(ApplicationReadyEvent.class)
        public void start() {
            scheduler.scheduleWithFixedDelay(service::refresh, Instant.now(), interval);
        }
    }
}
