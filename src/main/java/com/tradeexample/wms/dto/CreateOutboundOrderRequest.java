package com.tradeexample.wms.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/** 创建一个仓库的出库草稿，一次可以提交多项采购单和产品需求。 */
public record CreateOutboundOrderRequest(

        @NotBlank(message = "仓库编码不能为空")
        @Size(max = 32, message = "仓库编码不能超过32个字符")
        String warehouseCode,

        @NotNull(message = "出库日期不能为空")
        LocalDate outboundDate,

        @NotEmpty(message = "出库需求不能为空")
        List<@NotNull(message = "需求项不能为空") @Valid OutboundDemandItem> demands
) {
}
