package com.tradeexample.imagesync.vo;

/** 返回已创建的任务 ID 和产品类型，供调用方按任务查询进度。 */
public record ImageSyncTaskCreatedVO(long taskId, String productType) {
}
