package com.panoramic.customer.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.panoramic.contract.customer.dto.CustomerProfileSaveDTO;
import com.panoramic.contract.customer.dto.CustomerStatsQueryDTO;
import com.panoramic.contract.customer.vo.CustomerProfileVO;
import com.panoramic.contract.customer.vo.CustomerStatsVO;
import com.panoramic.customer.entity.CustomerProfile;

import java.util.List;

/**
 * 顾客资料服务（customer-center 域下沉纯域）。
 * <p>own-entity CRUD 直接用 MyBatis-Plus 基类（IService）内置方法；本接口只承载资料的三个领域入口。
 * 数据权限锚点 {@code customerId = mall_user.id}：前两个方法**全按传入锚点过滤**，锚点即数据权限本身。
 * <b>本域不做任何鉴权/身份判断</b>——调用方传的 customerId 是否「本人」由 mall-bff 从登录态取，
 * 域侧不校验（防线在 BFF）。</p>
 * <p>⚠ {@link #getCustomerStats} 是<b>例外</b>：它数全平台顾客、<b>无作用域锚点</b>，调用方是 admin BFF
 * （平台首页看板），不是 C 端自助路径——「所有方法全按传入锚点过滤」对它不适用。</p>
 */
public interface CustomerProfileService extends IService<CustomerProfile> {

    /**
     * 读顾客资料：无记录返回空 VO（id 填 customerId、其余字段 null），**不返回 null、不抛 404**，
     * 调用方不必判空。
     *
     * @param customerId 顾客账号 id（== 资料主键）
     * @return 资料视图对象；未建资料行时仅有 id
     */
    CustomerProfileVO getProfile(Long customerId);

    /**
     * 保存顾客资料（惰性创建）：无记录则 INSERT（显式写 id=customerId），有则 UPDATE。
     *
     * @param customerId 顾客账号 id（== 资料主键）
     * @param dto        资料字段（全字段选填）
     */
    void saveProfile(Long customerId, CustomerProfileSaveDTO dto);

    /**
     * <b>批量</b>读顾客资料：一次取回多个顾客的资料行（评价列表补齐昵称 / 头像用），消除 N+1。
     * <p>⚠ 出参与 {@link #getProfile} 刻意不同形：<b>查不到的 id 跳过</b>（不补「仅含 id 的空 VO」），
     * 否则调用方分不清「真有一条空资料」与「压根没这个人」。空集合 → 空列表（一条 SQL 都不发）。</p>
     *
     * @param customerIds 顾客账号 id 集合（== 资料主键）
     * @return 命中的资料列表（不含查不到的 id），不返回 null
     */
    List<CustomerProfileVO> listProfilesByIds(List<Long> customerIds);

    /**
     * <b>顾客统计</b>（平台首页数据看板）：累计资料数 + 窗口内每日新增。
     * <p>⚠ 唯一不按锚点过滤的方法：数的是全平台顾客（见类注释）。</p>
     * <p>⚠ <b>一律按天出点</b>，月/季/年粒度与日历算术都在调用方（admin BFF）——域不接受粒度参数
     * （cross-cutting 第 25 条）。</p>
     *
     * @param query 窗口起止；两个字段都可省（省略即只回快照、折线为空列表）
     * @return 累计数 + 每日新增点；空窗口时 {@code newSeries} 为空列表，不返回 null
     * @throws com.panoramic.common.exception.ServiceException 窗口只给了一端、或结束早于开始（HTTP 400）
     */
    CustomerStatsVO getCustomerStats(CustomerStatsQueryDTO query);
}
