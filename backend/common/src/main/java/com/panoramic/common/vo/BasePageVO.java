package com.panoramic.common.vo;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 分页查询参数
 */
@Data
public class BasePageVO {

    /**
     * 当前页码
     */
    @NotNull(message = "当前页码不能为空")
    @Min(value = 1, message = "当前页码必须大于0")
    private Integer pageNum;

    /**
     * 每页数量
     */
    @NotNull(message = "每页数量不能为空")
    @Min(value = 1, message = "每页数量必须大于0")
    @Max(value = 100, message = "每页数量不能超过100")
    private Integer pageSize;


    public <T> Page<T> toPage(Class<T> clazz) {
        return new Page<>(pageNum, pageSize);
    }
}
