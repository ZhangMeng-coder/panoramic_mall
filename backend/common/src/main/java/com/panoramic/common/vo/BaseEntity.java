package com.panoramic.common.vo;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 实体类基类
 * <p>创建人/更新人统一为字符串 {@code UserType:UserId}（如 {@code admin:1} / {@code store:7}），
 * 由 {@code MyMetaObjectHandler} 从 {@code UserContext} 自动填充；多端身份空间（admin/store/user）
 * 下同一 id 可能指向不同的人，故带上类型前缀消歧。数据库列相应为 {@code VARCHAR(32)}。</p>
 */
@Data
public class BaseEntity {

    /**
     * 创建人（格式 {@code UserType:UserId}，如 admin:1）
     */
    @TableField(fill = FieldFill.INSERT)
    private String createUser;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新人（格式 {@code UserType:UserId}，如 store:7）
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateUser;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 是否删除
     */
    @TableLogic
    private Integer isDelete;
}
