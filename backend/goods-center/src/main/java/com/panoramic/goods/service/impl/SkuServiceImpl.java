package com.panoramic.goods.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.panoramic.goods.entity.GoodsSku;
import com.panoramic.goods.mapper.GoodsSkuMapper;
import com.panoramic.goods.service.SkuService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * 商品 SKU 服务实现
 */
@Slf4j
@Service
public class SkuServiceImpl extends ServiceImpl<GoodsSkuMapper, GoodsSku> implements SkuService {

    @Override
    public List<GoodsSku> listBySpuId(Long spuId) {
        List<GoodsSku> skus = list(Wrappers.<GoodsSku>lambdaQuery()
                .eq(GoodsSku::getSpuId, spuId)
                .orderByAsc(GoodsSku::getId));
        return skus == null ? Collections.emptyList() : skus;
    }

    @Override
    public void removeBySpuId(Long spuId) {
        // GoodsSku 继承 BaseEntity（@TableLogic），remove 为逻辑删除
        remove(Wrappers.<GoodsSku>lambdaQuery().eq(GoodsSku::getSpuId, spuId));
    }
}
