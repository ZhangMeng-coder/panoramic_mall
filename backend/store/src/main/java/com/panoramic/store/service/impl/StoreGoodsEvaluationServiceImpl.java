package com.panoramic.store.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.panoramic.common.exception.ServiceException;
import com.panoramic.contract.store.dto.StoreGoodsEvaluationOrderQueryDTO;
import com.panoramic.contract.store.dto.StoreGoodsEvaluationPageQueryDTO;
import com.panoramic.contract.store.dto.StoreGoodsEvaluationReplyDTO;
import com.panoramic.contract.store.dto.StoreGoodsEvaluationStatQueryDTO;
import com.panoramic.contract.store.dto.StoreGoodsEvaluationSubmitDTO;
import com.panoramic.contract.store.vo.PageResult;
import com.panoramic.contract.store.vo.StoreGoodsEvaluationPageItemVO;
import com.panoramic.contract.store.vo.StoreGoodsEvaluationScoreCountVO;
import com.panoramic.contract.store.vo.StoreGoodsEvaluationSkuVO;
import com.panoramic.contract.store.vo.StoreGoodsEvaluationStatVO;
import com.panoramic.store.entity.StoreGoodsEvaluation;
import com.panoramic.store.mapper.StoreGoodsEvaluationMapper;
import com.panoramic.store.service.StoreGoodsEvaluationService;
import com.panoramic.store.service.StoreGoodsSpuService;
import com.panoramic.store.service.StoreShopService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 商品评价服务实现（store 域）。
 * <p><b>评分冗余列的唯一写入口是 {@link #refreshScores}</b>：商品与店铺的 {@code score} 都由它
 * 按本表重算后经 owner service 回写（本类不持 SPU / 店铺 Mapper，跨实体只走对方 service）。
 * 回写必须走 {@code lambdaUpdate().set(...)}——{@code updateById} 跳过 null 列，
 * 「最后一条评价被清成无评价 → 评分清回 NULL」这条会静默不落库（与 {@code refreshMinPrice} 同坑）。</p>
 * <p><b>不并入 {@code refreshDerived}</b>：那是 SPU 上下架与最低价的推导入口，由 SKU 变动触发；
 * 评分由评价变动触发，两者触发源与事务边界都不同，合并只会让「改 SKU 顺手重算评分」这种无谓写入发生。</p>
 * <p><b>域内不做鉴权 / 不做订单状态判断</b>：审核状态、订单是否已完成、调用者是不是评价人本人，
 * 全由端 BFF 前置（域内只按传入的 {@code customerId} / {@code storeId} 过滤）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StoreGoodsEvaluationServiceImpl extends ServiceImpl<StoreGoodsEvaluationMapper, StoreGoodsEvaluation>
        implements StoreGoodsEvaluationService {

    /** 跨实体：商品 SPU 服务（按 spuId 反查店铺、回写商品评分、批量回查商品名） */
    private final StoreGoodsSpuService spuService;
    /** 跨实体：店铺服务（回写店铺评分） */
    private final StoreShopService shopService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submit(StoreGoodsEvaluationSubmitDTO dto) {
        // 评分取值：DTO 上已有 @Min/@Max，这里再断言一次是**域内防线**（Feign 调用方绕过页面校验时仍然拦得住）
        Integer score = dto.getScore();
        if (score == null || score < StoreGoodsEvaluation.SCORE_MIN || score > StoreGoodsEvaluation.SCORE_MAX) {
            throw new ServiceException("评分必须为1~5星");
        }
        // 商品存在性 + 归属反查（⚠ 反查不过滤逻辑删除：下架/锁定/软删的商品，历史订单照常可评价）。
        // 取不到即报「商品不存在」，此处拿到的 storeId 即本次评价的归属，调用方无权指定
        Long storeId = spuService.getStoreIdOfSpuOrThrow(dto.getSpuId());

        // 防线一：唯一键 (order_no, spu_id) 先查。命中即「已评价」——给的是 400 而不是靠撞键回 500，
        // 因为「重复提交」是用户点两下的正常态，不是故障（500 还会按 5xx 计入调用方熔断失败率）
        if (count(Wrappers.<StoreGoodsEvaluation>lambdaQuery()
                .eq(StoreGoodsEvaluation::getOrderNo, dto.getOrderNo())
                .eq(StoreGoodsEvaluation::getSpuId, dto.getSpuId())) > 0) {
            throw new ServiceException("该商品已评价");
        }

        StoreGoodsEvaluation entity = new StoreGoodsEvaluation();
        entity.setOrderNo(dto.getOrderNo());
        entity.setSpuId(dto.getSpuId());
        entity.setStoreId(storeId);
        entity.setCustomerId(dto.getCustomerId());
        entity.setScore(score);
        // 空白串归一成 null：前端清空输入常提交 ""，存 null 才能让「没写文字」只有一种表达
        entity.setContent(StringUtils.hasText(dto.getContent()) ? dto.getContent().trim() : null);
        entity.setSkuSnapshot(writeJson(dto.getSkuSnapshot()));
        try {
            // 审计列由 MyMetaObjectHandler 经 save 自动填充；本方法带 @Transactional（外层的），
            // 与随后的评分回写同成同败
            save(entity);
        } catch (DuplicateKeyException e) {
            // 防线二：上面那次 count 与本次 save 之间没有锁，同单同商品的并发提交会双双通过 count、
            // 后者撞唯一键。就地翻译成同一条业务错误（**不吞**：两次提交只有一次落库，语义正确）。
            // ⚠ 之所以安全：① 本方法没有内层 @Transactional 边界，异常在方法体里被捕获、不穿过事务代理；
            //    ② MySQL/InnoDB 的重复键错误只让当前语句失败、不中止整个事务（与 PostgreSQL 不同）。
            //    此处随即抛 ServiceException → 外层事务回滚（本次 INSERT 本就没落库），
            //    评分回写不会执行，与「这次提交没发生」一致。
            log.info("评价并发提交撞唯一键，已翻译为业务错误（orderNo={}, spuId={}）", dto.getOrderNo(), dto.getSpuId());
            throw new ServiceException("该商品已评价");
        }
        // 同一事务内重算并回写两处评分（重算而非增量累加）
        refreshScores(storeId, dto.getSpuId());
    }

    @Override
    public PageResult<StoreGoodsEvaluationPageItemVO> page(StoreGoodsEvaluationPageQueryDTO dto) {
        boolean hasScores = dto.getScores() != null && !dto.getScores().isEmpty();
        LambdaQueryWrapper<StoreGoodsEvaluation> qw = Wrappers.<StoreGoodsEvaluation>lambdaQuery()
                .eq(dto.getStoreId() != null, StoreGoodsEvaluation::getStoreId, dto.getStoreId())
                .eq(dto.getSpuId() != null, StoreGoodsEvaluation::getSpuId, dto.getSpuId())
                .in(hasScores, StoreGoodsEvaluation::getScore, hasScores ? dto.getScores() : null)
                // 时间倒序；⚠ 必带 id 次级键：同一秒可落多条评价，无全序时 LIMIT/OFFSET 翻页会重复/漏行
                .orderByDesc(StoreGoodsEvaluation::getCreateTime)
                .orderByDesc(StoreGoodsEvaluation::getId);
        IPage<StoreGoodsEvaluation> result = page(dto.toPage(StoreGoodsEvaluation.class), qw);

        List<StoreGoodsEvaluation> rows = result.getRecords();
        if (rows.isEmpty()) {
            return new PageResult<>(result.getTotal(), Collections.emptyList());
        }
        // 商品名一次批量回查（跨实体只走 owner service）：⚠ 已软删的商品不在结果里 → spuName 为 null，
        // 兜底文案由各端 BFF 决定（域内不替调用方编展示语）
        Map<Long, String> spuNames = spuService.nameMap(rows.stream()
                .map(StoreGoodsEvaluation::getSpuId)
                .distinct()
                .collect(Collectors.toList()));
        List<StoreGoodsEvaluationPageItemVO> items = rows.stream()
                .map(row -> toItemVO(row, spuNames.get(row.getSpuId())))
                .collect(Collectors.toList());
        return new PageResult<>(result.getTotal(), items);
    }

    @Override
    public StoreGoodsEvaluationStatVO stat(StoreGoodsEvaluationStatQueryDTO dto) {
        QueryWrapper<StoreGoodsEvaluation> qw = new QueryWrapper<>();
        // 单条 GROUP BY 聚合（与商品筛选聚合 facetBy 同一手法），不逐条取回内存再算
        qw.select("score", "COUNT(*) AS cnt")
          .eq(dto.getSpuId() != null, "spu_id", dto.getSpuId())
          .eq(dto.getStoreId() != null, "store_id", dto.getStoreId())
          .groupBy("score");
        Map<Integer, Long> counts = new HashMap<>();
        for (Map<String, Object> row : listMaps(qw)) {
            counts.put(((Number) row.get("score")).intValue(), ((Number) row.get("cnt")).longValue());
        }
        // 固定 1~5 五行、升序、缺的补 0：前端拿到就能直接画，不必自己补缺失星级
        List<StoreGoodsEvaluationScoreCountVO> scores = new ArrayList<>(StoreGoodsEvaluation.SCORE_MAX);
        long total = 0L;
        for (int star = StoreGoodsEvaluation.SCORE_MIN; star <= StoreGoodsEvaluation.SCORE_MAX; star++) {
            long count = counts.getOrDefault(star, 0L);
            total += count;
            scores.add(new StoreGoodsEvaluationScoreCountVO(star, count));
        }
        StoreGoodsEvaluationStatVO vo = new StoreGoodsEvaluationStatVO();
        vo.setTotal(total);
        vo.setScores(scores);
        return vo;
    }

    @Override
    public List<Long> listEvaluatedSpuIds(String orderNo, StoreGoodsEvaluationOrderQueryDTO dto) {
        if (!StringUtils.hasText(orderNo)) {
            return Collections.emptyList();
        }
        // 唯一键 (order_no, spu_id) 保证同单同商品至多一条，故出参天然无重复，不必 distinct
        return list(Wrappers.<StoreGoodsEvaluation>lambdaQuery()
                .select(StoreGoodsEvaluation::getSpuId)
                .eq(StoreGoodsEvaluation::getOrderNo, orderNo)
                .eq(dto.getCustomerId() != null, StoreGoodsEvaluation::getCustomerId, dto.getCustomerId()))
                .stream()
                .map(StoreGoodsEvaluation::getSpuId)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reply(Long id, StoreGoodsEvaluationReplyDTO dto) {
        StoreGoodsEvaluation stored = getById(id);
        // 归属校验按「id + storeId」双条件：他人评价与不存在的评价同样报「评价不存在」，不泄露存在性
        if (stored == null || !Objects.equals(stored.getStoreId(), dto.getStoreId())) {
            throw new ServiceException("评价不存在");
        }
        // 这次读只为把「已回复」与「不存在」分成两种话术；并发下的真正判据是下面条件更新的影响行数
        if (stored.getReplyContent() != null) {
            throw new ServiceException("该评价已回复");
        }
        boolean updated = update(null, Wrappers.<StoreGoodsEvaluation>lambdaUpdate()
                .eq(StoreGoodsEvaluation::getId, id)
                .eq(StoreGoodsEvaluation::getStoreId, dto.getStoreId())
                // 以「回复列为空」为条件：并发双回复只有一个能命中
                .isNull(StoreGoodsEvaluation::getReplyContent)
                .set(StoreGoodsEvaluation::getReplyContent, dto.getReplyContent().trim())
                .set(StoreGoodsEvaluation::getReplyTime, LocalDateTime.now()));
        if (!updated) {
            throw new ServiceException("该评价已回复，请刷新后重试");
        }
        // 刻意不重算评分：评分只由星级决定，回复不改分
    }

    // ---- 评分重算（商品 / 店铺两个冗余列的唯一写入口）----

    /**
     * 重算并回写商品评分与所属店铺评分（<b>同一事务内</b>，与评价写入同成同败）。
     *
     * @param storeId 评价所属店铺 id（已由 spuId 反查得到，不再二次反查）
     * @param spuId   被评价的商品 SPU id
     */
    private void refreshScores(Long storeId, Long spuId) {
        // 回写经对方 owner service（本类不持 SPU / 店铺 Mapper）
        spuService.updateScore(spuId, avgScore(spuId, null));
        shopService.updateScore(storeId, avgScore(null, storeId));
    }

    /**
     * 平均评分：**全部评价的算术平均**（每笔等权），保留 1 位小数（四舍五入）；无评价返回 {@code null}。
     *
     * @param spuId   按商品统计（null = 不按商品限定）
     * @param storeId 按店铺统计（null = 不按店铺限定）
     * @return 平均分；无评价为 null（**不是 0**——0 分与「暂无评分」是两回事）
     */
    private BigDecimal avgScore(Long spuId, Long storeId) {
        QueryWrapper<StoreGoodsEvaluation> qw = new QueryWrapper<>();
        qw.select("AVG(score) AS avg_score")
          .eq(spuId != null, "spu_id", spuId)
          .eq(storeId != null, "store_id", storeId);
        List<Map<String, Object>> rows = listMaps(qw);
        if (rows.isEmpty()) {
            return null;
        }
        Object avg = rows.get(0).get("avg_score");
        if (avg == null) {
            // 无评价行时 AVG 为 NULL（聚合查询仍返回一行）——清回 NULL 由调用方以 set 显式落库
            return null;
        }
        return new BigDecimal(avg.toString()).setScale(1, RoundingMode.HALF_UP);
    }

    /**
     * 实体 → 列表项（{@code spuName} 由调用方批量取好后传入；SKU 快照由 JSON 串解析成 List）
     */
    private StoreGoodsEvaluationPageItemVO toItemVO(StoreGoodsEvaluation entity, String spuName) {
        StoreGoodsEvaluationPageItemVO vo = new StoreGoodsEvaluationPageItemVO();
        vo.setId(entity.getId());
        vo.setSpuId(entity.getSpuId());
        vo.setSpuName(spuName);
        vo.setSkuSnapshot(readJsonList(entity.getSkuSnapshot(),
                new TypeReference<List<StoreGoodsEvaluationSkuVO>>() {}));
        vo.setScore(entity.getScore());
        vo.setContent(entity.getContent());
        vo.setCustomerId(entity.getCustomerId());
        vo.setCreateTime(entity.getCreateTime());
        vo.setReplyContent(entity.getReplyContent());
        vo.setReplyTime(entity.getReplyTime());
        return vo;
    }

    /**
     * JSON 序列化（null 输入序列化为 null 存库）——与 {@code StoreGoodsSpuServiceImpl} 同一手法
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
