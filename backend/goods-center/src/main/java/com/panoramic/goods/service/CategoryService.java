package com.panoramic.goods.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.panoramic.common.goods.dto.CategorySaveDTO;
import com.panoramic.common.goods.dto.CategoryUpdateDTO;
import com.panoramic.goods.entity.GoodsCategory;
import com.panoramic.common.goods.vo.CategoryTreeVO;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 商品分类服务
 */
public interface CategoryService extends IService<GoodsCategory> {

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

    /**
     * 分类完整链条名称（根→叶子，用“ / ”拼接）；叶子分类不存在时返回空串
     *
     * @param categoryId 叶子分类ID
     * @return 如 "服饰 / 男装 / T恤"
     */
    String pathNames(Long categoryId);

    /**
     * 分类 ID 集合批量查名称（分页列表名称回填用）
     *
     * @param ids 分类 ID 集合
     * @return id -> 分类名称映射
     */
    Map<Long, String> nameMap(Collection<Long> ids);
}
