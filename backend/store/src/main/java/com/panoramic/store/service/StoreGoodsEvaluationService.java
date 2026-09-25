package com.panoramic.store.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.panoramic.contract.store.dto.StoreGoodsEvaluationOrderQueryDTO;
import com.panoramic.contract.store.dto.StoreGoodsEvaluationPageQueryDTO;
import com.panoramic.contract.store.dto.StoreGoodsEvaluationReplyDTO;
import com.panoramic.contract.store.dto.StoreGoodsEvaluationStatQueryDTO;
import com.panoramic.contract.store.dto.StoreGoodsEvaluationSubmitDTO;
import com.panoramic.contract.store.vo.PageResult;
import com.panoramic.contract.store.vo.StoreGoodsEvaluationPageItemVO;
import com.panoramic.contract.store.vo.StoreGoodsEvaluationStatVO;
import com.panoramic.store.entity.StoreGoodsEvaluation;

import java.util.List;

/**
 * 商品评价服务（store 域）。
 * <p>own-entity CRUD 直接用 MyBatis-Plus 基类（IService）内置方法；本接口承载评价的五个领域入口：
 * 写两条（提交 / 商家回复）+ 读三条（分页 / 星级分布 / 按单查已评价商品）。</p>
 * <p><b>评分口径（本域唯一写入口在 {@code refreshScores}）</b>：商品评分 = 该 SPU 全部评价的算术平均，
 * 店铺评分 = 该店全部评价的算术平均（每笔等权），<b>无评价为 NULL</b>（不是 0 分）；写入评价时在
 * <b>同一事务内</b>重算并回写两张表的冗余列，<b>重算而非增量累加</b>（增量会让并发与纠错都变难），
 * 评价回复<b>不改评分</b>。</p>
 * <p><b>读能力跨店通用</b>（无作用域锚点，限定条件全由调用方自设）：C 端传 {@code spuId}、
 * 商户端传 {@code storeId}，域内只做「传了就按它筛」，不判身份、不做端别分流。</p>
 * <p><b>「订单已完成」的门禁不在本域</b>：那是 mall-bff 的前置业务校验，域内不依赖 trade 域
 * （cross-cutting 第 24 条）。本域的防线只有 {@code (order_no, spu_id)} 唯一键与评分取值 1~5。</p>
 */
public interface StoreGoodsEvaluationService extends IService<StoreGoodsEvaluation> {

    /**
     * 提交商品评价（一笔订单里的一个商品一条；同 SPU 多 SKU 合成一条 + SKU 快照）。
     * <p>店铺归属由 {@code spuId} 反查（⚠ <b>不过滤逻辑删除</b>：商品下架 / 锁定 / 软删后，
     * 历史订单照常可评价）。重复提交同单同商品 → 400「该商品已评价」（唯一键先查 + 撞键同译）。</p>
     * <p>写入成功后同一事务内重算该商品与所属店铺的评分。</p>
     *
     * @param dto 评价内容（订单号 / 商品 / 评价人 / 星级 / 文字 / SKU 快照）
     */
    void submit(StoreGoodsEvaluationSubmitDTO dto);

    /**
     * 评价分页（时间倒序；跨店通用——按 {@code storeId} / {@code spuId} / {@code scores} 任意组合筛选）。
     * <p>商品名批量回查（跨实体只走 owner service），<b>商品已软删时 {@code spuName} 为 null</b>；
     * 出参带 {@code customerId}，昵称头像由端 BFF 批量补。</p>
     *
     * @param dto 分页与筛选参数
     * @return 分页结果
     */
    PageResult<StoreGoodsEvaluationPageItemVO> page(StoreGoodsEvaluationPageQueryDTO dto);

    /**
     * 评价星级分布（固定 1~5 五行、升序、无评价的星级补 0）+ 总条数。
     *
     * @param dto 分布查询参数（{@code spuId} / {@code storeId} 任意组合）
     * @return 星级分布
     */
    StoreGoodsEvaluationStatVO stat(StoreGoodsEvaluationStatQueryDTO dto);

    /**
     * 查某订单里<b>已评价过</b>的商品 SPU id 集合（订单详情页标「已评价 / 待评价」用）。
     * <p>唯一键 {@code (order_no, spu_id)} 已保证同单同商品至多一条，故出参天然无重复。
     * 订单号为空（理论上不可达）直接返回空列表，不发 SQL。</p>
     *
     * @param orderNo 订单号（路径变量）
     * @param dto     顾客锚点（可空 = 不限定）
     * @return 已评价的商品 SPU id 列表（无则空列表）
     */
    List<Long> listEvaluatedSpuIds(String orderNo, StoreGoodsEvaluationOrderQueryDTO dto);

    /**
     * 商家回复评价：一条评价至多一条回复，以「回复列为空」为条件更新，<b>影响行数是唯一判据</b>。
     * <p>⚠ 归属校验用「id + storeId」双条件，他人评价与不存在的评价<b>同样报「评价不存在」</b>，
     * 不泄露存在性；已回复 → 400「该评价已回复」。回复不支持修改 / 删除（C 端也不能追评）。</p>
     * <p>回复<b>不重算评分</b>：评分只由星级决定。</p>
     *
     * @param id  评价 id
     * @param dto 回复内容 + 店铺归属锚点
     */
    void reply(Long id, StoreGoodsEvaluationReplyDTO dto);
}
