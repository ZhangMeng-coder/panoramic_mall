package com.panoramic.store.controller;

import com.panoramic.common.vo.RespData;
import com.panoramic.contract.store.dto.StoreGoodsSkuBatchQueryDTO;
import com.panoramic.contract.store.dto.StoreStockDeductDTO;
import com.panoramic.contract.store.vo.StoreGoodsSkuSnapshotVO;
import com.panoramic.store.service.StoreGoodsSkuStockService;
import com.panoramic.store.service.StoreGoodsSpuService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * store 域<b>交易协作</b>内部接口（域间调用）。
 * <p>调用方是 <b>trade-center</b>（订单流水线适配器）而不是端 BFF——这是全仓<b>唯一的跨域调用边</b>，
 * 登记在 {@code docs/contracts/cross-cutting.md} 第 24 条。
 * 路径子段带 {@code trade} 与端 BFF 那批能力区分开；前缀 {@code /internal/store} 与 {@link GoodsController}
 * 同源，仍只在内网可达、不暴露公网路由。</p>
 * <p><b>不判身份、不做权限判断、无数据作用域锚点</b>：三条能力都按<b>资源 id</b>（skuId / orderNo）操作，
 * 入参里没有 {@code storeId}，故此处<b>不引入任何 storeId 校验</b>——身份头（{@code X-User-Id} /
 * {@code X-User-Type}）只供审计字段自动填充留痕，读身份不等于做鉴权。</p>
 * <p>出参一律包 {@code RespData<T>}：业务失败走 <b>HTTP 200 + {code,msg}</b>（不计入调用方熔断），
 * 只有兜底异常才是 HTTP 500（唯一计入失败率的信号，见 cross-cutting 第 2、13 条）。
 * ⚠ 唯一的「非错误失败」是 {@link #deductStock} 的 {@code false}——库存不足是正常业务结果（R18），
 * 故它是 <b>{@code code=200} + {@code data=false}</b>，<b>不得</b>写成 {@code code=400}：
 * 后者会让交易侧的库存校验步骤收到异常而不是 {@code false} 分支。</p>
 * <p>⚠ 完整路径必须与 {@code StoreClient} 的交易协作三条<b>逐字一致</b>（drift-check 会核对两端）。</p>
 */
@RestController
@RequestMapping("/internal/store/goods/trade")
@RequiredArgsConstructor
public class GoodsTradeController {

    private final StoreGoodsSpuService storeGoodsSpuService;
    private final StoreGoodsSkuStockService storeGoodsSkuStockService;

    /**
     * SKU 快照批量读（下单落订单明细快照用，只读；查不到的 id 跳过不报错）
     */
    @PostMapping("/sku/batch")
    public RespData<List<StoreGoodsSkuSnapshotVO>> tradeSkuSnapshotBatch(
            @Validated @RequestBody StoreGoodsSkuBatchQueryDTO dto) {
        return RespData.success(storeGoodsSpuService.platformSkuSnapshotBySkuIds(dto.getSkuIds()));
    }

    /**
     * 扣减库存：成功 {@code true}；库存不足 {@code false}（{@code code=200}，不是错误）。
     * <p>不判锁定、不判上下架、不校验店铺归属（可见性由交易侧判，R19）。</p>
     */
    @PostMapping("/stock/deduct")
    public RespData<Boolean> deductStock(@Validated @RequestBody StoreStockDeductDTO dto) {
        return RespData.success(storeGoodsSkuStockService.deduct(
                dto.getSkuId(), dto.getQuantity(), dto.getOrderNo()));
    }

    /**
     * 按订单号回补库存（补偿路径）：幂等、无守卫，入参空或该单没扣过一律 no-op。
     * <p>⚠ 刻意<b>不</b>做「订单不存在就报错」的校验——它是失败链路上的补偿，报错会丢掉本该归还的库存（R19）。
     * 路径变量就一个 {@code orderNo}，直接透传 service。</p>
     */
    @PostMapping("/stock/revert-by-order/{orderNo}")
    public RespData<Void> revertStockByOrder(@PathVariable("orderNo") String orderNo) {
        storeGoodsSkuStockService.revertByOrder(orderNo);
        return RespData.success();
    }
}
