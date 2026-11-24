package com.tradeexample.common.exception;

import lombok.Getter;

/**
 * 接口错误分类
 */
@Getter
public enum ErrorCode {

    INVALID_ARGUMENT(400, "请求参数不合法"),

    RESOURCE_NOT_FOUND(404, "请求资源不存在"),

    BUSINESS_CONFLICT(409, "当前业务状态不允许此操作"),

    INTERNAL_ERROR(500, "服务处理失败，请稍后重试"),

    EXCHANGE_RATE_NOT_READY(503, "尚未取得可用的汇率报价"),

    EXCHANGE_RATE_EXPIRED(503, "汇率报价已超过最大允许年龄"),

    EXCHANGE_RATE_REDIS_UNAVAILABLE(503, "汇率缓存暂时不可用");

    private final int httpStatus;

    private final String defaultMessage;

    /** 关联 HTTP 状态与省略业务详情时使用的默认提示。 */
    ErrorCode(int httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }
}