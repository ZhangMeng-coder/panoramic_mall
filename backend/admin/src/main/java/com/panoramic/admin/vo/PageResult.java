package com.panoramic.admin.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 分页响应结构
 * <p>goods 模块自带同名 PageResult，admin 模块暂不引用 goods，故此处自带一份。</p>
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
