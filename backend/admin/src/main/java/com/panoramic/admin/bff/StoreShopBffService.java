package com.panoramic.admin.bff;

import com.panoramic.common.feign.BffFeignCall;
import com.panoramic.contract.store.api.StoreClient;
import com.panoramic.contract.store.dto.ShopAuditDTO;
import com.panoramic.contract.store.dto.ShopPageQueryDTO;
import com.panoramic.contract.store.vo.PageResult;
import com.panoramic.contract.store.vo.ShopVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

/**
 * admin 端 BFF · 店铺管理编排。
 * <p>只做页面编排与聚合，不持有/复制 store 域任何实体与表；全部经内部 Feign 调 store 域
 * platform 接口（不传 store_id → 全量），共享 DTO/VO 同源在 store-interface（com.panoramic.contract.store），
 * 并按 Feign 规约熔断：下游业务异常（400 参数/业务）原样透传由统一异常处理还原 RespData 给页面；
 * 连接失败 / 熔断开启等降级为友好提示。不读店主账号（D6：admin 只管理店铺数据，不显示店主登录账号）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StoreShopBffService {

    /** 下游熔断/连接异常降级提示 */
    private static final String DEGRADE_MSG = "店铺服务暂不可用，请稍后重试";

    private final StoreClient storeClient;

    /**
     * 店铺分页列表（状态/关键字筛选，全量）
     */
    public PageResult<ShopVO> pageShops(ShopPageQueryDTO dto) {
        return call(() -> storeClient.pageShops(dto));
    }

    /**
     * 店铺详情（无店主账号信息）
     */
    public ShopVO shopDetail(Long id) {
        return call(() -> storeClient.shopDetail(id));
    }

    /**
     * 店铺审核：通过/驳回（驳回原因必填）；仅对「待审核」生效，防重复审核
     */
    public void auditShop(Long id, ShopAuditDTO dto) {
        call(() -> {
            storeClient.auditShop(id, dto);
            return null;
        });
    }

    /**
     * 统一编排执行：业务异常（400 参数/业务、403、404）透传，其余（熔断/连接/序列化等）降级为友好提示。
     * <p>剥 cause 链与降级的实现已抽到 common 的 {@link BffFeignCall}（admin 与 store-bff 共用一份），
     * 本类只传自己的降级文案。</p>
     */
    private <T> T call(Supplier<T> action) {
        return BffFeignCall.call("store", DEGRADE_MSG, action);
    }
}
