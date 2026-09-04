package com.panoramic.goods.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.panoramic.common.exception.ServiceException;
import com.panoramic.goods.dto.SkuDTO;
import com.panoramic.goods.dto.SpecAttr;
import com.panoramic.goods.dto.SpecConfigItem;
import com.panoramic.goods.dto.SpuPageQueryDTO;
import com.panoramic.goods.dto.SpuSaveDTO;
import com.panoramic.goods.dto.SpuSkuReplaceDTO;
import com.panoramic.goods.dto.SpuStatusDTO;
import com.panoramic.goods.dto.SpuUpdateDTO;
import com.panoramic.goods.entity.GoodsBrand;
import com.panoramic.goods.entity.GoodsCategory;
import com.panoramic.goods.entity.GoodsSku;
import com.panoramic.goods.entity.GoodsSpu;
import com.panoramic.goods.mapper.GoodsSpuMapper;
import com.panoramic.goods.service.BrandService;
import com.panoramic.goods.service.CategoryService;
import com.panoramic.goods.service.SkuService;
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
import java.util.HashMap;
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
public class SpuServiceImpl extends ServiceImpl<GoodsSpuMapper, GoodsSpu> implements SpuService {

    /** 跨实体：分类服务（叶子校验、详情取名、路径链、列表名称回填） */
    private final CategoryService categoryService;
    /** 跨实体：品牌服务（存在性校验、详情取名、列表名称回填） */
    private final BrandService brandService;
    /** 跨实体：SKU 服务（列表/详情/全量替换/级联读写） */
    private final SkuService skuService;
    private final ObjectMapper objectMapper;

    @Override
    public PageResult<SpuPageItemVO> page(SpuPageQueryDTO dto) {
        Page<GoodsSpu> spuPage = dto.toPage(GoodsSpu.class);
        IPage<GoodsSpu> result = page(spuPage,
                Wrappers.<GoodsSpu>lambdaQuery()
                        .eq(dto.getCategoryId() != null, GoodsSpu::getCategoryId, dto.getCategoryId())
                        .eq(dto.getBrandId() != null, GoodsSpu::getBrandId, dto.getBrandId())
                        .eq(dto.getStatus() != null, GoodsSpu::getStatus, dto.getStatus())
                        .like(StringUtils.hasText(dto.getKeyword()), GoodsSpu::getName, dto.getKeyword())
                        .orderByDesc(GoodsSpu::getId));

        List<GoodsSpu> records = result.getRecords();
        List<Long> categoryIds = records.stream().map(GoodsSpu::getCategoryId).distinct().collect(Collectors.toList());
        List<Long> brandIds = records.stream().map(GoodsSpu::getBrandId).distinct().collect(Collectors.toList());
        Map<Long, String> categoryNames = categoryService.nameMap(categoryIds);
        Map<Long, String> brandNames = brandService.nameMap(brandIds);

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
        // imageList/specConfig 实体为 String(JSON)、VO 为 List，类型不一致需排除后手动转换
        BeanUtils.copyProperties(spu, vo, "imageList", "specConfig");
        // 分类/品牌名称（经各自实体服务取名，容缺失 -> ""）
        GoodsCategory category = spu.getCategoryId() == null ? null : categoryService.getById(spu.getCategoryId());
        GoodsBrand brand = spu.getBrandId() == null ? null : brandService.getById(spu.getBrandId());
        vo.setCategoryName(category == null ? "" : category.getName());
        vo.setBrandName(brand == null ? "" : brand.getName());
        // 轮播图 JSON -> List
        vo.setImageList(readJsonList(spu.getImageList(), new TypeReference<List<String>>() {}));
        // 规格属性配置 JSON -> List；分类完整链条（根→叶子）
        vo.setSpecConfig(readJsonList(spu.getSpecConfig(), new TypeReference<List<SpecConfigItem>>() {}));
        vo.setCategoryPath(categoryService.pathNames(spu.getCategoryId()));
        // SKU 列表
        List<GoodsSku> skus = skuService.listBySpuId(id);
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
        validateSpecConfig(dto.getSpecConfig());

        GoodsSpu spu = new GoodsSpu();
        // imageList/specConfig DTO 为 List、实体为 String(JSON)，排除后手动转换
        BeanUtils.copyProperties(dto, spu, "imageList", "specConfig");
        spu.setImageList(writeJson(dto.getImageList()));
        spu.setSpecConfig(writeJson(dto.getSpecConfig()));
        spu.setStatus(dto.getStatus() == null ? 0 : dto.getStatus());
        save(spu);
        // 新建商品不携带 SKU（0 SKU 起步），SKU 由 replaceSkus 在「规格」管理中单独维护
        return spu.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSpu(Long id, SpuUpdateDTO dto) {
        GoodsSpu spu = getByIdOrThrow(id);
        checkCategoryAndBrand(dto.getCategoryId(), dto.getBrandId());
        validateSpecConfig(dto.getSpecConfig());

        // 防孤立守卫：存量 SKU 仍使用的规格/属性值不得从配置中移除
        guardConfigAgainstSkus(id, dto.getSpecConfig());

        // status 仅在显式传值时更新，避免编辑时未带展示状态导致误隐藏；
        // imageList/specConfig DTO 为 List、实体为 String(JSON)，排除后手动转换
        BeanUtils.copyProperties(dto, spu, "imageList", "specConfig", "status");
        spu.setImageList(writeJson(dto.getImageList()));
        spu.setSpecConfig(writeJson(dto.getSpecConfig()));
        if (dto.getStatus() != null) {
            spu.setStatus(dto.getStatus());
        }
        updateById(spu);
        // SKU 不再随基础信息更新；由 replaceSkus 在「规格」管理中单独维护
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void replaceSkus(Long id, SpuSkuReplaceDTO dto) {
        GoodsSpu spu = getByIdOrThrow(id);
        List<SkuDTO> skus = dto.getSkus() == null ? new ArrayList<>() : dto.getSkus();
        // 商品规格属性配置（空/null 均视为无规格）
        List<SpecConfigItem> config = readJsonList(spu.getSpecConfig(), new TypeReference<List<SpecConfigItem>>() {});
        Map<String, SpecConfigItem> configIndex = indexConfig(config);

        // ---- 校验：SKU 组合须来源于该商品的规格属性配置 ----
        if (configIndex.isEmpty() && !skus.isEmpty()) {
            throw new ServiceException("该商品未配置规格属性，请先在「编辑」中配置规格属性后再添加 SKU");
        }
        if (!skus.isEmpty()) {
            Set<String> seenKeys = new HashSet<>();
            for (SkuDTO sku : skus) {
                List<SpecAttr> attrs = sku.getSpecAttrs();
                if (attrs == null || attrs.isEmpty()) {
                    throw new ServiceException("SKU 至少需要一个规格属性");
                }
                Set<String> attrSpecs = new HashSet<>();
                for (SpecAttr attr : attrs) {
                    if (!StringUtils.hasText(attr.getSpec())) {
                        throw new ServiceException("规格名不能为空");
                    }
                    if (!StringUtils.hasText(attr.getValue())) {
                        throw new ServiceException("规格值不能为空");
                    }
                    if (!attrSpecs.add(attr.getSpec())) {
                        throw new ServiceException("SKU 内规格名重复：" + attr.getSpec());
                    }
                }
                // 无序比较：SKU 规格集合须与商品规格属性配置一致（各维度各取一个值）
                if (!attrSpecs.equals(configIndex.keySet())) {
                    throw new ServiceException("SKU 规格必须与商品规格属性配置一致");
                }
                for (SpecAttr attr : attrs) {
                    SpecConfigItem item = configIndex.get(attr.getSpec());
                    if (item == null || item.getValues() == null || !item.getValues().contains(attr.getValue())) {
                        throw new ServiceException("SKU 属性值「" + attr.getSpec() + "=" + attr.getValue() + "」不在规格属性配置中");
                    }
                }
                String key = comboKey(attrs);
                if (!seenKeys.add(key)) {
                    throw new ServiceException("SKU 规格组合重复：" + key.replace("|", "，"));
                }
            }
        }

        // ---- diff：无 id 插入，带 id 更新，存量缺失的逻辑删除（保证 SKU ID 稳定）----
        Map<Long, GoodsSku> existing = skuService.listBySpuId(id).stream()
                .collect(Collectors.toMap(GoodsSku::getId, Function.identity()));

        Set<Long> incomingIds = new HashSet<>();
        for (SkuDTO skuDto : skus) {
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
                skuService.updateById(target);
            }
        }
        // 入参缺失的存量 SKU -> 逻辑删除
        List<Long> removed = existing.keySet().stream()
                .filter(skuId -> !incomingIds.contains(skuId))
                .collect(Collectors.toList());
        if (!removed.isEmpty()) {
            skuService.removeByIds(removed);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, SpuStatusDTO dto) {
        GoodsSpu spu = getByIdOrThrow(id);
        spu.setStatus(dto.getStatus());
        updateById(spu);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteSpu(Long id) {
        GoodsSpu spu = getByIdOrThrow(id);
        if (spu.getStatus() == 1) {
            throw new ServiceException("商品展示中，请先隐藏再删除");
        }
        removeById(id);
        // 级联逻辑删除该商品全部 SKU
        skuService.removeBySpuId(id);
    }

    @Override
    public long countByCategoryId(Long categoryId) {
        return count(Wrappers.<GoodsSpu>lambdaQuery().eq(GoodsSpu::getCategoryId, categoryId));
    }

    @Override
    public long countByBrandId(Long brandId) {
        return count(Wrappers.<GoodsSpu>lambdaQuery().eq(GoodsSpu::getBrandId, brandId));
    }

    /**
     * 根据 ID 查询商品（不存在抛出业务异常）
     *
     * @param id 商品ID
     * @return 商品实体
     */
    private GoodsSpu getByIdOrThrow(Long id) {
        GoodsSpu spu = getById(id);
        if (spu == null) {
            throw new ServiceException("商品不存在");
        }
        return spu;
    }

    /**
     * 防孤立守卫：更新规格属性配置前，校验存量 SKU 仍使用的每个 (spec, value) 都保留在新配置中。
     * 配置被清空但仍有 SKU 时同样拒绝
     *
     * @param spuId  商品ID
     * @param config 新的规格属性配置（null 视为清空配置）
     */
    private void guardConfigAgainstSkus(Long spuId, List<SpecConfigItem> config) {
        List<GoodsSku> existing = skuService.listBySpuId(spuId);
        if (existing.isEmpty()) {
            return;
        }
        Map<String, SpecConfigItem> newIndex = indexConfig(config);
        if (newIndex.isEmpty()) {
            throw new ServiceException("商品仍存在 SKU，请先在「规格」中删除或调整 SKU 后再清空规格属性配置");
        }
        for (GoodsSku sku : existing) {
            List<SpecAttr> attrs = readJsonList(sku.getSpecAttrs(), new TypeReference<List<SpecAttr>>() {});
            for (SpecAttr attr : attrs) {
                SpecConfigItem item = newIndex.get(attr.getSpec());
                if (item == null || item.getValues() == null || !item.getValues().contains(attr.getValue())) {
                    throw new ServiceException("规格属性配置已变更，现有 SKU 使用了「" + attr.getSpec() + "=" + attr.getValue()
                            + "」；请先在「规格」中调整相关 SKU 后再保存基础信息");
                }
            }
        }
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
     * 校验规格属性配置结构：规格名非空且不重复、每项至少一个属性值、值非空且不重复
     *
     * @param config 规格属性配置（null 表示未配置，直接放行）
     */
    private void validateSpecConfig(List<SpecConfigItem> config) {
        if (config == null) {
            return;
        }
        Set<String> seenSpecs = new HashSet<>();
        for (SpecConfigItem item : config) {
            if (!StringUtils.hasText(item.getSpec())) {
                throw new ServiceException("规格名不能为空");
            }
            if (!seenSpecs.add(item.getSpec())) {
                throw new ServiceException("规格名重复：" + item.getSpec());
            }
            List<String> values = item.getValues();
            if (values == null || values.isEmpty()) {
                throw new ServiceException("规格「" + item.getSpec() + "」至少需要一个属性值");
            }
            Set<String> seenVals = new HashSet<>();
            for (String v : values) {
                if (!StringUtils.hasText(v)) {
                    throw new ServiceException("规格「" + item.getSpec() + "」存在空的属性值");
                }
                if (!seenVals.add(v)) {
                    throw new ServiceException("规格「" + item.getSpec() + "」属性值重复：" + v);
                }
            }
        }
    }

    /**
     * 规格属性配置建索引（规格名 → 配置项）
     *
     * @param config 规格属性配置
     * @return spec -> item 映射；空/未配置返回空 Map
     */
    private Map<String, SpecConfigItem> indexConfig(List<SpecConfigItem> config) {
        if (config == null || config.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, SpecConfigItem> index = new HashMap<>();
        for (SpecConfigItem item : config) {
            index.put(item.getSpec(), item);
        }
        return index;
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
     * 插入单个 SKU（经 SKU 服务）
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
        skuService.save(sku);
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
