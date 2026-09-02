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
    FILE_EXIST_ERROR(506, "文件已存在"),
    FILE_DELETE_ERROR(507, "文件清除异常"),
    QUESTION_TYPE_ERROR(508, "题目类型错误"),
    ANSWER_COUNT_ERROR(509, "答案数量错误"),
    KNOWLEDGE_TREE_NOT_EXIST(510, "知识点树不存在"),
    NO_QUESTION_FOUND(511, "未找到符合条件的题目"),
    EXAM_SESSION_EXPIRED(512, "检索条件已过期，请重新选择"),
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
