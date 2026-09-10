package com.panoramic.common.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.panoramic.common.util.UserContext;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 实体公共字段自动填充处理器
 * <p>基于 BaseEntity 的 @TableField(fill=...) 注解自动填充创建/更新时间与创建/更新人。
 * 创建人/更新人统一写 {@code UserType:UserId} 字符串（如 {@code admin:1} / {@code store:7}）：
 * 多端身份空间（admin/store/user）下同一 id 可能指向不同的人，带类型前缀消歧；
 * 类型缺失时由 {@link UserContext#getUserType()} 回退 {@code admin}（与 LoginUser 缺省语义一致），
 * 只有拿不到 userId（未登录 / 未带身份头的内部调用）时才留空。</p>
 */
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    /** 审计字段写入格式的类型与 id 分隔符 */
    private static final String AUDIT_SEPARATOR = ":";

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        String operator = currentOperator();
        // strict 填充：仅当目标字段当前值为空且字段声明了对应 fill 策略时才写入
        this.strictInsertFill(metaObject, "createUser", String.class, operator);
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "updateUser", String.class, operator);
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        this.strictUpdateFill(metaObject, "updateUser", String.class, currentOperator());
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, now);
    }

    /**
     * 当前操作人标识（格式 {@code UserType:UserId}）
     *
     * @return 如 {@code admin:1}；未登录（无 userId）时返回 null（留空）
     */
    private String currentOperator() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return null;
        }
        return UserContext.getUserType().trim() + AUDIT_SEPARATOR + userId;
    }
}
