package com.panoramic.trade.order.infrastructure.feign;

import com.panoramic.contract.store.api.StoreClient;
import com.panoramic.trade.order.domain.port.GoodsQueryPort;
import com.panoramic.trade.order.domain.port.StockPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 商品 / 库存两个下游端口的**真实装配**：把它们指向 store 域的内部 Feign 接口。
 *
 * <p>开关是 {@code panoramic.trade.order.store-adapter=feign}，且<b>带 {@code matchIfMissing = true}</b>
 * ——该键是本服务「把商品 / 库存指向谁」的显式声明，而现在**只有这一个装配存在**（阶段一的
 * {@code infrastructure/mock} 内存脚手架已随 T4b 删除），故「键缺失」映射到的正是这一份，
 * 与「写错一个值（{@code feigned} / 空串）仍会一个 bean 都不装配、启动即报『找不到
 * GoodsQueryPort / StockPort 的 bean』」并不矛盾：缺失是「按唯一存在的装配走」，写错是「起不来」。</p>
 *
 * <p>⚠ 与 {@code OrderDomainConfiguration} 里订单仓库那个开关刻意不同：那边的 {@code jdbc|memory}
 * 二选一**不设默认值**（「缺失该按哪个」没有唯一答案，必须显式写）。这里是「默认值翻面」的结果
 * ——阶段一时 {@code matchIfMissing} 在 mock 侧，本轮随 mock 装配一并挪到 feign 侧，
 * 不存在「缺失时两边都不装配」的时刻。见 {@code application.yml} 该键处的注释。</p>
 *
 * <p>⚠ <b>两个适配器类刻意不是 {@code @Component}</b>：启动类的 {@code scanBasePackages} 会扫到本包，
 * 若它们各自成为 bean，{@code @ConditionalOnProperty} 就管不住它们了（条件只作用在本类这个装配点上），
 * 于是「开关写错」会被悄悄绕过。故与 {@code JdbcOrderRepository} 同款：类本身不带注解，
 * 由配置类在条件下显式创建。</p>
 */
@Configuration
@ConditionalOnProperty(name = "panoramic.trade.order.store-adapter", havingValue = "feign", matchIfMissing = true)
public class StoreFeignAdapterConfiguration {

    /**
     * 商品只读查询端口（store 域的交易侧 SKU 快照批量读）
     *
     * @param storeClient store 域内部 Feign 客户端（由 {@code @EnableFeignClients} 注册）
     */
    @Bean
    public GoodsQueryPort goodsQueryAdapter(StoreClient storeClient) {
        return new GoodsQueryAdapter(storeClient);
    }

    /**
     * 库存端口（store 域的扣减 / 按单回补）
     */
    @Bean
    public StockPort stockAdapter(StoreClient storeClient) {
        return new StockAdapter(storeClient);
    }
}
