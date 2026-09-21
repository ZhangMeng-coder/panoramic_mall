package com.panoramic.trade.order.infrastructure.inmemory;

import com.panoramic.trade.order.domain.OrderAddress;
import com.panoramic.trade.order.domain.OrderLine;
import com.panoramic.trade.order.domain.OrderModel;
import com.panoramic.trade.order.domain.OrderSource;
import com.panoramic.trade.order.domain.port.OccupyResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 内存仓储的**先占键**语义：这是「一级幂等」在内存实现里的落点，也是单测能验到的那一层。
 *
 * <p>⚠ 为什么单独一个类：这些断言与下单链路无关，只关于**仓储这一件东西**——
 * 「占键什么时候才算数」「没提交的键算不算占用」「没有 requestId 的提交会不会互相顶掉」。
 * 混进编排层的用例里读，它们会淹在拆单 / 流水线的噪声里。</p>
 *
 * <p>⚠ 本类的口径与真实落库侧的差异**只在一处**：真实侧「未提交的键」是事务里未提交的行
 * （别人看不见），内存侧用 {@code 未 saveAll = 未提交} 来模拟。故这里钉住的正是
 * 「占键只在**提交后**才算数」这条语义；并发下的「后来者阻塞在唯一索引上」内存版表达不了，
 * 已登记为残留（见 {@code OrderRepository#occupy} 的 javadoc）。</p>
 */
class InMemoryOrderRepositoryTest {

    private static final Long CUSTOMER_ID = 11L;
    private static final String REQUEST_ID = "req-1";

    private static final OrderAddress ADDRESS =
            new OrderAddress("张三", "13800000000", "浙江省杭州市西湖区", "文一西路 969 号 1 幢 101 室");

    private InMemoryOrderRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryOrderRepository();
    }

    private static OrderModel order(String orderNo) {
        return OrderModel.open(orderNo, CUSTOMER_ID, 7L, "示例店铺", OrderSource.CART, ADDRESS,
                REQUEST_ID, "fp-" + orderNo, LocalDateTime.of(2026, 9, 21, 12, 0, 0), List.of(new OrderLine(10L, 1)));
    }

    // ── 占键与提交 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("首次占键 = created；同一 (顾客, requestId) 再占 → 回读同一个 submissionId 且 created=false")
    void occupyingTheSameKeyTwiceReturnsTheCommittedSubmission() {
        OccupyResult first = repository.occupy(CUSTOMER_ID, REQUEST_ID);
        assertThat(first.created()).isTrue();
        repository.saveAll(first.submissionId(), List.of(order("202609211200000001")));

        OccupyResult second = repository.occupy(CUSTOMER_ID, REQUEST_ID);

        assertThat(second.created()).isFalse();
        assertThat(second.submissionId()).isEqualTo(first.submissionId());
        // 回读拿到的必须是**首次提交落下的那一批**（不是空批、也不是按 requestId 重查的订单行）
        assertThat(repository.findCommittedBatch(CUSTOMER_ID, REQUEST_ID).orElseThrow())
                .extracting(OrderModel::getOrderNo).containsExactly("202609211200000001");
    }

    @Test
    @DisplayName("占了键但**没提交**（失败回滚的等价物）→ 键不算被占用，后来者仍是 created")
    void uncommittedKeyDoesNotBlockTheNextOccupant() {
        OccupyResult abandoned = repository.occupy(CUSTOMER_ID, REQUEST_ID);
        // 不 saveAll：模拟「先到者业务失败、键随事务一起回滚」
        assertThat(repository.findCommittedBatch(CUSTOMER_ID, REQUEST_ID)).isEmpty();

        OccupyResult next = repository.occupy(CUSTOMER_ID, REQUEST_ID);

        assertThat(next.created()).isTrue();
        assertThat(next.submissionId()).isNotEqualTo(abandoned.submissionId());
    }

    @Test
    @DisplayName("requestId 为空 → 每次占键都是新提交（第一级不生效，不该互相顶掉）")
    void blankRequestIdNeverCollides() {
        OccupyResult first = repository.occupy(CUSTOMER_ID, null);
        repository.saveAll(first.submissionId(), List.of(order("202609211200000001")));

        OccupyResult second = repository.occupy(CUSTOMER_ID, null);

        assertThat(second.created()).isTrue();
        assertThat(second.submissionId()).isNotEqualTo(first.submissionId());
        // 空 requestId 查不出提交记录（它压根没进映射），这与「查得到的空批」是两回事
        assertThat(repository.findCommittedBatch(CUSTOMER_ID, null)).isEmpty();
    }

    @Test
    @DisplayName("键的作用域是顾客内：不同顾客用同一个 requestId 互不影响")
    void keyIsScopedToTheCustomer() {
        OccupyResult mine = repository.occupy(CUSTOMER_ID, REQUEST_ID);
        repository.saveAll(mine.submissionId(), List.of(order("202609211200000001")));

        OccupyResult others = repository.occupy(99L, REQUEST_ID);

        assertThat(others.created()).isTrue();
        assertThat(others.submissionId()).isNotEqualTo(mine.submissionId());
    }

    @Test
    @DisplayName("同一批里既有复用笔也有新建笔 → 整批都在提交记录里（重放才返回得出同一批）")
    void committedBatchKeepsReusedOrdersToo() {
        OccupyResult submission = repository.occupy(CUSTOMER_ID, REQUEST_ID);
        repository.saveAll(submission.submissionId(),
                List.of(order("202609211200000001"), order("202609211200000002")));

        assertThat(repository.findCommittedBatch(CUSTOMER_ID, REQUEST_ID).orElseThrow())
                .extracting(OrderModel::getOrderNo)
                .containsExactly("202609211200000001", "202609211200000002");
        // 按 submissionId 回读（编排层命中键时走的那条）顺序也必须一致
        assertThat(repository.findBySubmissionId(submission.submissionId()))
                .extracting(OrderModel::getOrderNo)
                .containsExactly("202609211200000001", "202609211200000002");
    }
}
