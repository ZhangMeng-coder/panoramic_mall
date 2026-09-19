package com.panoramic.contract.store.dto;

import com.panoramic.contract.store.dto.SpecAttr;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 店铺在售商品 SKU 请求参数（store 域内部接口与 store-bff 同源共享）。
 * <p>与中台标准 SKU 同构，额外带价格（中台模板无价格、无库存）。</p>
 */
@Data
public class StoreGoodsSkuDTO {

    /**
     * SKU 主键：空 = 新增行；非空 = 更新既有行（整单替换语义）
     */
    private Long id;

    /**
     * 规格属性组合
     */
    @NotEmpty(message = "SKU 规格组合不能为空")
    @Valid
    private List<SpecAttr> specAttrs;

    /**
     * SKU 编码
     */
    @Size(max = 64, message = "SKU 编码不能超过64个字符")
    private String skuCode;

    /**
     * SKU 图片 URL
     */
    @Size(max = 255, message = "SKU 图片地址长度不能超过255个字符")
    private String mainImage;

    /**
     * 价格（必填，≥0.01）
     */
    @NotNull(message = "SKU 价格不能为空")
    @DecimalMin(value = "0.01", message = "SKU 价格不能低于0.01")
    @Digits(integer = 8, fraction = 2, message = "SKU 价格最多8位整数、2位小数")
    private BigDecimal price;

    /**
     * 初始库存：<b>仅新增行（{@code id} 为空）时被采信</b>；已有行的库存由库存管理页维护，此处传了也忽略。
     * <p>可空 = 按 0 建库存行；库存只影响可卖件数，不影响上下架与 C 端可见性。</p>
     */
    @Min(value = 0, message = "库存不能为负")
    private Integer stock;
}
