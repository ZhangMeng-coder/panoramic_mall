package com.panoramic.contract.store.dto;

import com.panoramic.contract.store.vo.StoreGoodsEvaluationSkuVO;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 提交商品评价（store 域内部接口与端 BFF 同源共享）。
 * <p>评价挂在 <b>商品 SPU</b> 上（一笔订单里的一个商品一条评价），但带 <b>SKU 快照</b>：
 * 同一订单里同一 SPU 下的多个 SKU 合成一条评价，快照包含全部 SKU 行组。</p>
 * <p><b>无 storeId</b>：店铺归属由域内按 {@code spuId} 反查得到（调用方不必传，也就不存在
 * 「store_id 与 spu_id 不一致」这种输入状态）。⚠ 反查<b>不过滤逻辑删除</b>——商品下架/锁定/软删后，
 * 历史订单照常可评价。</p>
 * <p><b>「订单已完成」的门禁不在这里</b>：那是端 BFF（mall-bff）的前置业务校验，
 * 域内不做订单状态判断、不依赖 trade 域（cross-cutting 第 24 条：全仓唯一的跨域边是 trade-center → store）。
 * 域内的防线只有两道：{@code (order_no, spu_id)} 唯一键 + 评分取值 1~5。</p>
 * <p>⚠ 本 DTO <b>不是任何端的页面入参类型</b>（两端评价页面的入参都是各自 BFF 的私有 DTO），
 * 故它的 {@code @NotNull} 用<b>默认组</b>，不挂 {@link StoreScopeGroup}——见 store.md 第三节的
 * 「必填校验分两档」。</p>
 */
@Data
public class StoreGoodsEvaluationSubmitDTO {

    /**
     * 订单号（评价归属的完成态订单）
     */
    @NotBlank(message = "订单号不能为空")
    private String orderNo;

    /**
     * 被评价的商品 SPU id（店铺在售商品 id）
     */
    @NotNull(message = "商品ID不能为空")
    private Long spuId;

    /**
     * 评价人（= mall_user.id）。
     * <p>评价表有 {@code customer_id} 列，域内无从凭空得知评价人，故必须由调用方传入；
     * 值由 mall-bff 从登录态取，域内不校验是否「本人」。</p>
     */
    @NotNull(message = "顾客ID不能为空")
    private Long customerId;

    /**
     * 评分：1 ~ 5 星（整星）
     */
    @NotNull(message = "评分不能为空")
    @Min(value = 1, message = "评分不能低于1星")
    @Max(value = 5, message = "评分不能高于5星")
    private Integer score;

    /**
     * 评价文字（可空；纯文本，富文本消毒口径见 cross-cutting 第 21 条）
     */
    @Size(max = 500, message = "评价内容最多500字")
    private String content;

    /**
     * SKU 快照：本单里该 SPU 下的全部 SKU 行组（规格组合 / 单价 / 数量）。
     * <p>由调用方从订单明细带出，域侧原样落库（见 {@link StoreGoodsEvaluationSkuVO}）。</p>
     */
    @NotEmpty(message = "缺少商品规格快照")
    @Size(max = 50, message = "单个商品的规格快照最多50条")
    private List<StoreGoodsEvaluationSkuVO> skuSnapshot;
}
