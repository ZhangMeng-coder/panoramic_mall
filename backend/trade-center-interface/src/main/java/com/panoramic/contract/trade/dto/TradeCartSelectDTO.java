package com.panoramic.contract.trade.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 设置购物车行选中状态请求参数（trade-center 域内部接口与 mall-bff 同源共享）。
 * <p>单行选中（{@code /items/{id}/selected}）与全选（{@code /selected}）共用本 DTO：两者的入参形状
 * 完全一致（就是一个布尔态），分成两个类只会得到两个逐字相同的类型。</p>
 * <p>⚠ {@code selected} <b>必须显式传</b>（{@code @NotNull}）：域侧是「整份覆盖」语义，
 * 若允许缺省，一次漏传就会被解释成「取消选中」——用户点一下别的按钮，选中态静默全掉。</p>
 */
@Data
public class TradeCartSelectDTO {

    /**
     * 选中状态：true=选中，false=未选中（整份覆盖，不是取反）
     */
    @NotNull(message = "选中状态不能为空")
    private Boolean selected;
}
