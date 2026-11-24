package com.tradeexample;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 启动外贸业务协同管理应用，从根包扫描各业务模块。 */
@SpringBootApplication
public class TradeExampleApplication {

    /** 创建 Spring 应用上下文并启动内嵌 HTTP 服务。 */
    public static void main(String[] args) {
        SpringApplication.run(TradeExampleApplication.class, args);
    }

}
