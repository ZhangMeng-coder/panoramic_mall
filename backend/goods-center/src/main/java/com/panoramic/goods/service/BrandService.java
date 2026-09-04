package com.panoramic.goods.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.panoramic.goods.dto.BrandPageQueryDTO;
import com.panoramic.goods.dto.BrandSaveDTO;
import com.panoramic.goods.dto.BrandUpdateDTO;
import com.panoramic.goods.entity.GoodsBrand;
import com.panoramic.goods.vo.BrandVO;
import com.panoramic.goods.vo.PageResult;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 商品品牌服务
 */
public interface BrandService extends IService<GoodsBrand> {

    /**
     * 品牌分页查询
     *
     * @param dto 分页查询参数（名称关键字模糊匹配）
     * @return 分页结果
     */
    PageResult<BrandVO> page(BrandPageQueryDTO dto);

    /**
     * 全量品牌列表（商品表单下拉选择用，按排序升序）
     *
     * @return 品牌列表
     */
    List<BrandVO> listAll();

    /**
     * 品牌详情
     *
     * @param id 品牌ID
     * @return 品牌响应
     */
    BrandVO detail(Long id);

    /**
     * 新建品牌
     *
     * @param dto 新建请求
     * @return 新品牌ID
     */
    Long saveBrand(BrandSaveDTO dto);

    /**
     * 更新品牌
     *
     * @param id  品牌ID
     * @param dto 更新请求
     */
    void updateBrand(Long id, BrandUpdateDTO dto);

    /**
     * 删除品牌（被商品引用时拒绝）
     *
     * @param id 品牌ID
     */
    void deleteBrand(Long id);

    /**
     * 品牌 ID 集合批量查名称（分页列表名称回填用）
     *
     * @param ids 品牌 ID 集合
     * @return id -> 品牌名称映射
     */
    Map<Long, String> nameMap(Collection<Long> ids);
}
