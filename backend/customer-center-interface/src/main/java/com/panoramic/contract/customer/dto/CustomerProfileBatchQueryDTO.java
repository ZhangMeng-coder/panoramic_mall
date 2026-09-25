package com.panoramic.contract.customer.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 顾客资料<b>批量</b>查询参数（customer-center 域内部接口与端 BFF 同源共享）。
 * <p>用途：BFF 渲染<b>评价列表</b>时一次取回本页评价人的昵称 / 头像，替代逐条调
 * {@link com.panoramic.contract.customer.api.CustomerCenterClient#getProfile}（一页 10 条即 10 次跨服务往返），消除 N+1。</p>
 * <p>⚠ 本 DTO 经 Feign 以 {@code POST + @RequestBody} 传输（不做 query string 序列化），
 * 与 store 域 {@code StoreGoodsSpuBatchQueryDTO} 的集合字段同口径。</p>
 * <p>⚠ 出参口径与单条 {@code getProfile} <b>刻意不同</b>：查不到的 id <b>跳过</b>、不出现在出参里，
 * 而不是给每人补一个「仅含 id 的空 VO」——批量场景下后者会让调用方分不清「真有一条空资料」与
 * 「压根没这个人」。调用方按「拿不到 = 无资料」处理，<b>不整批失败</b>（评价列表不能因为某个顾客
 * 资料缺失就整页取不回来）。</p>
 */
@Data
public class CustomerProfileBatchQueryDTO {

    /**
     * 待查顾客账号 id 集合（= {@code mall_user.id}，必填，1 ~ 200 个）。
     * <p>上限 200：调用方一页最多几十行、去重后只会更少，200 是余量，同时也是 {@code IN} 列表长度的兜底。</p>
     */
    @NotEmpty(message = "请至少选择一个顾客")
    @Size(max = 200, message = "一次最多查询200个顾客")
    private List<Long> customerIds;
}
