package com.panoramic.common.enums;

import lombok.Getter;

@Getter
public enum ServiceExceptionEnums {

    PARAM_ERROR(400, "参数错误"),
    DATA_NOT_EXIST(504, "数据不存在"),
    FILE_UPLOAD_ERROR(505, "文件上传失败"),
    SAVE_ERROR(500, "保存失败"),
    UPDATE_ERROR(500, "更新失败"),
    DELETE_ERROR(500, "删除失败"),
    DATA_EXIST(513, "数据已存在"),
    USERNAME_PASSWORD_ERROR(514, "用户名或密码错误"),
    USER_DISABLED(515, "用户已被停用"),;

    private final Integer code;
    private final String message;

    ServiceExceptionEnums(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}
