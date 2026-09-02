package com.panoramic.goods.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.panoramic.common.exception.ServiceException;
import com.panoramic.goods.dto.SkuDTO;
import com.panoramic.goods.dto.SpecAttr;
import com.panoramic.goods.dto.SpuPageQueryDTO;
import com.panoramic.goods.dto.SpuSaveDTO;
import com.panoramic.goods.dto.SpuStatusDTO;
import com.panoramic.goods.dto.SpuUpdateDTO;
import com.panoramic.goods.entity.GoodsBrand;
import com.panoramic.goods.entity.GoodsCategory;
import com.panoramic.goods.entity.GoodsSku;
import com.panoramic.goods.entity.GoodsSpu;
import com.panoramic.goods.mapper.GoodsBrandMapper;
import com.panoramic.goods.mapper.GoodsCategoryMapper;
import com.panoramic.goods.mapper.GoodsSkuMapper;
import com.panoramic.goods.mapper.GoodsSpuMapper;
import com.panoramic.goods.service.BrandService;
import com.panoramic.goods.service.CategoryService;
import com.panoramic.goods.service.SpuService;
import com.panoramic.goods.vo.PageResult;
import com.panoramic.goods.vo.SkuVO;
import com.panoramic.goods.vo.SpuDetailVO;
import com.panoramic.goods.vo.SpuPageItemVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 商品（SPU）服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SpuServiceImpl implements SpuService {

    private final GoodsSpuMapper spuMapper;
    private final GoodsSkuMapper skuMapper;
    private final GoodsCategoryMapper categoryMapper;
    private final GoodsBrandMapper brandMapper;
    private final CategoryService categoryService;
    private final BrandService brandService;
    private final ObjectMapper objectMapper;

    @Override
    public PageResult<SpuPageItemVO> page(SpuPageQueryDTO dto) {
        Page<GoodsSpu> spuPage = dto.toPage(GoodsSpu.class);
        IPage<GoodsSpu> result = spuMapper.selectPage(spuPage,
                Wrappers.<GoodsSpu>lambdaQuery()
                        .eq(dto.getCategoryId() != null, GoodsSpu::getCategoryId, dto.getCategoryId())
                        .eq(dto.getBrandId() != null, GoodsSpu::getBrandId, dto.getBrandId())
                        .eq(dto.getStatus() != null, GoodsSpu::getStatus, dto.getStatus())
                        .like(StringUtils.hasText(dto.getKeyword()), GoodsSpu::getName, dto.getKeyword())
                        .orderByDesc(GoodsSpu::getId));

        List<GoodsSpu> records = result.getRecords();
        List<Long> categoryIds = records.stream().map(GoodsSpu::getCategoryId).distinct().collect(Collectors.toList());
        List<Long> brandIds = records.stream().map(GoodsSpu::getBrandId).distinct().collect(Collectors.toList());
        Map<Long, String> categoryNames = categoryNamesByIds(categoryIds);
        Map<Long, String> brandNames = brandNamesByIds(brandIds);

        List<SpuPageItemVO> items = records.stream().map(spu -> {
            SpuPageItemVO vo = new SpuPageItemVO();
            BeanUtils.copyProperties(spu, vo);
            vo.setCategoryName(categoryNames.getOrDefault(spu.getCategoryId(), ""));
            vo.setBrandName(brandNames.getOrDefault(spu.getBrandId(), ""));
            return vo;
        }).collect(Collectors.toList());
        return new PageResult<>(result.getTotal(), items);
    }

    @Override
    public SpuDetailVO detail(Long id) {
        GoodsSpu spu = getByIdOrThrow(id);
        SpuDetailVO vo = new SpuDetailVO();
        // imageList 实体为 String(JSON)、VO 为 List，类型不一致需排除后手动转换
        BeanUtils.copyProperties(spu, vo, "imageList");
        // 分类/品牌名称
        GoodsCategory category = categoryMapper.selectById(spu.getCategoryId());
        GoodsBrand brand = brandMapper.selectById(spu.getBrandId());
        vo.setCategoryName(category == null ? "" : category.getName());
        vo.setBrandName(brand == null ? "" : brand.getName());
        // 轮播图 JSON -> List
        vo.setImageList(readJsonList(spu.getImageList(), new TypeReference<List<String>>() {}));
        // SKU 列表
        List<GoodsSku> skus = skuMapper.selectList(
                Wrappers.<GoodsSku>lambdaQuery()
                        .eq(GoodsSku::getSpuId, id)
                        .orderByAsc(GoodsSku::getId));
        List<SkuVO> skuVos = skus.stream().map(sku -> {
            SkuVO skuVo = new SkuVO();
            BeanUtils.copyProperties(sku, skuVo);
            skuVo.setSpecAttrs(readJsonList(sku.getSpecAttrs(), new TypeReference<List<SpecAttr>>() {}));
            return skuVo;
        }).collect(Collectors.toList());
        vo.setSkus(skuVos);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveSpu(SpuSaveDTO dto) {
        checkCategoryAndBrand(dto.getCategoryId(), dto.getBrandId());
        validateSkus(dto.getSkus());

        GoodsSpu spu = new GoodsSpu();
        // imageList DTO 为 List、实体为 String(JSON)，排除后手动转换
        BeanUtils.copyProperties(dto, spu, "imageList");
        spu.setImageList(writeJson(dto.getImageList()));
        spu.setStatus(dto.getStatus() == null ? 0 : dto.getStatus());
        spuMapper.insert(spu);

        insertSkus(spu.getId(), dto.getSkus());
        return spu.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSpu(Long id, SpuUpdateDTO dto) {
        GoodsSpu spu = getByIdOrThrow(id);
        checkCategoryAndBrand(dto.getCategoryId(), dto.getBrandId());
        validateSkus(dto.getSkus());

        // status 仅在显式传值时更新，避免编辑时未带上架状态导致误下架
        BeanUtils.copyProperties(dto, spu, "imageList", "status");
        spu.setImageList(writeJson(dto.getImageList()));
        if (dto.getStatus() != null) {
            spu.setStatus(dto.getStatus());
        }
        spuMapper.updateById(spu);

        // SKU diff：无 id 插入，带 id 更新，存量缺失的逻辑删除（保证 SKU ID 稳定）
        Map<Long, GoodsSku> existing = skuMapper.selectList(
                        Wrappers.<GoodsSku>lambdaQuery().eq(GoodsSku::getSpuId, id))
                .stream().collect(Collectors.toMap(GoodsSku::getId, Function.identity()));

        Set<Long> incomingIds = new HashSet<>();
        for (SkuDTO skuDto : dto.getSkus()) {
            if (skuDto.getId() == null) {
                // 新增 SKU
                insertSku(id, skuDto);
            } else {
                GoodsSku target = existing.get(skuDto.getId());
                if (target == null) {
                    throw new ServiceException("SKU 不存在或不属于该商品");
                }
                incomingIds.add(skuDto.getId());
                target.setSpecAttrs(writeJson(skuDto.getSpecAttrs()));
                target.setSkuCode(skuDto.getSkuCode());
                target.setMainImage(skuDto.getMainImage());
                skuMapper.updateById(target);
            }
        }
        // 入参缺失的存量 SKU -> 逻辑删除
        List<Long> removed = existing.keySet().stream()
                .filter(skuId -> !incomingIds.contains(skuId))
                .collect(Collectors.toList());
        if (!removed.isEmpty()) {
            skuMapper.deleteByIds(removed);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, SpuStatusDTO dto) {
        GoodsSpu spu = getByIdOrThrow(id);
        spu.setStatus(dto.getStatus());
        spuMapper.updateById(spu);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteSpu(Long id) {
        GoodsSpu spu = getByIdOrThrow(id);
        if (spu.getStatus() == 1) {
            throw new ServiceException("商品上架中，请先下架再删除");
        }
        spuMapper.deleteById(id);
        // 级联逻辑删除该商品全部 SKU
        skuMapper.delete(Wrappers.<GoodsSku>lambdaQuery().eq(GoodsSku::getSpuId, id));
    }

    /**
     * 根据 ID 查询商品（不存在抛出业务异常）
     *
     * @param id 商品ID
     * @return 商品实体
     */
    private GoodsSpu getByIdOrThrow(Long id) {
        GoodsSpu spu = spuMapper.selectById(id);
        if (spu == null) {
            throw new ServiceException("商品不存在");
        }
        return spu;
    }

    /**
     * 校验分类与品牌：分类存在且为叶子分类、品牌存在
     *
     * @param categoryId 分类ID
     * @param brandId    品牌ID
     */
    private void checkCategoryAndBrand(Long categoryId, Long brandId) {
        GoodsCategory category = categoryService.getByIdOrThrow(categoryId);
        if (!categoryService.isLeaf(categoryId)) {
            throw new ServiceException("商品必须挂在叶子分类下");
        }
        brandService.detail(brandId);
    }

    /**
     * 校验 SKU 结构与组合唯一性：
     * 每个 SKU 至少 1 个规格属性；规格名/值非空；SKU 内规格名不重复；请求内组合不重复
     *
     * @param skus SKU 列表
     */
    private void validateSkus(List<SkuDTO> skus) {
        if (skus == null || skus.isEmpty()) {
            throw new ServiceException("商品至少需要一个 SKU");
        }
        Set<String> seenKeys = new HashSet<>();
        for (SkuDTO sku : skus) {
            List<SpecAttr> attrs = sku.getSpecAttrs();
            if (attrs == null || attrs.isEmpty()) {
                throw new ServiceException("SKU 至少需要一个规格属性");
            }
            Set<String> specNames = new HashSet<>();
            for (SpecAttr attr : attrs) {
                if (!StringUtils.hasText(attr.getSpec())) {
                    throw new ServiceException("规格名不能为空");
                }
                if (!StringUtils.hasText(attr.getValue())) {
                    throw new ServiceException("规格值不能为空");
                }
                if (!specNames.add(attr.getSpec())) {
                    throw new ServiceException("SKU 内规格名重复：" + attr.getSpec());
                }
            }
            String key = comboKey(attrs);
            if (!seenKeys.add(key)) {
                throw new ServiceException("SKU 规格组合重复：" + key.replace("|", "，"));
            }
        }
    }

    /**
     * 生成规格组合唯一键（按规格名排序后拼接，保证与提交顺序无关）
     *
     * @param attrs 规格属性列表
     * @return 组合键
     */
    private String comboKey(List<SpecAttr> attrs) {
        return attrs.stream()
                .sorted((a, b) -> a.getSpec().compareTo(b.getSpec()))
                .map(a -> a.getSpec() + "=" + a.getValue())
                .collect(Collectors.joining("|"));
    }

    /**
     * 批量插入 SKU（回填 spuId）
     *
     * @param spuId 商品ID
     * @param skus  SKU 请求列表
     */
    private void insertSkus(Long spuId, List<SkuDTO> skus) {
        for (SkuDTO skuDto : skus) {
            insertSku(spuId, skuDto);
        }
    }

    /**
     * 插入单个 SKU
     *
     * @param spuId  商品ID
     * @param skuDto SKU 请求
     */
    private void insertSku(Long spuId, SkuDTO skuDto) {
        GoodsSku sku = new GoodsSku();
        sku.setSpuId(spuId);
        sku.setSpecAttrs(writeJson(skuDto.getSpecAttrs()));
        sku.setSkuCode(skuDto.getSkuCode());
        sku.setMainImage(skuDto.getMainImage());
        skuMapper.insert(sku);
    }

    /**
     * 分类 ID 集合批量查名称（分页列表名称回填用）
     *
     * @param ids 分类 ID 集合
     * @return id -> 分类名称映射
     */
    private Map<Long, String> categoryNamesByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return categoryMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(GoodsCategory::getId, GoodsCategory::getName));
    }

    /**
     * 品牌 ID 集合批量查名称（分页列表名称回填用）
     *
     * @param ids 品牌 ID 集合
     * @return id -> 品牌名称映射
     */
    private Map<Long, String> brandNamesByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return brandMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(GoodsBrand::getId, GoodsBrand::getName));
    }

    /**
     * JSON 序列化（null 输入序列化为 null 存库）
     */
    private String writeJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("JSON 序列化失败: ", e);
            throw new ServiceException("数据序列化失败");
        }
    }

    /**
     * JSON 反序列化（容错：空/非法 JSON 返回空列表）
     */
    private <T> List<T> readJsonList(String json, TypeReference<List<T>> typeRef) {
        if (!StringUtils.hasText(json)) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, typeRef);
        } catch (JsonProcessingException e) {
            log.warn("JSON 反序列化失败，json={}: {}", json, e.getMessage());
            return new ArrayList<>();
        }
    }
}
