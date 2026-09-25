package com.panoramic.store.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.panoramic.store.entity.StoreGoodsSpu;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 店铺在售商品 SPU Mapper
 */
public interface StoreGoodsSpuMapper extends BaseMapper<StoreGoodsSpu> {

    /**
     * 按商品 id 取其所属店铺 id（<b>不过滤逻辑删除</b>）。
     * <p>为什么必须自己写 SQL：实体带 {@code @TableLogic}，MP 会给所有查询/更新自动追加
     * {@code is_delete = 0}，商品软删后就查不到行了。而提交评价这条链路要的正是「已软删的商品
     * 也照常可评价」——故只能绕过逻辑删除注入。⚠ 这是<b>唯一</b>一处刻意无视逻辑删除的读，
     * 别把它当范例套到其它查询上。</p>
     *
     * @param spuId 店铺商品 id
     * @return 所属店铺 id；该 id 无对应行时为 null
     */
    @Select("SELECT store_id FROM store_goods_spu WHERE id = #{spuId}")
    Long selectStoreIdIgnoreDelete(@Param("spuId") Long spuId);
}
