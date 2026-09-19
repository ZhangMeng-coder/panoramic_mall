package com.panoramic.contract.customer.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 顾客资料保存请求参数（customer-center 域内部接口与 mall-bff 同源共享）。
 * <p>四个字段全部选填（本 DTO 不带 {@code @NotNull} / {@code @NotBlank}），
 * 但<b>语义是整份覆盖而非增量更新</b>：域侧对四列**无条件**写入，未传的字段会被写成 {@code NULL}。</p>
 * <p>⚠ 这是刻意的，与入口一致：页面侧是 {@code PUT /profile}（PUT 语义即整份替换），
 * 前端 `/account/profile` 是「昵称 / 头像 / 性别 / 生日」四项的整表单，每次都全量提交。
 * <b>调用方不得只传要改的字段</b>——那样会把其余字段清空。
 * （域侧用 {@code lambdaUpdate().set(...)} 写入而非 {@code updateById}，理由是后者会跳过 null 列，
 * 把「清空昵称 / 头像」静默丢掉；一旦发现某处需要「非 null 才更新」，那是新的接口，不是改这里。）</p>
 */
@Data
public class CustomerProfileSaveDTO {

    /**
     * 昵称
     */
    @Size(max = 50, message = "昵称不能超过50个字符")
    private String nickname;

    /**
     * 头像 URL
     */
    @Size(max = 255, message = "头像地址长度不能超过255个字符")
    private String avatar;

    /**
     * 性别：0未知，1男，2女
     * <p>⚠ 取值范围必须在此收口：DB 列是 {@code TINYINT}，{@code 9} 这类越界枚举会**静默入库**，
     * 前端拿不到任何已知分支；{@code 999} 则触发 MySQL 严格模式报错，经域兜底变成 HTTP 500
     * （5xx 会计入端 BFF 的熔断失败率，见 cross-cutting 第 13 条）。</p>
     */
    @Min(value = 0, message = "性别取值不正确")
    @Max(value = 2, message = "性别取值不正确")
    private Integer gender;

    /**
     * 生日
     */
    private LocalDate birthday;
}
