package com.panoramic.common.enums;

import lombok.Getter;

/**
 * 删除状态枚举
 */
@Getter
public enum DeleteTypeEnum {

    DELETED(1, "已删除"),
    NOT_DELETED(0, "未删除");

    private Integer code;
    private String message;

    DeleteTypeEnum(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}
