package com.panoramic.trade.order.domain.port;

import java.math.BigDecimal;
import java.util.Map;

/**
 * SKU 快照：**下单那一刻**从 store 域读到的商品可见性 + 价格 + 归属店铺（只读模型）。
 *
 * <p>⚠ 它是「跨域只读投影」，不是 store 域的实体：本域结构上拿不到 store 的任何类型（各域只依赖自己的
 * {@code <域>-interface}），所以这里按订单的需要**只声明用得到的列**，由适配器负责从真实商品接口映射过来。
 * 将来接真实现时改适配器即可，domain 层不动。</p>
 *
 * <p>⚠ 四个开关（{@code shopApproved} / {@code spuOnShelf} / {@code skuOnShelf} / {@code spuLocked}）
 * 是 <b>goods-check 步骤的四条拒绝分支</b>，一个都不能少：它们分属不同事实（店铺审核态、SPU 上下架、
 * SKU 上下架、平台锁定），合成一个 boolean 就再也说不清「为什么不可购买」，而错误提示与排查都依赖这个区分
 * （锁定是平台行为，与店主自己下架不是一回事）。</p>
 *
 * <p>⚠ {@code price} 在这里出现是**刻意的**：它同时是 goods-check 之后的 price-compute 的输入源，
 * 而价格不允许由调用方（BFF）传入——价格必须由服务端从商品域读，否则顾客改包就能改价。</p>
 *
 * @param spuId        店铺 SPU id
 * @param skuId        店铺 SKU id
 * @param storeId      所属店铺 id（拆单按它分组，裁定 D3）
 * @param storeName    店铺名（拆单时由编排层写进订单，商品快照本身不落库）
 * @param goodsName    商品名（下单时冻进订单项，裁定 D15：之后不再回查商品）
 * @param mainImage    商品主图 URL（同上，冻结）
 * @param specAttrs    规格属性（如「颜色=黑」；同上，冻结）
 * @param price        当前单价，判定「商品可购买」通过后才允许计价
 * @param shopApproved 店铺是否已审核通过
 * @param spuOnShelf   SPU 是否上架
 * @param skuOnShelf   SKU 是否上架
 * @param spuLocked    SPU 是否被平台锁定（锁定 = 整行只读，名下的 SKU 也已级联下架）
 */
public record SkuSnapshot(Long spuId,
                          Long skuId,
                          Long storeId,
                          String storeName,
                          String goodsName,
                          String mainImage,
                          Map<String, String> specAttrs,
                          BigDecimal price,
                          boolean shopApproved,
                          boolean spuOnShelf,
                          boolean skuOnShelf,
                          boolean spuLocked) {
}
