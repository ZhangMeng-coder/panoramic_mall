package com.panoramic.goods.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.panoramic.common.exception.ServiceException;
import com.panoramic.goods.dto.CategorySaveDTO;
import com.panoramic.goods.dto.CategoryUpdateDTO;
import com.panoramic.goods.entity.GoodsCategory;
import com.panoramic.goods.entity.GoodsSpu;
import com.panoramic.goods.mapper.GoodsCategoryMapper;
import com.panoramic.goods.mapper.GoodsSpuMapper;
import com.panoramic.goods.service.CategoryService;
import com.panoramic.goods.vo.CategoryTreeVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 商品分类服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    /**
     * 最大分类层级
     */
    private static final int MAX_LEVEL = 3;

    private final GoodsCategoryMapper categoryMapper;
    private final GoodsSpuMapper spuMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveCategory(CategorySaveDTO dto) {
        GoodsCategory category = new GoodsCategory();
        category.setParentId(dto.getParentId());
        category.setName(dto.getName());
        category.setSort(dto.getSort() == null ? 0 : dto.getSort());

        if (dto.getParentId() == 0L) {
            // 顶级分类
            category.setLevel(1);
        } else {
            // 子分类：层级 = 父层级 + 1，限制最多 3 级
            GoodsCategory parent = getByIdOrThrow(dto.getParentId());
            int level = parent.getLevel() + 1;
            if (level > MAX_LEVEL) {
                throw new ServiceException("分类层级最多" + MAX_LEVEL + "级，无法继续添加子分类");
            }
            category.setLevel(level);
        }
        checkNameDuplicate(category.getName(), category.getParentId(), null);

        categoryMapper.insert(category);
        return category.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCategory(Long id, CategoryUpdateDTO dto) {
        GoodsCategory category = getByIdOrThrow(id);
        // 保持原有父级与层级，仅更新名称与排序
        checkNameDuplicate(dto.getName(), category.getParentId(), id);

        category.setName(dto.getName());
        category.setSort(dto.getSort() == null ? 0 : dto.getSort());
        categoryMapper.updateById(category);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCategory(Long id) {
        getByIdOrThrow(id);

        // 存在直接子分类即拦截（树形结构下必然拦截所有后代）
        Long childCount = categoryMapper.selectCount(
                Wrappers.<GoodsCategory>lambdaQuery().eq(GoodsCategory::getParentId, id));
        if (childCount > 0) {
            throw new ServiceException("存在子分类，无法删除");
        }
        // 分类下有商品时拒绝删除
        Long spuCount = spuMapper.selectCount(
                Wrappers.<GoodsSpu>lambdaQuery().eq(GoodsSpu::getCategoryId, id));
        if (spuCount > 0) {
            throw new ServiceException("该分类下存在商品，无法删除");
        }
        categoryMapper.deleteById(id);
    }

    @Override
    public List<CategoryTreeVO> tree() {
        // 平铺查询（排序后），再按 parentId 分组递归组装成树
        List<GoodsCategory> categories = categoryMapper.selectList(
                Wrappers.<GoodsCategory>lambdaQuery()
                        .orderByAsc(GoodsCategory::getSort)
                        .orderByAsc(GoodsCategory::getId));
        if (categories.isEmpty()) {
            return new ArrayList<>();
        }
        Map<Long, List<GoodsCategory>> byParent = categories.stream()
                .collect(Collectors.groupingBy(GoodsCategory::getParentId));
        return buildChildren(byParent, 0L);
    }

    @Override
    public GoodsCategory getByIdOrThrow(Long id) {
        GoodsCategory category = categoryMapper.selectById(id);
        if (category == null) {
            throw new ServiceException("分类不存在");
        }
        return category;
    }

    @Override
    public boolean isLeaf(Long id) {
        return categoryMapper.selectCount(
                Wrappers.<GoodsCategory>lambdaQuery().eq(GoodsCategory::getParentId, id)) == 0;
    }

    /**
     * 递归构建指定父分类下的子分类树
     *
     * @param byParent 按父分类ID分组的全量分类
     * @param parentId 父分类ID（0 表示顶级）
     * @return 子分类树列表
     */
    private List<CategoryTreeVO> buildChildren(Map<Long, List<GoodsCategory>> byParent, Long parentId) {
        List<GoodsCategory> nodes = byParent.getOrDefault(parentId, Collections.emptyList());
        return nodes.stream().map(category -> {
            CategoryTreeVO vo = new CategoryTreeVO();
            BeanUtils.copyProperties(category, vo);
            vo.setChildren(buildChildren(byParent, category.getId()));
            return vo;
        }).collect(Collectors.toList());
    }

    /**
     * 校验同级下分类名称不重复（逻辑删除下不做数据库唯一约束，仅做提示性校验）
     *
     * @param name      分类名称
     * @param parentId  父分类ID
     * @param excludeId 需要排除的分类ID（更新时排除自身），可为 null
     */
    private void checkNameDuplicate(String name, Long parentId, Long excludeId) {
        Long count = categoryMapper.selectCount(
                Wrappers.<GoodsCategory>lambdaQuery()
                        .eq(GoodsCategory::getParentId, parentId)
                        .eq(GoodsCategory::getName, name)
                        .ne(excludeId != null, GoodsCategory::getId, excludeId));
        if (count > 0) {
            throw new ServiceException("同级下已存在同名分类");
        }
    }
}
