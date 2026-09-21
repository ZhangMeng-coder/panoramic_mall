package com.panoramic.contract.store.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 店铺商品批量详情查询参数（store 域内部接口与端 BFF 同源共享）。
 * <p>用途：mall-bff 渲染<b>购物车列表</b>时一次取回多个 SPU 的详情，替代逐行调单条详情
 * （购物车行数上限 100，逐行调用即 100 次往返），消除 N+1。</p>
 * <p>⚠ 本 DTO 经 Feign 以 {@code POST + @RequestBody} 传输（不做 query string 序列化），
 * 与 {@link StoreGoodsSpuCrossShopPageQueryDTO} 的集合字段同口径。</p>
 */
@Data
public class StoreGoodsSpuBatchQueryDTO {

    /**
     * 待查店铺商品（SPU）id 集合（必填，1 ~ 200 个）。
     * <p>上限 200：调用方购物车单片上限 100 行、去重后只会更少，200 是余量同时也是
     * {@code IN} 列表长度的兜底。查不到的 id（SPU 已删除）<b>跳过</b>，不出现在出参里。</p>
     */
    @NotEmpty(message = "请至少选择一个商品")
    @Size(max = 200, message = "一次最多查询200个商品")
    private List<Long> spuIds;
}
