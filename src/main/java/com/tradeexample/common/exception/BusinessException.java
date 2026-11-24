package com.tradeexample.common.exception;

import lombok.Getter;


/** 携带接口错误码和业务原因，由统一异常处理器生成响应。 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    /** 使用错误码约定的默认提示。 */
    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.getDefaultMessage());
    }

    /** 保留错误分类，并用当前操作的失败原因作为提示。 */
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}