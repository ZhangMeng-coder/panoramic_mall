package com.panoramic.goods.service;

import com.panoramic.goods.dto.CategorySaveDTO;
import com.panoramic.goods.dto.CategoryUpdateDTO;
import com.panoramic.goods.entity.GoodsCategory;
import com.panoramic.goods.vo.CategoryTreeVO;

import java.util.List;

/**
 * 商品分类服务
 */
public interface CategoryService {

    /**
     * 新建分类
     *
     * @param dto 新建请求
     * @return 新分类ID
     */
    Long saveCategory(CategorySaveDTO dto);

    /**
     * 更新分类（仅名称与排序）
     *
     * @param id  分类ID
     * @param dto 更新请求
     */
    void updateCategory(Long id, CategoryUpdateDTO dto);

    /**
     * 删除分类（存在子分类或商品时拒绝）
     *
     * @param id 分类ID
     */
    void deleteCategory(Long id);

    /**
     * 查询全量分类树
     *
     * @return 顶级分类树列表
     */
    List<CategoryTreeVO> tree();

    /**
     * 根据 ID 查询分类（不存在抛出业务异常）
     *
     * @param id 分类ID
     * @return 分类实体
     */
    GoodsCategory getByIdOrThrow(Long id);

    /**
     * 判断是否为叶子分类（无子分类）
     *
     * @param id 分类ID
     * @return true=叶子分类
     */
    boolean isLeaf(Long id);
}
