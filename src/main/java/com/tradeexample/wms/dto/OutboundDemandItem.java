package com.tradeexample.wms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** 同一请求内，采购单号和产品编号相同的需求只能出现一次。 */
public record OutboundDemandItem(

        @NotBlank(message = "采购单号不能为空")
        @Size(max = 64, message = "采购单号不能超过64个字符")
        String purchaseOrderNo,

        @NotBlank(message = "产品编号不能为空")
        @Size(max = 64, message = "产品编号不能超过64个字符")
        String productNo,

        @NotNull(message = "需求箱数不能为空")
        @Positive(message = "需求箱数必须大于0")
        Integer boxCount
) {
}
