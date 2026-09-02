package com.panoramic.common.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.panoramic.common.util.UserContext;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 实体公共字段自动填充处理器
 * <p>基于 BaseEntity 的 @TableField(fill=...) 注解自动填充创建/更新时间与创建/更新人。
 * 当前未接入权限体系，UserContext 无用户时人名字段留空，后续接入后自动生效。</p>
 */
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        Long userId = UserContext.getUserId();
        Integer userInt = userId == null ? null : userId.intValue();
        // strict 填充：仅当目标字段当前值为空且字段声明了对应 fill 策略时才写入
        this.strictInsertFill(metaObject, "createUser", Integer.class, userInt);
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "updateUser", Integer.class, userInt);
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        Long userId = UserContext.getUserId();
        Integer userInt = userId == null ? null : userId.intValue();
        this.strictUpdateFill(metaObject, "updateUser", Integer.class, userInt);
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, now);
    }
}
