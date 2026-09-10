package com.panoramic.storebff.bff;

import com.panoramic.common.exception.ServiceException;
import com.panoramic.common.security.LoginUser;
import com.panoramic.common.store.api.StoreClient;
import com.panoramic.common.store.dto.ShopSaveDTO;
import com.panoramic.common.store.vo.ShopVO;
import com.panoramic.common.util.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

/**
 * 店铺端 BFF · 店主店铺编排。
 * <p>只做页面编排，不持有/复制 store 域任何实体与表（store_shop 归 store 域）；
 * 全部经内部 Feign 调 store 域 owner 接口，并按 Feign 规约熔断。
 * 「账号店同 ID」：店主账号 id 即其店铺 id（store_shop.id），本模块以
 * {@code UserContext} 当前登录店主账号 id 作为 store_id 传给域（owner 归属收敛在 BFF）。
 * mine 未开店时 store 域返回 200+null，本层原样透出 null（契约，勿改抛异常）。
 * 下游业务异常（400 参数/业务，如“审核中锁定/提交前请补全”）原样透传由统一异常处理还原
 * RespData 给页面；连接失败 / 熔断开启等降级为友好提示，避免拖垮调用方。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StoreShopBffService {

    /** 下游熔断/连接异常降级提示 */
    private static final String DEGRADE_MSG = "店铺服务暂不可用，请稍后重试";

    private final StoreClient storeClient;

    /**
     * 我的店铺（当前店主账号 id=store_id；未开店返回 null）
     */
    public ShopVO mine() {
        Long storeId = currentStoreId();
        return call(() -> storeClient.mineShop(storeId));
    }

    /**
     * 保存草稿（无店则建 id=store_id 的店；已驳回回草稿并清审核留痕）
     */
    public void saveDraft(ShopSaveDTO dto) {
        Long storeId = currentStoreId();
        call(() -> {
            storeClient.saveShop(storeId, dto);
            return null;
        });
    }

    /**
     * 提交审核（store 域做完整资质校验→待审核）
     */
    public void submit(ShopSaveDTO dto) {
        Long storeId = currentStoreId();
        call(() -> {
            storeClient.submitShop(storeId, dto);
            return null;
        });
    }

    /**
     * 取当前登录店主账号 id（== store_id），登录态缺失时拒绝
     */
    private Long currentStoreId() {
        LoginUser loginUser = UserContext.getLoginUser();
        if (loginUser == null || loginUser.getId() == null) {
            throw new ServiceException("登录已失效，请重新登录");
        }
        return loginUser.getId();
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
