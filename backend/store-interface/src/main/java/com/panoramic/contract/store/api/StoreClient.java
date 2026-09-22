package com.panoramic.contract.store.api;

import com.panoramic.contract.store.dto.ShopAuditDTO;
import com.panoramic.contract.store.dto.ShopPageQueryDTO;
import com.panoramic.contract.store.dto.ShopSaveDTO;
import com.panoramic.contract.store.dto.StoreGoodsLockDTO;
import com.panoramic.contract.store.dto.StoreGoodsSkuBatchQueryDTO;
import com.panoramic.contract.store.dto.StoreGoodsSkuReplaceDTO;
import com.panoramic.contract.store.dto.StoreGoodsSkuShelfDTO;
import com.panoramic.contract.store.dto.StoreGoodsSpuBatchQueryDTO;
import com.panoramic.contract.store.dto.StoreGoodsSpuDetailQueryDTO;
import com.panoramic.contract.store.dto.StoreGoodsSpuPageQueryDTO;
import com.panoramic.contract.store.dto.StoreGoodsSpuCrossShopPageQueryDTO;
import com.panoramic.contract.store.dto.StoreGoodsSpuFacetQueryDTO;
import com.panoramic.contract.store.dto.StoreGoodsSpuSaveDTO;
import com.panoramic.contract.store.dto.StoreGoodsSpuUpdateDTO;
import com.panoramic.contract.store.dto.StoreGoodsStockBatchUpdateDTO;
import com.panoramic.contract.store.dto.StoreGoodsStockPageQueryDTO;
import com.panoramic.contract.store.dto.StoreGoodsStockUpdateDTO;
import com.panoramic.contract.store.dto.StoreStockDeductDTO;
import com.panoramic.contract.store.vo.PageResult;
import com.panoramic.contract.store.vo.ShopOptionVO;
import com.panoramic.contract.store.vo.ShopVO;
import com.panoramic.contract.store.vo.StoreGoodsSkuSnapshotVO;
import com.panoramic.contract.store.vo.StoreGoodsSpuCrossShopPageItemVO;
import com.panoramic.contract.store.vo.StoreGoodsSpuFacetVO;
import com.panoramic.contract.store.vo.StoreGoodsSpuPageItemVO;
import com.panoramic.contract.store.vo.StoreGoodsSpuPlatformDetailVO;
import com.panoramic.contract.store.vo.StoreGoodsStockPageItemVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * store（店铺业务域，下沉纯域）内部 Feign 客户端。
 * <p>本切片起 store 域只持 {@code store_shop}（账号店同 ID：店铺主键 == 店主账号 id），不再向页面暴露公网路由，
 * 由各端 BFF 经本接口内部调用。规约（见 docs/contracts/store.md 与 cross-cutting.md）：
 * <ul>
 *   <li>入参/出参 DTO 与接口同源维护在 store-interface（store 域服务端、store-bff/admin/mall-bff 客户端引用同一份类型）；</li>
 *   <li>方法直接返回业务结果类型（不包 RespData），错误走异常统一传播；</li>
 *   <li>调用经 {@link StoreFeignConfiguration} 附带信任头 + 透传主身份 + 错误解码；熔断由<b>调用方</b>经 Nacos
 *       {@code feign-circuitbreaker.yml} 配置提供，不在本类。</li>
 * </ul>
 * <b>接口按能力通用、不按端分侧</b>（cross-cutting 第 22/23 条）：同一能力只有一条路径，路径段与方法名里
 * 不出现端别子段；数据作用域（{@code storeId}）**不进路径段**，只是入参 DTO 的一个字段——
 * <b>传了就按它筛，没传就是不限定</b>。域内不判身份、不读 {@code X-User-Type} 判权（该头只用于审计留痕），
 * 「谁该传什么」全在端 BFF：值取自登录态（{@code LoginUser.getId()}），<b>禁止</b>从前端入参透传。
 * 各能力的作用域必填性见 {@code docs/contracts/store.md} 第三节。
 * 服务端路径与映射需与 store 域内部控制器一一对应（前缀 /internal/store）。</p>
 */
@FeignClient(name = "store", contextId = "storeClient",
        path = "/internal/store", configuration = StoreFeignConfiguration.class)
public interface StoreClient {

    // ---- 店铺 ----

    /**
     * 店铺详情（无店主账号信息）。
     * <p><b>一条路径服务所有调用方</b>：店主侧传自己的 {@code storeId}（= 账号 id）、管理端与 C 端
     * 跨店传 {@code id} 不传作用域——差别只在调用方传什么，域内不分侧。
     * <p>⚠ 查不到一律<b>返空</b>（HTTP 200 空 body → Feign 解出 {@code null}），域内不抛；
     * 「未开店」是正常态，不是故障。调用方各自重判：store-bff 判「未开店」、admin 转本层
     * 「店铺不存在」、mall-bff 判「店铺不可见」（见 store.md 第三节）。
     */
    @GetMapping("/shops/{id}")
    ShopVO getShop(@PathVariable("id") Long id);

    /**
     * 保存草稿（{@code dto.storeId} 必填：无店则建 id=storeId 的店；待审核/已通过状态机守卫）
     */
    @PostMapping("/shops/save")
    void saveShop(@RequestBody ShopSaveDTO dto);

    /**
     * 提交审核（{@code dto.storeId} 必填：完整资质校验 → 待审核）
     */
    @PostMapping("/shops/submit")
    void submitShop(@RequestBody ShopSaveDTO dto);

    /**
     * 店铺分页（状态/关键字筛选，全量——本能力存在合法全量视角，故无作用域字段）
     */
    @GetMapping("/shops/page")
    PageResult<ShopVO> pageShops(@SpringQueryMap ShopPageQueryDTO dto);

    /**
     * 审核通过/驳回（仅对待审核条件更新）
     */
    @PostMapping("/shops/{id}/audit")
    void auditShop(@PathVariable("id") Long id, @RequestBody ShopAuditDTO dto);

    /**
     * 店铺下拉选项（店铺商品列表「按店铺筛选」用）。
     * <p>不按审核状态过滤：未审核通过的店铺本就没有商品。</p>
     */
    @GetMapping("/shops/options")
    List<ShopOptionVO> listShopOptions();

    // ---- 店铺在售商品（店主侧写读：作用域 storeId 必填，无全量视角）----
    // 作用域在 DTO 字段里（不进路径段）；值由端 BFF 从登录态取并无条件覆盖（cross-cutting 第 22 条）。
    // 管理端的跨店视角走本文件尾部的跨店通用分页（pageStoreGoodsCrossShop），不在这几条上开口子。

    /**
     * 我的商品分页（仅 {@code dto.storeId} 名下的商品）
     */
    @GetMapping("/goods/spu/page")
    PageResult<StoreGoodsSpuPageItemVO> pageStoreGoods(@SpringQueryMap StoreGoodsSpuPageQueryDTO dto);

    /**
     * 店铺商品详情（含 SKU 列表与锁定信息）。
     * <p>作用域 {@code query.storeId} <b>可空</b>（该能力存在合法全量视角）：传了就按它筛
     * （店主侧传自己的店，不属本店与不存在同样报 400、不泄露存在性），没传就是不限定（管理端 / C 端跨店详情）。
     * <p>出参统一为管理端超集 {@link StoreGoodsSpuPlatformDetailVO}（含 {@code lockUser} / {@code storeName}）——
     * <b>域返回的字段不等于可以对外暴露</b>：C 端与商户端输出前必须由各自端 BFF <b>逐字段手工映射裁剪</b>
     * （见 store.md 第三节、cross-cutting 第 17/19/20 条）。域内不做可见性判断。
     */
    @GetMapping("/goods/spu/{id}")
    StoreGoodsSpuPlatformDetailVO storeGoodsDetail(@PathVariable("id") Long id,
                                                   @SpringQueryMap StoreGoodsSpuDetailQueryDTO query);

    /**
     * 新增商品（返回新商品 id；SKU 一律以下架态落库）。{@code dto.storeId} 必填
     */
    @PostMapping("/goods/spu")
    Long saveStoreGoods(@RequestBody StoreGoodsSpuSaveDTO dto);

    /**
     * 修改商品（{@code dto.storeId} 必填；存在上架 SKU 时规格配置只读；centerVersion 非空则刷新关联版本戳）
     */
    @PutMapping("/goods/spu/{id}")
    void updateStoreGoods(@PathVariable("id") Long id, @RequestBody StoreGoodsSpuUpdateDTO dto);

    /**
     * 删除商品（存在上架 SKU 时拒绝；否则软删并级联软删其下全部 SKU）。
     * <p>⚠ 本方法刻意<b>不并 DTO</b>（cross-cutting 第 23 条的唯一例外）：路径变量之外只有 {@code storeId}
     * 一个参数，为它单独造一份一次性 DTO 纯属形式开销（与 trade-center 购物车三条同款先例）。
     * 代价是要守住「域内按 {@code id + store_id} 双条件删除」这条不变量——传参写反时命中 0 行、
     * 而不是删掉别人的商品（实现见 {@code StoreGoodsSpuServiceImpl#getOwnedOrThrow}）。
     */
    @DeleteMapping("/goods/spu/{id}")
    void deleteStoreGoods(@PathVariable("id") Long id, @RequestParam("storeId") Long storeId);

    /**
     * SKU 整单替换（{@code dto.storeId} 必填；未上架可增/改/删；已上架必须原样保留且不得缺失）
     */
    @PutMapping("/goods/spu/{id}/skus")
    void replaceStoreGoodsSkus(@PathVariable("id") Long id, @RequestBody StoreGoodsSkuReplaceDTO dto);

    /**
     * SKU 上下架（{@code dto.storeId} 必填；驱动所属 SPU 状态联动）
     */
    @PutMapping("/goods/spu/{spuId}/skus/{skuId}/shelf")
    void updateStoreGoodsSkuShelf(@PathVariable("spuId") Long spuId, @PathVariable("skuId") Long skuId,
                                  @RequestBody StoreGoodsSkuShelfDTO dto);

    /**
     * SKU 库存分页（仅 {@code dto.storeId} 名下；按 SKU 平铺一行一条，
     * 支持商品名 / SKU 编码关键字、上下架筛选、仅看低库存）。
     * <p>出参的 {@code stock} 是库存表里的总库存；可用库存 = {@code stock}
     * （{@code lockedStock} 已于 2026-09-21 废弃，不参与口径、不再写入）。</p>
     */
    @GetMapping("/goods/stock/page")
    PageResult<StoreGoodsStockPageItemVO> pageSkuStock(@SpringQueryMap StoreGoodsStockPageQueryDTO dto);

    /**
     * 改单行 SKU 库存（仅 {@code dto.storeId} 名下）；{@code warnStock} 传 null = 清除预警。
     * <p>平台锁定期 owner 侧只读（域内强制拒绝），库存行不存在时按 0 行处理。</p>
     */
    @PutMapping("/goods/stock/{skuId}")
    void updateSkuStock(@PathVariable("skuId") Long skuId, @RequestBody StoreGoodsStockUpdateDTO dto);

    /**
     * 批量设置整批 SKU 的总库存（{@code dto.storeId} 必填，单条 IN 更新、不逐行）；平台锁定期只读。
     * <p>不属于本店的 SKU 直接拒绝（不静默跳过）。</p>
     */
    @PutMapping("/goods/stock/batch")
    void batchUpdateSkuStock(@RequestBody StoreGoodsStockBatchUpdateDTO dto);

    // ---- 跨店通用（无作用域锚点，限定条件全由调用方自设）----
    // 分页（/goods/cross-shop/spu/page）与聚合（/goods/facets）**跨店通用**：不传 store_id 锚点，
    //   —— admin BFF「店铺商品管理」走全量，mall-bff C 端浏览固定传 shopStatus/shelfStatus/lockStatus。
    // 商品详情（/goods/spu/{id} 不传作用域）与批量详情（/goods/spu/batch）同样跨店只读。
    // 锁定语义：锁定 → 名下 SKU 全部级联下架、SPU 随之推导为下架；锁定期 owner 侧整行只读；
    // 仅平台可解锁，解锁不自动恢复上架（由店主手动重新上架）。

    /**
     * 店铺商品分页（<b>跨店通用</b>：不传 store_id 锚点，调用方自设限定条件）。
     * <p>admin BFF 用于「店铺商品管理」（不限条件，全量）；
     * mall-bff 用于 C 端商品浏览（固定传 shopStatus=2 + shelfStatus=1 + lockStatus=0）。</p>
     * <p>⚠ 用 {@code POST + @RequestBody} 而非 query 参数：{@code categoryIds}/{@code brandIds} 是集合，
     * {@code @SpringQueryMap} 对集合字段的序列化口径不确定，走 body 规避。</p>
     */
    @PostMapping("/goods/cross-shop/spu/page")
    PageResult<StoreGoodsSpuCrossShopPageItemVO> pageStoreGoodsCrossShop(
            @RequestBody StoreGoodsSpuCrossShopPageQueryDTO dto);

    /**
     * 商品筛选维度聚合（分类 / 品牌，各带命中数）。
     * <p>⚠ 口径见 {@link StoreGoodsSpuFacetVO}：两维度互斥排除自身。</p>
     */
    @PostMapping("/goods/facets")
    StoreGoodsSpuFacetVO crossShopFacets(@RequestBody StoreGoodsSpuFacetQueryDTO dto);

    /**
     * 店铺商品<b>批量</b>详情（跨店，不校验归属；含 SKU 列表与锁定信息，只读）。
     * <p>调用方是 <b>mall-bff 的购物车列表</b>：一次取回多个 SPU 的详情，避免逐行调
     * {@link #storeGoodsDetail} 造成 N+1。通用于管理端与 C 端，差别只在调用方传入的 id 集合。</p>
     * <p>⚠ 查不到的 id（SPU 已删除）<b>跳过、不出现在出参里</b>，不抛异常——与单条版
     * {@link #storeGoodsDetail} 取不到即 400 的语义不同：批量场景逐行报错会让整个列表
     * 取不回来，故由调用方按「拿不到 = 商品不存在」处理。</p>
     * <p>⚠ 出参是<b>管理端超集</b>（含 {@code lockUser} / {@code goodsSpuId} 等），C 端输出前必须由
     * mall-bff 裁剪——见 docs/contracts/store.md 第三节与 cross-cutting 第 17/19/20 条。域内不做可见性判断。</p>
     * <p>⚠ 用 {@code POST + @RequestBody} 而非 query 参数：{@code spuIds} 是集合，走 body 规避
     * {@code @SpringQueryMap} 的集合序列化口径问题（与 {@link #pageStoreGoodsCrossShop} / {@link #crossShopFacets} 同口径）。</p>
     */
    @PostMapping("/goods/spu/batch")
    List<StoreGoodsSpuPlatformDetailVO> batchSpuDetail(@RequestBody StoreGoodsSpuBatchQueryDTO dto);

    /**
     * 锁定商品（原因必填）：写锁定字段 + 名下 SKU 级联下架 → SPU 推导为下架
     */
    @PostMapping("/goods/spu/{id}/lock")
    void lockStoreGoods(@PathVariable("id") Long id, @RequestBody StoreGoodsLockDTO dto);

    /**
     * 解锁商品：清空锁定字段；<b>不恢复上架</b>（SKU 保持下架，需店主手动上架）
     */
    @PostMapping("/goods/spu/{id}/unlock")
    void unlockStoreGoods(@PathVariable("id") Long id);

    // ---- 交易协作（调用方是 trade-center 的订单流水线适配器，**不是端 BFF**）----
    // ⚠ 本节是 store 域**唯一**被域间调用的能力（cross-cutting 第 24 条）：trade-center → store 是全仓
    //   唯一的跨域调用边，这也让「各域只依赖自己的 <域>-interface」的编译期守卫对它失效——只能靠登记 + 人工核对。
    // ⚠ 三条都**无作用域锚点**（没有 storeId 字段）：调用方手上只有 skuId 与订单号，按**资源 id** 操作；
    //   域内**不判身份、不做权限判断、不校验店铺归属**（身份头只用于审计留痕）。
    // ⚠ 库存不足**不是 HTTP 错误**（R18）：deductStock 返回 false（HTTP 200），由交易域翻成业务错误；
    //   按单回补是补偿路径，**宁可不做也不能炸**（R19）——入参空 / 该单没扣过一律 no-op，不抛异常。
    // ⚠ 熔断口径与其它域调用一致：业务 4xx 还原为 ServiceException（调用方熔断忽略），5xx 计入失败率。
    //   熔断线程下身份头可能丢失 → 审计留空（不影响业务，见第 24 条）。

    /**
     * 交易侧 SKU 快照批量读（落订单明细快照用，只读）。
     * <p>调用方是 <b>trade-center 的订单流水线适配器</b>：按 SKU id 集合一次取回
     * 名称 / 图片 / 规格 / 价格 / 上下架 / 锁定 / 店铺状态 / 可用库存，避免逐行回查。
     * <b>SQL 条数与 {@code skuIds} 个数无关</b>（SKU / SPU / 店铺名 / 店铺状态 / 可用库存 各一次批量查）。</p>
     * <p>⚠ 查不到的 skuId（SKU 或所属 SPU 已删除）<b>跳过、不出现在出参里</b>，不抛异常——
     * 与 {@link #storeGoodsDetail} 取不到即 400 刻意不同：下单流水线要自己判「拿不到 = 商品不存在、
     * 该行不可购买」，而不是让整批快照取不回来。</p>
     * <p>⚠ 出参 {@link StoreGoodsSkuSnapshotVO} 是<b>交易视角</b>：{@code specAttrs} 是已解析的规格组合
     * （不透出库里的 JSON 串）、{@code shopStatus} 为 null 表示无该店铺行（<b>不是</b>草稿态 0）；
     * 域内<b>不判可购买性</b>，各维度状态如实给出，判据在交易侧。</p>
     */
    @PostMapping("/goods/trade/sku/batch")
    List<StoreGoodsSkuSnapshotVO> tradeSkuSnapshotBatch(@RequestBody StoreGoodsSkuBatchQueryDTO dto);

    /**
     * 扣减库存（下单出库）：库存表上一条原子条件更新，<b>影响行数是唯一判据</b>。
     * <p>调用方是 <b>trade-center 的库存适配器</b>。扣减成功返回 {@code true} 并记一条出库流水；
     * <b>库存不足返回 {@code false}（HTTP 200），不记流水、不抛异常</b>——库存不足不是故障，
     * 由交易域的校验步骤翻成 400「库存不足」（R18）。</p>
     * <p>不判平台锁定、不判上下架、不校验店铺归属（R19）：可见性由交易侧的商品校验判，
     * 本接口只管库存数够不够；库存行不存在同样按「不足」处理。</p>
     * <p>⚠ 同一个 {@code orderNo + skuId} 重复扣减会撞流水唯一键（{@code uk_order_sku_kind}）：
     * 事务整体回滚并回 500。本域<b>不做</b>「已扣过就跳过」的隐式兜底，⚠ <b>故调用方不得给本方法配重试</b>
     * ——重试不是幂等的，第二次就是 500（Feign/R4J 重试、`TimeLimiter` 的 `cancel-running-future`
     * 只取消本地等待、停不下服务端已提交的扣减）。⚠ 触发因子也**不止**「调用方重发」：订单号在同一秒内
     * 可能被复用（失败的提交随事务回滚、单号放回池子），届时「新的一单」拿同一单号扣同一 SKU 同样撞键
     * （跨服务隐式契约见 cross-cutting 第 24 条）。</p>
     */
    @PostMapping("/goods/trade/stock/deduct")
    boolean deductStock(@RequestBody StoreStockDeductDTO dto);

    /**
     * 按订单号回补库存（取消 / 超时 / 支付失败后的补偿路径）。
     * <p>调用方是 <b>trade-center 的库存适配器</b>。回补该单<b>尚未回补过</b>的每一条出库流水
     * （无守卫 {@code stock = stock + 数量}）并记一条回补流水；已回过补的跳过——
     * 故<b>重复调用是 no-op</b>。全流程在同一事务内，库存与流水同成同败。</p>
     * <p>⚠ <b>补偿路径宁可不做也不能炸</b>（R19）：{@code orderNo} 为空、该单没有出库流水、
     * 库存行已不存在，都<b>静默成功</b>（不报错、不抛异常）——它在失败回滚链路上跑，
     * 抛异常会把已经失败的订单再炸一次，反而丢掉本该归还的库存。</p>
     * <p>⚠ 路径变量就一个 {@code orderNo}，故<b>保持裸参</b>（cross-cutting 第 23 条：路径变量不并入 DTO，
     * 单个路径变量可裸类型），不为它单造一份一次性 DTO。</p>
     */
    @PostMapping("/goods/trade/stock/revert-by-order/{orderNo}")
    void revertStockByOrder(@PathVariable("orderNo") String orderNo);
}
