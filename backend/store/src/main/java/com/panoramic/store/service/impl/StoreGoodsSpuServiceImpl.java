package com.panoramic.store.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.panoramic.common.exception.ServiceException;
import com.panoramic.contract.store.dto.SpecAttr;
import com.panoramic.contract.store.dto.SpecConfigItem;
import com.panoramic.contract.store.dto.StoreGoodsLockDTO;
import com.panoramic.contract.store.dto.StoreGoodsSkuDTO;
import com.panoramic.contract.store.dto.StoreGoodsSkuReplaceDTO;
import com.panoramic.contract.store.dto.StoreGoodsSpuCrossShopPageQueryDTO;
import com.panoramic.contract.store.dto.StoreGoodsSpuFacetQueryDTO;
import com.panoramic.contract.store.dto.StoreGoodsSpuPageQueryDTO;
import com.panoramic.contract.store.dto.StoreGoodsSpuSaveDTO;
import com.panoramic.contract.store.dto.StoreGoodsSpuUpdateDTO;
import com.panoramic.contract.store.dto.StoreGoodsStockBatchUpdateDTO;
import com.panoramic.contract.store.dto.StoreGoodsStockPageQueryDTO;
import com.panoramic.contract.store.dto.StoreGoodsStockUpdateDTO;
import com.panoramic.contract.store.vo.PageResult;
import com.panoramic.contract.store.vo.StoreGoodsFacetItemVO;
import com.panoramic.contract.store.vo.StoreGoodsSkuVO;
import com.panoramic.contract.store.vo.StoreGoodsStockPageItemVO;
import com.panoramic.contract.store.vo.StoreGoodsSpuCrossShopPageItemVO;
import com.panoramic.contract.store.vo.StoreGoodsSpuDetailVO;
import com.panoramic.contract.store.vo.StoreGoodsSpuFacetVO;
import com.panoramic.contract.store.vo.StoreGoodsSpuPageItemVO;
import com.panoramic.contract.store.vo.StoreGoodsSpuPlatformDetailVO;
import com.panoramic.common.util.UserContext;
import com.panoramic.store.entity.StoreGoodsSku;
import com.panoramic.store.entity.StoreGoodsSkuStock;
import com.panoramic.store.entity.StoreGoodsSpu;
import com.panoramic.store.mapper.StoreGoodsSpuMapper;
import com.panoramic.store.service.StoreGoodsSkuService;
import com.panoramic.store.service.StoreGoodsSkuStockService;
import com.panoramic.store.service.StoreGoodsSpuService;
import com.panoramic.store.service.StoreShopService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
 * <p><b>owner 侧（数据权限 R11）</b>：owner 方法入口一律 {@link #getOwnedOrThrow}
 * 以「id + store_id」双条件取行——他人商品与不存在的商品同样报「商品不存在」，
 * 不泄露存在性；SKU 侧操作先经 {@link #getOwnedSkuOrThrow} 校验 SPU 归属。
 * <b>owner 侧锁定只读守卫（R12）</b>：改 / 删 / 改 SKU / 上下架 在取行后立即
 * {@link #assertNotLocked} 拦截——锁定期整行只读由域内强制，不只靠前端禁用按钮。</p>
 * <p><b>platform 侧（跨店通用，调用方自设限定条件）</b>：{@link #crossShopPage} / {@link #facets} / {@link #platformDetail}
 * 不带 store_id 过滤——{@link #crossShopPage} 供 admin BFF「店铺商品管理」与 mall-bff C 端浏览共用（差别只在传入条件），
 * {@link #facets} 供 mall-bff C 端筛选面板，{@link #platformDetail} / {@link #platformDetails} 供 admin BFF
 * 与 mall-bff（后者是 mall-bff 购物车列表的批量详情，一次取回多个 SPU）；
 * {@link #lock} / {@link #unlock} 是锁定的唯一写入口。</p>
 * <p><b>推导量不变量</b>：SPU 的 {@code shelf_status} 与 {@code min_price} 从不直接接受入参，
 * 只由 {@link #refreshDerived} 按名下 SKU 重算，保证 {@code SPU上架 ⟺ ≥1 SKU 上架} 与
 * {@code min_price = 上架且未删 SKU 的最低价}；
 * 锁定的级联下架也不破例——先下架 SKU，再由它推导 SPU。</p>
 * <p><b>域内不做鉴权/审核判断</b>：店铺 {@code status == 2} 的门禁由端 BFF 前置（R9），
 * 审计字段由 MyMetaObjectHandler 经 UserContext 自动填充，本类一律不手写。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StoreGoodsSpuServiceImpl extends ServiceImpl<StoreGoodsSpuMapper, StoreGoodsSpu>
        implements StoreGoodsSpuService {

    /** 跨实体：SKU 服务（列表/计数/级联删除/级联下架；不直接持有 SKU Mapper） */
    private final StoreGoodsSkuService skuService;
    /** 跨实体：SKU 库存服务（库存表读写只走它；不直接持有库存 Mapper / 表名） */
    private final StoreGoodsSkuStockService skuStockService;
    /** 跨实体：店铺服务（跨店列表/详情回填 storeName，以及按审核状态取店铺 id 做过滤） */
    private final StoreShopService shopService;
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
        StoreGoodsSpuDetailVO vo = new StoreGoodsSpuDetailVO();
        buildDetail(getOwnedOrThrow(storeId, id), vo);
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
        assertNotLocked(spu, "编辑");
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
        StoreGoodsSpu spu = getOwnedOrThrow(storeId, id);
        assertNotLocked(spu, "删除");
        // R8：存在已上架 SKU 时拒绝删除
        if (skuService.hasOnShelfSku(id)) {
            throw new ServiceException("商品存在已上架 SKU，请先全部下架后再删除");
        }
        // SKU id 必须在删 SKU 之前取（删掉就查不到了），随后级联删库存行
        List<Long> skuIds = skuService.listBySpuId(id).stream()
                .map(StoreGoodsSku::getId)
                .collect(Collectors.toList());
        removeById(id);
        // 级联逻辑删除其下全部 SKU（走 SKU service）
        skuService.removeBySpuId(id);
        skuStockService.removeBySkuIds(skuIds);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void replaceSkus(Long storeId, Long id, StoreGoodsSkuReplaceDTO dto) {
        StoreGoodsSpu spu = getOwnedOrThrow(storeId, id);
        assertNotLocked(spu, "修改 SKU");
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
            // 级联逻辑删库存行（与 SKU 同事务：本方法已带 @Transactional）
            skuStockService.removeBySkuIds(removedIds);
        }
        // 整单替换可能清空/新增上架行，按最新 SKU 状态重新联动 SPU
        refreshDerived(spu);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSkuShelf(Long storeId, Long spuId, Long skuId, Integer shelfStatus) {
        StoreGoodsSpu spu = getOwnedOrThrow(storeId, spuId);
        assertNotLocked(spu, "上下架");
        StoreGoodsSku sku = getOwnedSkuOrThrow(spuId, skuId);
        sku.setShelfStatus(shelfStatus);
        skuService.updateById(sku);
        // R2/R3：SKU 上下架反向联动 SPU（上架任一 → SPU 上架；全下架 → SPU 下架）
        refreshDerived(spu);
    }

    // ---- owner 侧：SKU 库存（读走批量、写落库存表；本类只管归属校验与编排）----

    @Override
    public PageResult<StoreGoodsStockPageItemVO> pageStock(Long storeId, StoreGoodsStockPageQueryDTO dto) {
        String kw = dto.getKeyword() == null ? null : dto.getKeyword().trim();
        LambdaQueryWrapper<StoreGoodsSku> qw = Wrappers.<StoreGoodsSku>lambdaQuery()
                // 店铺过滤走子查询：避免把全店 SPU id 拉回来拼 IN 列表（子查询是单表过滤、走 idx_store_id）。
                // ⚠ 用 apply 而非 inSql：MP 3.5.16 的 inSql(R, String) 不接受参数（无 varargs 重载、
                // 也不认 {0} 占位符），带参子查询只能走 apply——两者的 AND/OR 连接语义一致。
                .apply("spu_id IN (SELECT id FROM store_goods_spu WHERE store_id = {0} AND is_delete = 0)",
                        storeId);
        if (dto.getShelfStatus() != null) {
            qw.eq(StoreGoodsSku::getShelfStatus, dto.getShelfStatus());
        }
        if (StringUtils.hasText(kw)) {
            String like = "%" + kw + "%";
            qw.and(w -> w.like(StoreGoodsSku::getSkuCode, kw)
                    .or()
                    .apply("spu_id IN (SELECT id FROM store_goods_spu WHERE name LIKE {0} AND is_delete = 0)",
                            like));
        }
        if (Boolean.TRUE.equals(dto.getLowStockOnly())) {
            // warn_stock 为 NULL 时 `stock <= warn_stock` 结果非真，天然排除未设预警的行
            qw.inSql(StoreGoodsSku::getId,
                    "SELECT sku_id FROM store_goods_sku_stock WHERE is_delete = 0 AND stock <= warn_stock");
        }
        qw.orderByDesc(StoreGoodsSku::getId);

        IPage<StoreGoodsSku> result = skuService.page(dto.toPage(StoreGoodsSku.class), qw);
        List<StoreGoodsSku> rows = result.getRecords();
        if (rows.isEmpty()) {
            return new PageResult<>(result.getTotal(), Collections.emptyList());
        }

        List<Long> skuIds = rows.stream().map(StoreGoodsSku::getId).collect(Collectors.toList());
        List<Long> spuIds = rows.stream().map(StoreGoodsSku::getSpuId).distinct().collect(Collectors.toList());
        // 一次批量读库存 + 一次批量读 SPU 名（无 N+1，均走快照读不加锁）
        Map<Long, StoreGoodsSkuStock> stockMap = skuStockService.mapBySkuIds(skuIds);
        Map<Long, String> spuNameMap = listByIds(spuIds).stream()
                .collect(Collectors.toMap(StoreGoodsSpu::getId, StoreGoodsSpu::getName));

        List<StoreGoodsStockPageItemVO> items = rows.stream().map(sku -> {
            StoreGoodsStockPageItemVO vo = new StoreGoodsStockPageItemVO();
            vo.setSkuId(sku.getId());
            vo.setSpuId(sku.getSpuId());
            vo.setSpuName(spuNameMap.get(sku.getSpuId()));
            vo.setMainImage(sku.getMainImage());
            vo.setSkuCode(sku.getSkuCode());
            vo.setPrice(sku.getPrice());
            vo.setShelfStatus(sku.getShelfStatus());
            vo.setSpecAttrs(readJsonList(sku.getSpecAttrs(), new TypeReference<List<SpecAttr>>() {}));
            StoreGoodsSkuStock st = stockMap.get(sku.getId());
            vo.setStock(st == null || st.getStock() == null ? 0 : st.getStock());
            vo.setLockedStock(st == null || st.getLockedStock() == null ? 0 : st.getLockedStock());
            vo.setWarnStock(st == null ? null : st.getWarnStock());
            return vo;
        }).collect(Collectors.toList());
        return new PageResult<>(result.getTotal(), items);
    }

    @Override
    public void updateSkuStock(Long storeId, Long skuId, StoreGoodsStockUpdateDTO dto) {
        Long spuId = getOwnedSpuIdOfSkuOrThrow(storeId, skuId);
        assertNotLocked(getById(spuId), "修改库存");
        skuStockService.updateStock(skuId, dto.getStock(), dto.getWarnStock());
    }

    @Override
    public void batchUpdateSkuStock(Long storeId, StoreGoodsStockBatchUpdateDTO dto) {
        List<Long> skuIds = dto.getSkuIds().stream().distinct().collect(Collectors.toList());
        // 逐个校验归属（不属于本店的直接拒绝，不静默跳过）
        Set<Long> spuIds = new HashSet<>();
        for (Long skuId : skuIds) {
            spuIds.add(getOwnedSpuIdOfSkuOrThrow(storeId, skuId));
        }
        for (Long spuId : spuIds) {
            assertNotLocked(getById(spuId), "修改库存");
        }
        skuStockService.batchUpdateStock(skuIds, dto.getStock());
    }

    // ---- platform 侧（跨店通用：调用方自设限定条件；详情/锁定仍只服务 admin BFF）----

    @Override
    public PageResult<StoreGoodsSpuCrossShopPageItemVO> crossShopPage(StoreGoodsSpuCrossShopPageQueryDTO dto) {
        Page<StoreGoodsSpu> spuPage = dto.toPage(StoreGoodsSpu.class);
        boolean hasCategoryFilter = dto.getCategoryIds() != null && !dto.getCategoryIds().isEmpty();
        boolean hasBrandFilter = dto.getBrandIds() != null && !dto.getBrandIds().isEmpty();
        // ⚠ DTO 的 brandIds 是复数（新），但**实体** StoreGoodsSpu 的字段仍是单数 brandId —— 这里是列引用，用 getBrandId
        // 店铺状态过滤：经店铺 service 取 id 集合后 IN（跨实体只走 owner service，不 join）
        List<Long> shopIds = dto.getShopStatus() == null
                ? Collections.emptyList() : shopService.idListByStatus(dto.getShopStatus());
        LambdaQueryWrapper<StoreGoodsSpu> wrapper = Wrappers.<StoreGoodsSpu>lambdaQuery()
                // 分类为多值子树匹配：categoryIds 已由端 BFF 用分类树展开（含全部后代）
                .in(hasCategoryFilter, StoreGoodsSpu::getCategoryId, dto.getCategoryIds())
                .in(hasBrandFilter, StoreGoodsSpu::getBrandId, dto.getBrandIds())
                .eq(dto.getStoreId() != null, StoreGoodsSpu::getStoreId, dto.getStoreId())
                .eq(dto.getShelfStatus() != null, StoreGoodsSpu::getShelfStatus, dto.getShelfStatus())
                .eq(dto.getLockStatus() != null, StoreGoodsSpu::getLockStatus, dto.getLockStatus())
                .like(StringUtils.hasText(dto.getKeyword()), StoreGoodsSpu::getName, dto.getKeyword());
        if (dto.getShopStatus() != null) {
            if (shopIds.isEmpty()) {
                // 永假哨兵：store_shop.id 自增必为正，-1 永不命中；用一个不可能匹配的 IN
                // （而非「跳过该条件」）保证「筛选无结果」不会退化成「不加筛选返回全量」
                wrapper.in(StoreGoodsSpu::getStoreId, List.of(-1L));
            } else {
                wrapper.in(StoreGoodsSpu::getStoreId, shopIds);
            }
        }
        applySort(wrapper, dto.getSort());
        IPage<StoreGoodsSpu> result = page(spuPage, wrapper);

        List<StoreGoodsSpu> records = result.getRecords();
        // 店铺名与 SKU 数量批量回填，避免 N+1
        Map<Long, String> shopNames = shopService.nameMap(
                records.stream().map(StoreGoodsSpu::getStoreId).collect(Collectors.toList()));
        Map<Long, Integer> skuCounts = skuService.countMapBySpuIds(
                records.stream().map(StoreGoodsSpu::getId).collect(Collectors.toList()));
        List<StoreGoodsSpuCrossShopPageItemVO> items = records.stream().map(spu -> {
            StoreGoodsSpuCrossShopPageItemVO vo = new StoreGoodsSpuCrossShopPageItemVO();
            // minPrice 同名同类型，随 copyProperties 一并带出（VO 已声明该字段）
            BeanUtils.copyProperties(spu, vo);
            vo.setStoreName(shopNames.getOrDefault(spu.getStoreId(), ""));
            vo.setSkuCount(skuCounts.getOrDefault(spu.getId(), 0));
            return vo;
        }).collect(Collectors.toList());
        return new PageResult<>(result.getTotal(), items);
    }

    /**
     * 施加排序：默认按 id 倒序；价格排序走 min_price。
     * <p>不处理 min_price 的 NULL 位置：C 端固定 shelf_status=1，而上架 SPU 必有上架 SKU（不变量），
     * 故 C 端 min_price 必非 null；管理端出现下架商品时排序位置不作保证。</p>
     *
     * @param wrapper 待施排序的查询条件
     * @param sort    排序标识（{@code priceAsc} / {@code priceDesc}；其它值按默认 id 倒序）
     */
    private void applySort(LambdaQueryWrapper<StoreGoodsSpu> wrapper, String sort) {
        if ("priceAsc".equals(sort)) {
            // ⚠ 必带 id 次级键：min_price 大量重复，无全序时 LIMIT/OFFSET 翻页会重复/漏行
            wrapper.orderByAsc(StoreGoodsSpu::getMinPrice).orderByAsc(StoreGoodsSpu::getId);
        } else if ("priceDesc".equals(sort)) {
            wrapper.orderByDesc(StoreGoodsSpu::getMinPrice).orderByAsc(StoreGoodsSpu::getId);
        } else {
            wrapper.orderByDesc(StoreGoodsSpu::getId);
        }
    }

    @Override
    public StoreGoodsSpuFacetVO facets(StoreGoodsSpuFacetQueryDTO dto) {
        StoreGoodsSpuFacetVO vo = new StoreGoodsSpuFacetVO();
        // ⚠ 两个维度互斥地排除自己：分类维度不带 filterCategoryIds，品牌维度不带 filterBrandIds
        vo.setCategories(facetBy(dto, "category_id", "category_name", true));
        vo.setBrands(facetBy(dto, "brand_id", "brand_name", false));
        return vo;
    }

    /**
     * 按某一维度的列做 GROUP BY 聚合。
     *
     * @param dto        查询参数
     * @param idColumn   维度列名（{@code category_id} / {@code brand_id}）
     * @param nameColumn 维度名称快照列名
     * @param isCategory true = 本次算分类维度（用 scopeCategoryIds + filterBrandIds）；false = 品牌维度（用 scope+filterCategoryIds）
     */
    private List<StoreGoodsFacetItemVO> facetBy(StoreGoodsSpuFacetQueryDTO dto, String idColumn,
                                                String nameColumn, boolean isCategory) {
        List<Long> scope = dto.getScopeCategoryIds();
        List<Long> filterCats = dto.getFilterCategoryIds();
        List<Long> filterBrands = dto.getFilterBrandIds();
        QueryWrapper<StoreGoodsSpu> qw = new QueryWrapper<>();
        qw.select(idColumn + " AS id", nameColumn + " AS name", "COUNT(*) AS cnt")
          .isNotNull(idColumn)
          .like(StringUtils.hasText(dto.getKeyword()), "name", dto.getKeyword())
          .eq(dto.getShelfStatus() != null, "shelf_status", dto.getShelfStatus())
          .eq(dto.getLockStatus() != null, "lock_status", dto.getLockStatus())
          .in(scope != null && !scope.isEmpty(), "category_id", scope)
          .in(isCategory && filterBrands != null && !filterBrands.isEmpty(), "brand_id", filterBrands)
          .in(!isCategory && filterCats != null && !filterCats.isEmpty(), "category_id", filterCats)
          .groupBy(idColumn, nameColumn)
          .orderByDesc("cnt")
          // ⚠ 必须补 id 升序兜底：`StoreGoodsFacetItemVO` / `StoreGoodsSpuFacetVO` 的 javadoc 承诺「按 count 降序，id 升序兜底」，
          // 而 COUNT 相同的项在 MySQL 里顺序不定 → 同一条件两次请求可能给出不同排列，前端筛选面板会莫名其妙地抖动
          .orderByAsc(idColumn);
        if (dto.getShopStatus() != null) {
            List<Long> shopIds = shopService.idListByStatus(dto.getShopStatus());
            if (shopIds.isEmpty()) {
                return Collections.emptyList();
            }
            qw.in("store_id", shopIds);
        }
        return listMaps(qw).stream().map(row -> {
            StoreGoodsFacetItemVO item = new StoreGoodsFacetItemVO();
            item.setId(((Number) row.get("id")).longValue());
            item.setName((String) row.get("name"));
            item.setCount(((Number) row.get("cnt")).intValue());
            return item;
        }).collect(Collectors.toList());
    }

    @Override
    public StoreGoodsSpuPlatformDetailVO platformDetail(Long id) {
        StoreGoodsSpu spu = getByIdOrThrow(id);
        StoreGoodsSpuPlatformDetailVO vo = new StoreGoodsSpuPlatformDetailVO();
        buildDetail(spu, vo);
        vo.setStoreName(spu.getStoreId() == null
                ? "" : shopService.nameMap(List.of(spu.getStoreId())).getOrDefault(spu.getStoreId(), ""));
        return vo;
    }

    @Override
    public List<StoreGoodsSpuPlatformDetailVO> platformDetails(List<Long> spuIds) {
        if (spuIds == null || spuIds.isEmpty()) {
            return Collections.emptyList();
        }
        // ⚠ 四段查询，条数与 spuIds 个数无关（无 N+1）：SPU → SKU → 可用库存 → 店铺名，随后内存分组组装
        List<StoreGoodsSpu> spus = listByIds(spuIds);
        if (spus == null || spus.isEmpty()) {
            // 查不到的 id 直接跳过（SPU 已删除），由调用方按「拿不到 = 商品不存在」处理，不抛异常
            return Collections.emptyList();
        }
        List<Long> ids = spus.stream().map(StoreGoodsSpu::getId).collect(Collectors.toList());
        Map<Long, String> shopNames = shopService.nameMap(spus.stream()
                .map(StoreGoodsSpu::getStoreId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList()));
        // SKU 一次 IN 取回后按 spu_id 分组；可用库存再对全量 SKU id 一次 IN（跨实体只走 owner service）
        List<StoreGoodsSku> allSkus = skuService.listBySpuIds(ids);
        Map<Long, List<StoreGoodsSku>> skuGroups = allSkus.stream()
                .collect(Collectors.groupingBy(StoreGoodsSku::getSpuId));
        Map<Long, Integer> availableMap = skuStockService.availableStockMapBySkuIds(
                allSkus.stream().map(StoreGoodsSku::getId).collect(Collectors.toList()));
        return spus.stream().map(spu -> {
            StoreGoodsSpuPlatformDetailVO vo = new StoreGoodsSpuPlatformDetailVO();
            // 字段映射复用单条路径同一份 buildDetail，SKU 与库存由批量结果传入
            buildDetail(spu, vo, skuGroups.getOrDefault(spu.getId(), Collections.emptyList()), availableMap);
            vo.setStoreName(shopNames.getOrDefault(spu.getStoreId(), ""));
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void lock(Long id, StoreGoodsLockDTO dto) {
        StoreGoodsSpu spu = getByIdOrThrow(id);
        if (isLocked(spu)) {
            throw new ServiceException("商品已锁定，无需重复操作");
        }
        // 锁定字段用条件更新显式写入：以「未锁定」为条件，防并发重复锁定；
        // 且 updateById 会跳过 null 字段，锁定位无法用实体逐字段赋值可靠落库
        boolean updated = update(null, Wrappers.<StoreGoodsSpu>lambdaUpdate()
                .eq(StoreGoodsSpu::getId, id)
                .eq(StoreGoodsSpu::getLockStatus, StoreGoodsSpu.LOCK_OFF)
                .set(StoreGoodsSpu::getLockStatus, StoreGoodsSpu.LOCK_ON)
                .set(StoreGoodsSpu::getLockReason, dto.getReason())
                .set(StoreGoodsSpu::getLockUser, currentActor())
                .set(StoreGoodsSpu::getLockTime, LocalDateTime.now()));
        if (!updated) {
            throw new ServiceException("商品已锁定，无需重复操作");
        }
        // 级联下架名下全部 SKU（锁上架商品 → 自动下架），再由不变量推导 SPU（D2）
        skuService.offShelfBySpuId(id);
        // ⚠ 必须重取实体：上面的条件更新绕过实体，手上这行的 lock_* 仍是旧值，
        // 直接拿来 updateById 会把 lock_status=0 写回去，把刚落的锁抹掉
        refreshDerived(getById(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unlock(Long id) {
        StoreGoodsSpu spu = getByIdOrThrow(id);
        if (!isLocked(spu)) {
            throw new ServiceException("商品未锁定");
        }
        // 解锁必须用显式 set null 清字段：MyBatis-Plus 默认 NOT_NULL 策略下 updateById 会跳过 null，
        // 库里上一轮的 lock_reason/lock_user/lock_time 清不掉
        update(null, Wrappers.<StoreGoodsSpu>lambdaUpdate()
                .eq(StoreGoodsSpu::getId, id)
                .eq(StoreGoodsSpu::getLockStatus, StoreGoodsSpu.LOCK_ON)
                .set(StoreGoodsSpu::getLockStatus, StoreGoodsSpu.LOCK_OFF)
                .set(StoreGoodsSpu::getLockReason, null)
                .set(StoreGoodsSpu::getLockUser, null)
                .set(StoreGoodsSpu::getLockTime, null));
        // 不动 SKU 与 shelf_status：解锁后保持下架，由店主手动重新上架（D2）
    }

    // ---- 联动与规则辅助 ----

    /**
     * 组装详情出参（owner / platform 两侧共用，避免复制 JSON 转换与 SKU 组装代码）。
     *
     * @param spu 店铺商品实体（已确权）
     * @param vo  目标 VO（owner 为 {@link StoreGoodsSpuDetailVO}，platform 为其子类）
     */
    private void buildDetail(StoreGoodsSpu spu, StoreGoodsSpuDetailVO vo) {
        // 单条路径保持自己的 SQL 形状（SKU 一条 + 可用库存一条，共 2 条），不借用批量路径的取数方式
        List<StoreGoodsSku> skuRows = skuService.listBySpuId(spu.getId());
        Map<Long, Integer> availableMap = skuStockService.availableStockMapBySkuIds(
                skuRows.stream().map(StoreGoodsSku::getId).collect(Collectors.toList()));
        buildDetail(spu, vo, skuRows, availableMap);
    }

    /**
     * 组装详情出参（字段映射的唯一实现，单条路径与批量路径共用，避免复制一份映射代码）。
     *
     * @param spu          店铺商品实体（已确权）
     * @param vo           目标 VO（owner 为 {@link StoreGoodsSpuDetailVO}，platform 为其子类）
     * @param skuRows      该 SPU 名下的 SKU 列表（单条路径按 SPU 查得；批量路径由全量结果内存分组得到）
     * @param availableMap skuId -> 可用库存（{@code stock}）；缺行按 0 计
     */
    private void buildDetail(StoreGoodsSpu spu, StoreGoodsSpuDetailVO vo, List<StoreGoodsSku> skuRows,
                             Map<Long, Integer> availableMap) {
        // imageList/specConfig 实体为 String(JSON)、VO 为 List，类型不一致需排除后手动转换
        BeanUtils.copyProperties(spu, vo, "imageList", "specConfig");
        vo.setImageList(readJsonList(spu.getImageList(), new TypeReference<List<String>>() {}));
        vo.setSpecConfig(readJsonList(spu.getSpecConfig(), new TypeReference<List<SpecConfigItem>>() {}));
        vo.setSkus(skuRows.stream()
                .map(s -> toSkuVO(s, availableMap.get(s.getId())))
                .collect(Collectors.toList()));
    }

    /**
     * owner 侧锁定只读守卫（R12）：商品被平台锁定期，店主侧改/删/改 SKU/上下架 一律拒绝。
     *
     * @param spu    店铺商品实体（已确权）
     * @param action 操作名（拼进提示，如「编辑」「删除」「修改 SKU」「上下架」）
     */
    private void assertNotLocked(StoreGoodsSpu spu, String action) {
        if (isLocked(spu)) {
            throw new ServiceException("商品已被平台锁定，不可" + action + "，请联系平台管理员");
        }
    }

    /**
     * 是否处于平台锁定态
     */
    private boolean isLocked(StoreGoodsSpu spu) {
        return spu != null && spu.getLockStatus() != null && spu.getLockStatus() == StoreGoodsSpu.LOCK_ON;
    }

    /**
     * 当前操作人标识（格式 {@code UserType:UserId}，如 {@code admin:1}），用于 lock_user 留痕。
     * <p>与 {@code MyMetaObjectHandler#currentOperator} 同口径：拿不到 userId（未登录 / 未带身份头）返回 null。
     * 这是业务列而非审计列（D7），故在此直取写入，不经自动填充。</p>
     */
    private String currentActor() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return null;
        }
        return UserContext.getUserType().trim() + ":" + userId;
    }

    /**
     * 按 id 取商品（platform 侧：跨店，不校验归属），不存在即报错
     *
     * @param id 店铺商品 id
     * @return 店铺商品实体
     */
    private StoreGoodsSpu getByIdOrThrow(Long id) {
        StoreGoodsSpu spu = getById(id);
        if (spu == null) {
            throw new ServiceException("商品不存在");
        }
        return spu;
    }

    /**
     * 推导量统一刷新入口：SPU 的 {@code shelf_status} 与 {@code min_price} 都只经此处写入。
     *
     * @param spu 店铺商品实体（须是**从库中重取**的最新行，不可用被条件更新绕过的旧对象）
     */
    private void refreshDerived(StoreGoodsSpu spu) {
        refreshShelfStatus(spu);
        refreshMinPrice(spu);
    }

    /**
     * 按名下 SKU 重算并回写 SPU 上下架状态（R2/R3）。
     * <p>不变量「SPU上架 ⟺ 至少一个 SKU 上架」的唯一写入口。状态未变则不发 UPDATE。</p>
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
     * 按名下上架 SKU 重算并回写 SPU 最低价。
     * <p>不变量「min_price = 上架且未删 SKU 的最低价」的唯一写入口。
     * ⚠ 必须<b>独立</b>比较 min_price 是否变化，不得复用 {@link #refreshShelfStatus} 的状态早退——
     * 上下架状态没变时 min_price 仍可能变（例：下架高价 SKU 后最低价改变但 SPU 仍为上架）。</p>
     */
    private void refreshMinPrice(StoreGoodsSpu spu) {
        BigDecimal derived = skuService.minPriceBySpuId(spu.getId());
        if (samePrice(spu.getMinPrice(), derived)) {
            return;
        }
        spu.setMinPrice(derived);
        // ⚠ 不能用 updateById：MP 默认 FieldStrategy 为 NOT_NULL，derived 为 null 时该列会被跳过，
        // 「SKU 全部下架 → min_price 清空」这条就静默不落库（同解锁清 lock_* 的陷阱）。
        // 条件更新 + 显式 set 是唯一可靠写法。
        lambdaUpdate()
                .set(StoreGoodsSpu::getMinPrice, derived)
                .eq(StoreGoodsSpu::getId, spu.getId())
                .update();
    }

    /** 价格等值比较（都用 compareTo：BigDecimal.equals 对精度敏感，10.0 与 10.00 判定不等） */
    private boolean samePrice(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) {
            return a == null && b == null;
        }
        return a.compareTo(b) == 0;
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
        // 建 0 行库存（初始库存仅新建行采信；已存在则只补不覆盖）
        skuStockService.saveIfAbsent(sku.getId(), skuDto.getStock());
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

    /**
     * 按 store_id 校验 SKU 归属：skuId → SKU.spuId → SPU.store_id 双条件。
     * <p>不属于本店与不存在同样报「SKU 不存在」，不泄露存在性。</p>
     *
     * @return 该 SKU 的 spuId（已确权）
     */
    private Long getOwnedSpuIdOfSkuOrThrow(Long storeId, Long skuId) {
        StoreGoodsSku sku = skuService.getById(skuId);
        if (sku == null) {
            throw new ServiceException("SKU 不存在");
        }
        getOwnedOrThrow(storeId, sku.getSpuId());
        return sku.getSpuId();
    }

    private boolean isOnShelf(StoreGoodsSku sku) {
        return sku != null && sku.getShelfStatus() != null && sku.getShelfStatus() == StoreGoodsSku.SHELF_ON;
    }

    /**
     * SKU 实体 → VO（{@code availableStock} 由调用方批量取好后传入，避免逐行查库存）
     *
     * @param sku            SKU 实体
     * @param availableStock 可用库存（{@code stock}）；null 按 0
     */
    private StoreGoodsSkuVO toSkuVO(StoreGoodsSku sku, Integer availableStock) {
        StoreGoodsSkuVO vo = new StoreGoodsSkuVO();
        BeanUtils.copyProperties(sku, vo, "specAttrs");
        vo.setSpecAttrs(readJsonList(sku.getSpecAttrs(), new TypeReference<List<SpecAttr>>() {}));
        vo.setAvailableStock(availableStock == null ? 0 : availableStock);
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
