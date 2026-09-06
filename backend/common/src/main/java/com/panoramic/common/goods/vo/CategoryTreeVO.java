package com.panoramic.common.goods.vo;

import lombok.Data;

import java.util.List;

/**
 * 分类树节点响应
 */
@Data
public class CategoryTreeVO {

    /**
     * 主键
     */
    private Long id;

    /**
     * 父分类ID，0 表示顶级
     */
    private Long parentId;

    /**
     * 分类名称
     */
    private String name;

    /**
     * 层级：1=顶级
     */
    private Integer level;

    /**
     * 排序值，越小越靠前
     */
    private Integer sort;

    /**
     * 子分类列表
     */
    private List<CategoryTreeVO> children;
}
