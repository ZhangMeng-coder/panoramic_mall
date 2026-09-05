package com.panoramic.store.controller;

import com.panoramic.common.vo.RespData;
import com.panoramic.store.dto.ShopSaveDTO;
import com.panoramic.store.service.StoreShopService;
import com.panoramic.store.vo.ShopVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 店主店铺接口（店主侧，归属 + 审核状态机守卫，无 RBAC 权限）
 */
@RestController
@RequestMapping("/shops")
@RequiredArgsConstructor
public class ShopController {

    private final StoreShopService storeShopService;

    /**
     * 我的店铺：返回当前店主的店铺（未创建返回 null，前端据此进入「未开店」引导）
     */
    @GetMapping("/mine")
    public RespData<ShopVO> mine() {
        return RespData.success(storeShopService.mine());
    }

    /**
     * 保存草稿（无店铺则新建；待审核/已通过不允许改动）
     */
    @PostMapping("/save")
    public RespData<Void> save(@Valid @RequestBody ShopSaveDTO dto) {
        storeShopService.saveDraft(dto);
        return RespData.success();
    }

    /**
     * 提交审核（须完整资质，成功后进入待审核）
     */
    @PostMapping("/submit")
    public RespData<Void> submit(@Valid @RequestBody ShopSaveDTO dto) {
        storeShopService.submit(dto);
        return RespData.success();
    }
}
