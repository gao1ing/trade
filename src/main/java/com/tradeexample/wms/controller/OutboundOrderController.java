package com.tradeexample.wms.controller;

import com.tradeexample.common.api.ApiResponse;
import com.tradeexample.wms.dto.CancelOutboundOrderRequest;
import com.tradeexample.wms.dto.CreateOutboundOrderRequest;
import com.tradeexample.wms.dto.RegenerateOutboundOrderRequest;
import com.tradeexample.wms.dto.SubmitOutboundOrderRequest;
import com.tradeexample.wms.service.OutboundOrderService;
import com.tradeexample.wms.vo.OutboundOrderDetailVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 出库草稿创建和当前修订查询接口。 */
@RestController
@RequestMapping("/api/v1/outbound-orders")
@RequiredArgsConstructor
public class OutboundOrderController {

    private final OutboundOrderService outboundOrderService;

    /** 按请求分配库存并创建一张新草稿；重复创建会生成新单据。 */
    @PostMapping
    public ApiResponse<OutboundOrderDetailVO> createDraft(
            @Valid @RequestBody CreateOutboundOrderRequest request) {

        return ApiResponse.success(
                outboundOrderService.createDraft(request)
        );
    }

    /** 提交指定修订的出库单；同一修订已提交时返回当前明细。 */
    @PostMapping("/{id}/submit")
    public ApiResponse<OutboundOrderDetailVO> submit(
            @PathVariable("id")
            @Positive(message = "出库单ID必须大于0")
            Long id,
            @Valid @RequestBody SubmitOutboundOrderRequest request) {

        return ApiResponse.success(outboundOrderService.submit(id, request));
    }

    /** 作废指定修订；已提交的单据在本次事务中恢复原批次库存。 */
    @PostMapping("/{id}/cancel")
    public ApiResponse<OutboundOrderDetailVO> cancel(
            @PathVariable("id")
            @Positive(message = "出库单ID必须大于0")
            Long id,
            @Valid @RequestBody CancelOutboundOrderRequest request) {

        return ApiResponse.success(outboundOrderService.cancel(id, request));
    }

    /** 使用新需求重新分配库存并生成下一修订，旧明细保留供追溯。 */
    @PostMapping("/{id}/regenerate")
    public ApiResponse<OutboundOrderDetailVO> regenerate(
            @PathVariable("id")
            @Positive(message = "出库单ID必须大于0")
            Long id,
            @Valid @RequestBody RegenerateOutboundOrderRequest request) {

        return ApiResponse.success(outboundOrderService.regenerate(id, request));
    }

    /** 查询主表及其当前修订的分配明细。 */
    @GetMapping("/{id}")
    public ApiResponse<OutboundOrderDetailVO> getDetail(
            @PathVariable("id")
            @Positive(message = "出库单ID必须大于0")
            Long id) {

        return ApiResponse.success(
                outboundOrderService.getDetail(id)
        );
    }
}