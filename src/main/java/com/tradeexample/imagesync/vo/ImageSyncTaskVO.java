package com.tradeexample.imagesync.vo;

/** 一类产品的任务进度。成功与跳过均计入已完成；结果未知的明细需要查询外部回执。 */
public record ImageSyncTaskVO(
        long taskId,
        String productType,
        String status,
        String resultMessage,
        long totalCount,
        long pendingCount,
        long runningCount,
        long successCount,
        long skippedCount,
        long failedCount,
        long unknownCount
) {
}