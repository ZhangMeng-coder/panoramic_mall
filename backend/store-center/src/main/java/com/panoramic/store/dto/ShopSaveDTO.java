package com.panoramic.store.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 店铺保存/提交请求参数
 * <p>「保存草稿」仅要求店铺名称等基础字段；「提交审核」时由服务端校验完整资质字段
 * （联系人/电话/省市区地址/营业执照等），见 StoreShopService 提交逻辑。</p>
 */
@Data
public class ShopSaveDTO {

    /**
     * 店铺名称
     */
    @NotBlank(message = "店铺名称不能为空")
    @Size(max = 50, message = "店铺名称不能超过50个字符")
    private String shopName;

    /**
     * 店铺 LOGO 图片 URL
     */
    @Size(max = 255, message = "LOGO 地址长度不能超过255个字符")
    private String logo;

    /**
     * 店铺简介
     */
    @Size(max = 500, message = "店铺简介不能超过500个字符")
    private String intro;

    /**
     * 联系人姓名
     */
    @Size(max = 50, message = "联系人姓名不能超过50个字符")
    private String contactName;

    /**
     * 联系人电话
     */
    @Size(max = 20, message = "联系人电话不能超过20个字符")
    private String contactPhone;

    /**
     * 所在地区（省市区）
     */
    @Size(max = 100, message = "所在地区不能超过100个字符")
    private String region;

    /**
     * 详细地址
     */
    @Size(max = 255, message = "详细地址不能超过255个字符")
    private String address;

    /**
     * 营业执照企业名称
     */
    @Size(max = 100, message = "营业执照企业名称不能超过100个字符")
    private String licenseName;

    /**
     * 统一社会信用代码
     */
    @Size(max = 50, message = "统一社会信用代码不能超过50个字符")
    private String licenseNo;

    /**
     * 营业执照照片 URL
     */
    @Size(max = 255, message = "营业执照照片地址长度不能超过255个字符")
    private String licenseImg;
}
