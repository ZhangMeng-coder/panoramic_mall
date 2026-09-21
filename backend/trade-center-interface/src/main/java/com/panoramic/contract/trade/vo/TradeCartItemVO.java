package com.panoramic.contract.trade.vo;

import lombok.Data;

/**
 * 购物车行响应（trade-center 域内部接口与 mall-bff 同源共享）。
 * <p>⚠ <b>只有引用与购物车自身的字段，没有任何商品快照</b>（名称 / 价格 / 图 / 店铺）：
 * 那些都在 store 域，且**必须以读取时刻的实时值为准**（加购时快照的价格，到结算时早已过期）。
 * mall-bff 拿 {@code spuId} 去 store 域批量取详情后再拼成页面 VO —— 本 VO 不是页面形状。</p>
 * <p>⚠ <b>不含 {@code customerId}</b>：它由调用方传入、是查询条件本身，回传只会让调用方误以为
 * 「域侧判过归属」。也不含无效标记：可见性判定不在域内做（见
 * {@code docs/contracts/trade-center.md} 第一节最后一条）。</p>
 */
@Data
public class TradeCartItemVO {

    /**
     * 购物车行 id（删除 / 改数量 / 改选中的作用对象）
     */
    private Long id;

    /**
     * 店铺商品 SPU id（store 域）
     */
    private Long spuId;

    /**
     * 店铺商品 SKU id（store 域）
     */
    private Long skuId;

    /**
     * 数量：1..999
     */
    private Integer quantity;

    /**
     * 选中状态：true=选中，false=未选中
     * <p>服务端持久化（不是前端本地状态）：换设备 / 换标签页后选中态保持一致，
     * 结算口径也才有唯一的服务端来源。</p>
     */
    private Boolean selected;
}
