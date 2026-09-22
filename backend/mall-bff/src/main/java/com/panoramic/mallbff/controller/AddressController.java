package com.panoramic.mallbff.controller;

import com.panoramic.common.util.UserContext;
import com.panoramic.common.vo.RespData;
import com.panoramic.mallbff.dto.AddressSaveDTO;
import com.panoramic.mallbff.service.CustomerAddressBffService;
import com.panoramic.mallbff.vo.AddressStatusVO;
import com.panoramic.mallbff.vo.AddressVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * C 端收货地址接口（列表 / **状态** / 新增 / 编辑 / 删除 / 设默认，共 6 条）。
 * <p>⚠ <b>不能少一条、也不能多一条</b>：页面契约里没有「取单条地址详情」的端点
 * （{@code GET /addresses/{id}}），域侧虽有同名方法也不编排——见
 * docs/contracts/mall-bff.md 的接口清单。</p>
 * <p>⚠ {@code GET /addresses/status} 是**派生态**而不是地址数据（回「有没有地址 + 默认地址 id」，
 * 不回列表）：它是下单前的分支依据，本层缓存之。它<b>不是</b>「取单条地址详情」的替代——
 * 路径段 {@code status} 与 {@code {id}} 也不同形。</p>
 * <p>⚠ <b>顾客 id 只能取自 {@code UserContext}</b>（登录态），绝不从请求体 / 路径接收——
 * 域内不做任何鉴权（{@code customerId} 就是数据权限本身），BFF 是唯一授权点。</p>
 * <p>分层：本类只碰 {@link CustomerAddressBffService}，<b>不注入</b> {@code CustomerCenterClient}，
 * 页面类型 ↔ 域契约类型的映射收在该 service 内。</p>
 * <p>⚠ <b>C 端不接 RBAC</b>：本类没有、也不应有任何 {@code @PreAuthorize}——顾客对自己的地址簿全权限。
 * 本类全部路径<b>均需登录态</b>（登记在 {@code application.yml} 的「需登录态端点」注），
 * <b>不得</b>加进任何免鉴权白名单。</p>
 * <p>错误形状：下游业务 4xx 原样透传——「地址不存在」（404）与「地址超过 20 条」（400）如实回页面；
 * 下游故障才降级为 500「地址服务暂不可用，请稍后重试」。</p>
 */
@RestController
@RequestMapping("/addresses")
@RequiredArgsConstructor
public class AddressController {

    private final CustomerAddressBffService customerAddressBffService;

    /**
     * 我的收货地址列表（默认地址排最前；无地址返回空列表）
     */
    @GetMapping
    public RespData<List<AddressVO>> list() {
        return RespData.success(customerAddressBffService.list(UserContext.getUserId()));
    }

    /**
     * 我的地址状态（有没有地址 + 默认地址 id）——下单 / 改地址前判分支用，**不回地址列表**
     *
     * <p>⚠ 它由本层从地址列表派生并**缓存**（缓存口径见 {@code docs/contracts/mall-bff.md}），
     * 故页面在「有默认地址」这条主路径上不必再拉一次列表。</p>
     */
    @GetMapping("/status")
    public RespData<AddressStatusVO> status() {
        return RespData.success(customerAddressBffService.status(UserContext.getUserId()));
    }

    /**
     * 新增地址（首条自动设为默认；最多 20 条）
     */
    @PostMapping
    public RespData<Long> save(@Valid @RequestBody AddressSaveDTO dto) {
        return RespData.success(customerAddressBffService.save(UserContext.getUserId(), dto));
    }

    /**
     * 编辑地址（⚠ <b>不动默认位</b>：设默认只能走 {@code POST /addresses/{id}/default}）
     */
    @PutMapping("/{id}")
    public RespData<Void> update(@PathVariable("id") Long id, @Valid @RequestBody AddressSaveDTO dto) {
        customerAddressBffService.update(UserContext.getUserId(), id, dto);
        return RespData.success();
    }

    /**
     * 删除地址（⚠ 删掉默认地址后<b>不自动递补</b>）
     */
    @DeleteMapping("/{id}")
    public RespData<Void> delete(@PathVariable("id") Long id) {
        customerAddressBffService.delete(UserContext.getUserId(), id);
        return RespData.success();
    }

    /**
     * 设为默认地址
     */
    @PostMapping("/{id}/default")
    public RespData<Void> setDefault(@PathVariable("id") Long id) {
        customerAddressBffService.setDefault(UserContext.getUserId(), id);
        return RespData.success();
    }
}
