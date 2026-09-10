package com.panoramic.admin.bff;

import com.panoramic.common.exception.ServiceException;
import com.panoramic.common.store.api.StoreClient;
import com.panoramic.common.store.dto.ShopAuditDTO;
import com.panoramic.common.store.dto.ShopPageQueryDTO;
import com.panoramic.common.store.vo.PageResult;
import com.panoramic.common.store.vo.ShopVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

/**
 * admin 端 BFF · 店铺管理编排。
 * <p>只做页面编排与聚合，不持有/复制 store 域任何实体与表；全部经内部 Feign 调 store 域
 * platform 接口（不传 store_id → 全量），共享 DTO/VO 同源在 common（com.panoramic.common.store），
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
     * 统一编排执行：业务异常（400 参数/业务）透传，其余（熔断/连接/序列化等）降级为友好提示。
     * <p>⚠ Feign + 熔断会把下游抛出的业务异常包装成 {@code NoFallbackAvailableException}/
     * {@code ExecutionException}/{@code CompletionException} 等再抛出，因此须沿 cause 链定位原始
     * {@link ServiceException}；否则 400 会被误当成连接故障降级为 500「服务暂不可用」。</p>
     */
    private <T> T call(Supplier<T> action) {
        try {
            return action.get();
        } catch (Exception e) {
            // 沿 cause 链找下游业务异常，剥开熔断/异步包装层
            for (Throwable t = e; t != null; t = t.getCause()) {
                if (t instanceof ServiceException se) {
                    Integer code = se.getCode();
                    if (code != null && (code == 400 || code == 403)) {
                        throw se; // 参数/业务(400)、权限(403)：原样透传，由统一异常处理还原给页面
                    }
                    log.warn("store 域调用异常，降级处理: code={}, msg={}", se.getCode(), se.getMessage());
                    throw new ServiceException(500, DEGRADE_MSG);
                }
            }
            // 非业务异常：熔断开启 / 连接失败 / 序列化等 → 降级为友好提示
            log.error("store 域调用失败，降级处理", e);
            throw new ServiceException(500, DEGRADE_MSG);
        }
    }
}
