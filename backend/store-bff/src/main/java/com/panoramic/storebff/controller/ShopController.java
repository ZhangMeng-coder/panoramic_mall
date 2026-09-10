package com.panoramic.storebff.controller;

import com.panoramic.common.store.dto.ShopSaveDTO;
import com.panoramic.common.store.vo.ShopVO;
import com.panoramic.common.vo.RespData;
import com.panoramic.storebff.bff.StoreShopBffService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 店主店铺接口（店铺端 BFF 对外形状，路径与旧 store-center 一致，store 前端不改）。
 * <p>数据在 store 域（store_shop + 审核状态机），本控制器只做编排：把当前店主账号 id 作为
 * store_id 经 {@link StoreShopBffService} → StoreClient owner 接口调 store 域（域内不存在则建店）；
 * 权限判定收敛在本端（common 认证链，需 store 登录），无 RBAC。</p>
 */
@RestController
@RequestMapping("/shops")
@RequiredArgsConstructor
public class ShopController {

    private final StoreShopBffService storeShopBffService;

    /**
     * 我的店铺：返回当前店主的店铺（未开店返回 data=null，前端据此进入「未开店」引导）
     */
    @GetMapping("/mine")
    public RespData<ShopVO> mine() {
        return RespData.success(storeShopBffService.mine());
    }

    /**
     * 保存草稿（无店铺则新建 id=账号id 的店；待审核/已通过不允许改动）
     */
    @PostMapping("/save")
    public RespData<Void> save(@Valid @RequestBody ShopSaveDTO dto) {
        storeShopBffService.saveDraft(dto);
        return RespData.success();
    }

    /**
     * 提交审核（须完整资质，成功后进入待审核）
     */
    @PostMapping("/submit")
    public RespData<Void> submit(@Valid @RequestBody ShopSaveDTO dto) {
        storeShopBffService.submit(dto);
        return RespData.success();
    }
}
