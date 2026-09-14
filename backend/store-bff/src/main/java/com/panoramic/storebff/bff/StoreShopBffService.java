package com.panoramic.storebff.bff;

import com.panoramic.common.exception.ServiceException;
import com.panoramic.common.feign.BffFeignCall;
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
     * 统一编排执行：业务异常（400 参数/业务、403、404）透传，其余（熔断/连接/序列化等）降级为友好提示。
     * <p>剥 cause 链与降级的实现已抽到 common 的 {@link BffFeignCall}（与 admin BFF 共用一份），
     * 本类只传自己的降级文案。</p>
     */
    private <T> T call(Supplier<T> action) {
        return BffFeignCall.call("store", DEGRADE_MSG, action);
    }
}
