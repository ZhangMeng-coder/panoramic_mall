package com.panoramic.common.store.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 分页响应结构（store 域专用独立一份，仿 goods 各域独立；store 域 / store-bff / admin 三方统一引用此份）
 *
 * @param <T> 记录类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {

    /**
     * 总记录数
     */
    private long total;

    /**
     * 当前页记录
     */
    private List<T> records;
}
