package com.panoramic.store.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.panoramic.common.exception.ServiceException;
import com.panoramic.common.goods.dto.SpecAttr;
import com.panoramic.common.goods.dto.SpecConfigItem;
import com.panoramic.common.store.dto.StoreGoodsSkuDTO;
import com.panoramic.common.store.dto.StoreGoodsSkuReplaceDTO;
import com.panoramic.common.store.dto.StoreGoodsSpuPageQueryDTO;
import com.panoramic.common.store.dto.StoreGoodsSpuSaveDTO;
import com.panoramic.common.store.dto.StoreGoodsSpuUpdateDTO;
import com.panoramic.common.store.vo.PageResult;
import com.panoramic.common.store.vo.StoreGoodsSkuVO;
import com.panoramic.common.store.vo.StoreGoodsSpuDetailVO;
import com.panoramic.common.store.vo.StoreGoodsSpuPageItemVO;
import com.panoramic.store.entity.StoreGoodsSku;
import com.panoramic.store.entity.StoreGoodsSpu;
import com.panoramic.store.mapper.StoreGoodsSpuMapper;
import com.panoramic.store.service.StoreGoodsSkuService;
import com.panoramic.store.service.StoreGoodsSpuService;
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
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 店铺在售商品（SPU）服务实现（store 域下沉纯域）。
 * <p><b>数据权限（R11）</b>：本实现全部方法为 owner 侧，入口一律 {@link #getOwnedOrThrow}
 * 以「id + store_id」双条件取行——他人商品与不存在的商品同样报「商品不存在」，
 * 不泄露存在性；SKU 侧操作先经 {@link #getOwnedSkuOrThrow} 校验 SPU 归属。</p>
 * <p><b>上下架不变量</b>：SPU 的 {@code shelf_status} 从不直接接受入参，只由
 * {@link #refreshShelfStatus} 按名下 SKU 重算，保证 {@code SPU上架 ⟺ ≥1 SKU 上架}。</p>
 * <p><b>域内不做鉴权/审核判断</b>：店铺 {@code status == 2} 的门禁由端 BFF 前置（R9），
 * 审计字段由 MyMetaObjectHandler 经 UserContext 自动填充，本类一律不手写。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StoreGoodsSpuServiceImpl extends ServiceImpl<StoreGoodsSpuMapper, StoreGoodsSpu>
        implements StoreGoodsSpuService {

    /** 跨实体：SKU 服务（列表/计数/级联删除；不直接持有 SKU Mapper） */
    private final StoreGoodsSkuService skuService;
    private final ObjectMapper objectMapper;

    @Override
    public PageResult<StoreGoodsSpuPageItemVO> page(Long storeId, StoreGoodsSpuPageQueryDTO dto) {
        Page<StoreGoodsSpu> spuPage = dto.toPage(StoreGoodsSpu.class);
        IPage<StoreGoodsSpu> result = page(spuPage,
                Wrappers.<StoreGoodsSpu>lambdaQuery()
                        .eq(StoreGoodsSpu::getStoreId, storeId)
                        .eq(dto.getCategoryId() != null, StoreGoodsSpu::getCategoryId, dto.getCategoryId())
                        .eq(dto.getBrandId() != null, StoreGoodsSpu::getBrandId, dto.getBrandId())
                        .eq(dto.getShelfStatus() != null, StoreGoodsSpu::getShelfStatus, dto.getShelfStatus())
                        .like(StringUtils.hasText(dto.getKeyword()), StoreGoodsSpu::getName, dto.getKeyword())
                        .orderByDesc(StoreGoodsSpu::getId));

        List<StoreGoodsSpu> records = result.getRecords();
        // 分类/品牌名称取落库快照，不回查中台；SKU 数量批量回填，避免 N+1
        Map<Long, Integer> skuCounts = skuService.countMapBySpuIds(
                records.stream().map(StoreGoodsSpu::getId).collect(Collectors.toList()));
        List<StoreGoodsSpuPageItemVO> items = records.stream().map(spu -> {
            StoreGoodsSpuPageItemVO vo = new StoreGoodsSpuPageItemVO();
            BeanUtils.copyProperties(spu, vo);
            vo.setSkuCount(skuCounts.getOrDefault(spu.getId(), 0));
            return vo;
        }).collect(Collectors.toList());
        return new PageResult<>(result.getTotal(), items);
    }

    @Override
    public StoreGoodsSpuDetailVO detail(Long storeId, Long id) {
        StoreGoodsSpu spu = getOwnedOrThrow(storeId, id);
        StoreGoodsSpuDetailVO vo = new StoreGoodsSpuDetailVO();
        // imageList/specConfig 实体为 String(JSON)、VO 为 List，类型不一致需排除后手动转换
        BeanUtils.copyProperties(spu, vo, "imageList", "specConfig");
        vo.setImageList(readJsonList(spu.getImageList(), new TypeReference<List<String>>() {}));
        vo.setSpecConfig(readJsonList(spu.getSpecConfig(), new TypeReference<List<SpecConfigItem>>() {}));
        vo.setSkus(skuService.listBySpuId(id).stream().map(this::toSkuVO).collect(Collectors.toList()));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(Long storeId, StoreGoodsSpuSaveDTO dto) {
        validateSpecConfig(dto.getSpecConfig());
        List<StoreGoodsSkuDTO> skus = dto.getSkus() == null ? new ArrayList<>() : dto.getSkus();
        validateSkus(dto.getSpecConfig(), skus);

        StoreGoodsSpu spu = new StoreGoodsSpu();
        // imageList/specConfig/skus DTO 为 List、实体为 String(JSON) 或无对应列，排除后手动转换
        BeanUtils.copyProperties(dto, spu, "imageList", "specConfig", "skus");
        spu.setStoreId(storeId);
        spu.setImageList(writeJson(dto.getImageList()));
        spu.setSpecConfig(writeJson(dto.getSpecConfig()));
        // R1：新建商品一律下架态起步，上架与否完全由 SKU 联动推导
        spu.setShelfStatus(StoreGoodsSpu.SHELF_OFF);
        save(spu);
        // SKU 不单独传上下架：一律以下架态随商品落库
        for (StoreGoodsSkuDTO skuDto : skus) {
            insertSku(spu.getId(), skuDto);
        }
        return spu.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long storeId, Long id, StoreGoodsSpuUpdateDTO dto) {
        StoreGoodsSpu spu = getOwnedOrThrow(storeId, id);
        validateSpecConfig(dto.getSpecConfig());

        // R6：存在上架 SKU 时规格配置只读（防止已上架 SKU 的规格组合沦为孤儿）
        if (skuService.hasOnShelfSku(id) && configChanged(spu.getSpecConfig(), dto.getSpecConfig())) {
            throw new ServiceException("商品存在已上架 SKU，规格属性不可修改；请先下架全部 SKU");
        }

        // shelfStatus / goodsSpuId / storeId 不在入参中，spu 由库中取出即保持原值；
        // imageList/specConfig 实体为 String(JSON)，排除后手动转换
        BeanUtils.copyProperties(dto, spu, "imageList", "specConfig");
        spu.setImageList(writeJson(dto.getImageList()));
        spu.setSpecConfig(writeJson(dto.getSpecConfig()));
        // centerVersion 仅在店主点过「同步」后携带；未携带则沿用原值（updateById 跳过 null 字段）
        if (dto.getCenterVersion() != null) {
            spu.setCenterVersion(dto.getCenterVersion());
        }
        updateById(spu);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long storeId, Long id) {
        getOwnedOrThrow(storeId, id);
        // R8：存在已上架 SKU 时拒绝删除
        if (skuService.hasOnShelfSku(id)) {
            throw new ServiceException("商品存在已上架 SKU，请先全部下架后再删除");
        }
        removeById(id);
        // 级联逻辑删除其下全部 SKU（走 SKU service）
        skuService.removeBySpuId(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void replaceSkus(Long storeId, Long id, StoreGoodsSkuReplaceDTO dto) {
        StoreGoodsSpu spu = getOwnedOrThrow(storeId, id);
        List<StoreGoodsSkuDTO> skus = dto.getSkus() == null ? new ArrayList<>() : dto.getSkus();
        List<SpecConfigItem> config = readJsonList(spu.getSpecConfig(), new TypeReference<List<SpecConfigItem>>() {});
        validateSkus(config, skus);

        Map<Long, StoreGoodsSku> existing = skuService.listBySpuId(id).stream()
                .collect(Collectors.toMap(StoreGoodsSku::getId, Function.identity()));

        // ---- 预扫描（先校验后落写，失败不留半成品）----
        Set<Long> incomingIds = new HashSet<>();
        for (StoreGoodsSkuDTO skuDto : skus) {
            if (skuDto.getId() == null) {
                continue;
            }
            StoreGoodsSku target = existing.get(skuDto.getId());
            if (target == null) {
                throw new ServiceException("SKU 不存在或不属于该商品");
            }
            incomingIds.add(skuDto.getId());
            // R4：已上架 SKU 整行锁死——规格/价格/编码/图片必须与库中完全一致
            if (isOnShelf(target)) {
                assertOnShelfSkuUnchanged(target, skuDto);
            }
        }
        for (StoreGoodsSku stored : existing.values()) {
            // R4/R8：已上架 SKU 不得缺失（等价于删除）
            if (!incomingIds.contains(stored.getId()) && isOnShelf(stored)) {
                throw new ServiceException("已上架 SKU 不可删除，请先下架后再删除");
            }
        }

        // ---- 落写：无 id 插入（下架态），带 id 且未上架的更新 ----
        for (StoreGoodsSkuDTO skuDto : skus) {
            if (skuDto.getId() == null) {
                insertSku(id, skuDto);
                continue;
            }
            StoreGoodsSku target = existing.get(skuDto.getId());
            if (isOnShelf(target)) {
                // 已上架且上面已校验一致，无需写入
                continue;
            }
            target.setSpecAttrs(writeJson(skuDto.getSpecAttrs()));
            target.setSkuCode(skuDto.getSkuCode());
            target.setMainImage(skuDto.getMainImage());
            target.setPrice(skuDto.getPrice());
            skuService.updateById(target);
        }
        // 入参缺失的存量行（此处必然都是未上架）-> 逻辑删除
        List<Long> removedIds = existing.values().stream()
                .filter(stored -> !incomingIds.contains(stored.getId()))
                .map(StoreGoodsSku::getId)
                .collect(Collectors.toList());
        if (!removedIds.isEmpty()) {
            skuService.removeByIds(removedIds);
        }
        // 整单替换可能清空/新增上架行，按最新 SKU 状态重新联动 SPU
        refreshShelfStatus(spu);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSkuShelf(Long storeId, Long spuId, Long skuId, Integer shelfStatus) {
        StoreGoodsSpu spu = getOwnedOrThrow(storeId, spuId);
        StoreGoodsSku sku = getOwnedSkuOrThrow(spuId, skuId);
        sku.setShelfStatus(shelfStatus);
        skuService.updateById(sku);
        // R2/R3：SKU 上下架反向联动 SPU（上架任一 → SPU 上架；全下架 → SPU 下架）
        refreshShelfStatus(spu);
    }

    // ---- 联动与规则辅助 ----

    /**
     * 按名下 SKU 重算并回写 SPU 上下架状态（R2/R3）。
     * <p>不变量的唯一写入口：SPU 上架 ⟺ 至少一个 SKU 上架。状态未变则不发 UPDATE。</p>
     *
     * @param spu 店铺商品实体（已确权）
     */
    private void refreshShelfStatus(StoreGoodsSpu spu) {
        int derived = skuService.hasOnShelfSku(spu.getId())
                ? StoreGoodsSpu.SHELF_ON : StoreGoodsSpu.SHELF_OFF;
        if (Objects.equals(spu.getShelfStatus(), derived)) {
            return;
        }
        spu.setShelfStatus(derived);
        updateById(spu);
    }

    /**
     * 插入单个 SKU（经 SKU service），一律以下架态落库（R1）
     *
     * @param spuId  店铺商品 id
     * @param skuDto SKU 请求
     */
    private void insertSku(Long spuId, StoreGoodsSkuDTO skuDto) {
        StoreGoodsSku sku = new StoreGoodsSku();
        sku.setSpuId(spuId);
        sku.setSpecAttrs(writeJson(skuDto.getSpecAttrs()));
        sku.setSkuCode(skuDto.getSkuCode());
        sku.setMainImage(skuDto.getMainImage());
        sku.setPrice(skuDto.getPrice());
        sku.setShelfStatus(StoreGoodsSku.SHELF_OFF);
        skuService.save(sku);
    }

    /**
     * 校验已上架 SKU 入参与库中完全一致（R4：规格组合/价格/编码/图片整行锁死）
     *
     * @param stored   库中 SKU
     * @param incoming 入参 SKU
     */
    private void assertOnShelfSkuUnchanged(StoreGoodsSku stored, StoreGoodsSkuDTO incoming) {
        List<SpecAttr> storedAttrs = readJsonList(stored.getSpecAttrs(), new TypeReference<List<SpecAttr>>() {});
        List<SpecAttr> incomingAttrs = incoming.getSpecAttrs() == null
                ? Collections.emptyList() : incoming.getSpecAttrs();
        boolean attrsSame = normalizeAttrs(storedAttrs).equals(normalizeAttrs(incomingAttrs));
        // 价格用 compareTo：BigDecimal.equals 对精度敏感（10.0 与 10.00 判定不等）
        boolean priceSame = stored.getPrice() != null && incoming.getPrice() != null
                && stored.getPrice().compareTo(incoming.getPrice()) == 0;
        boolean same = attrsSame
                && sameText(stored.getSkuCode(), incoming.getSkuCode())
                && sameText(stored.getMainImage(), incoming.getMainImage())
                && priceSame;
        if (!same) {
            throw new ServiceException("已上架 SKU 不可修改，请先下架后再编辑");
        }
    }

    /**
     * 规格属性配置是否被改动（R6 判定）。按「规格名=值列表」归一化后整体排序比较，
     * 与维度书写顺序无关；比较用入参反序列化后的结构，避免 JSON 文本差异造成误判
     *
     * @param existingJson 库中配置 JSON
     * @param incoming     入参配置
     * @return true = 配置有实质变化
     */
    private boolean configChanged(String existingJson, List<SpecConfigItem> incoming) {
        List<SpecConfigItem> existing = readJsonList(existingJson, new TypeReference<List<SpecConfigItem>>() {});
        return !normalizeConfig(existing).equals(normalizeConfig(incoming));
    }

    /**
     * 规格属性配置归一化为可比较的字符串列表
     */
    private List<String> normalizeConfig(List<SpecConfigItem> config) {
        if (config == null || config.isEmpty()) {
            return Collections.emptyList();
        }
        return config.stream()
                .map(item -> item.getSpec() + "=" + (item.getValues() == null
                        ? Collections.emptyList() : item.getValues()))
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * 规格属性归一化为可比较的字符串列表（与书写顺序无关）
     */
    private List<String> normalizeAttrs(List<SpecAttr> attrs) {
        if (attrs == null || attrs.isEmpty()) {
            return Collections.emptyList();
        }
        return attrs.stream()
                .map(attr -> attr.getSpec() + "=" + attr.getValue())
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * 文本比较：空白串与 null 视为等价（前端清空输入常提交 ""，避免误判为「已改动」）
     */
    private boolean sameText(String a, String b) {
        String x = StringUtils.hasText(a) ? a.trim() : null;
        String y = StringUtils.hasText(b) ? b.trim() : null;
        return Objects.equals(x, y);
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
            for (String value : values) {
                if (!StringUtils.hasText(value)) {
                    throw new ServiceException("规格「" + item.getSpec() + "」存在空的属性值");
                }
                if (!seenVals.add(value)) {
                    throw new ServiceException("规格「" + item.getSpec() + "」属性值重复：" + value);
                }
            }
        }
    }

    /**
     * 校验 SKU 列表：每个 SKU 的规格组合必须来自商品规格属性配置（各维度各取一个值）、
     * 组内不重复且批次内组合不重复
     *
     * @param config 商品规格属性配置
     * @param skus   待落库 SKU 列表
     */
    private void validateSkus(List<SpecConfigItem> config, List<StoreGoodsSkuDTO> skus) {
        Map<String, SpecConfigItem> configIndex = indexConfig(config);
        if (configIndex.isEmpty() && !skus.isEmpty()) {
            throw new ServiceException("本商品未配置规格属性，请先配置规格属性后再添加 SKU");
        }
        if (skus.isEmpty()) {
            return;
        }
        Set<String> seenKeys = new HashSet<>();
        for (StoreGoodsSkuDTO sku : skus) {
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

    /**
     * 规格属性配置建索引（规格名 → 配置项）
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
     */
    private String comboKey(List<SpecAttr> attrs) {
        return attrs.stream()
                .sorted((a, b) -> a.getSpec().compareTo(b.getSpec()))
                .map(attr -> attr.getSpec() + "=" + attr.getValue())
                .collect(Collectors.joining("|"));
    }

    /**
     * 按「id + store_id」取本人商品，取不到（不存在或非本人）一律报「商品不存在」，
     * 不区分二者以免泄露他人商品的存在性（R11）
     *
     * @param storeId 店主账号 id
     * @param id      店铺商品 id
     * @return 店铺商品实体
     */
    private StoreGoodsSpu getOwnedOrThrow(Long storeId, Long id) {
        StoreGoodsSpu spu = getOne(Wrappers.<StoreGoodsSpu>lambdaQuery()
                .eq(StoreGoodsSpu::getId, id)
                .eq(StoreGoodsSpu::getStoreId, storeId));
        if (spu == null) {
            throw new ServiceException("商品不存在或不属于当前店铺");
        }
        return spu;
    }

    /**
     * 取 SPU 名下的 SKU（先校验 SPU 归属的调用方再调本方法），取不到即报错
     *
     * @param spuId 店铺商品 id（已确权）
     * @param skuId SKU id
     * @return SKU 实体
     */
    private StoreGoodsSku getOwnedSkuOrThrow(Long spuId, Long skuId) {
        StoreGoodsSku sku = skuService.getById(skuId);
        if (sku == null || !spuId.equals(sku.getSpuId())) {
            throw new ServiceException("SKU 不存在或不属于该商品");
        }
        return sku;
    }

    private boolean isOnShelf(StoreGoodsSku sku) {
        return sku != null && sku.getShelfStatus() != null && sku.getShelfStatus() == StoreGoodsSku.SHELF_ON;
    }

    private StoreGoodsSkuVO toSkuVO(StoreGoodsSku sku) {
        StoreGoodsSkuVO vo = new StoreGoodsSkuVO();
        BeanUtils.copyProperties(sku, vo, "specAttrs");
        vo.setSpecAttrs(readJsonList(sku.getSpecAttrs(), new TypeReference<List<SpecAttr>>() {}));
        return vo;
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
