package com.tradeexample.exchange.controller;

import com.tradeexample.common.api.ApiResponse;
import com.tradeexample.exchange.model.ExchangeRateSnapshot;
import com.tradeexample.exchange.service.ExchangeRateService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 对外提供 Redis 汇率快照的只读查询。 */
@RestController
public class ExchangeRateController {

    private final ExchangeRateService service;

    /** 注入汇率查询服务。 */
    public ExchangeRateController(ExchangeRateService service) {
        this.service = service;
    }

    /** 返回有效报价和来源发布时间、成功抓取时间及最近尝试时间。 */
    @GetMapping("/api/v1/exchange-rates")
    public ApiResponse<ExchangeRateSnapshot> query() {
        return ApiResponse.success(service.query());
    }
}
