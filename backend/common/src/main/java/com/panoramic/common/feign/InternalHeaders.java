package com.panoramic.common.feign;

/**
 * BFF → 业务域内部调用（Feign）的信任头常量。
 * <p>规约见 CLAUDE.md：内部调用携带「主身份 + type + scope」，
 * 主身份与用户类型复用 {@code X-User-Id} / {@code X-User-Type}（与网关透传同名头，下游经
 * {@code AuthTokenFilter} 从 Redis 重建登录用户做最终范围判定）；再叠加一个内部调用令牌
 * {@code X-Internal-Token}，证明调用方是可信的端 BFF 而非页面/公网流量。
 * scope（{@link #TRUST_SCOPE}）预留用于下游按调用意图区分授权（现阶段仅平台身份），供后续
 * store-bff/mall-bff 接入时扩展。</p>
 */
public final class InternalHeaders {

    private InternalHeaders() {
    }

    /** 内部调用令牌头名：值 = {@code panoramic.internal.secret}（调用方与被调用方必须一致） */
    public static final String TRUST_TOKEN = "X-Internal-Token";

    /** 内部调用意图/范围头名 */
    public static final String TRUST_SCOPE = "X-Internal-Scope";

    /** 平台身份范围：端 BFF 以平台后台身份调用业务域（如标准模板维护） */
    public static final String SCOPE_PLATFORM = "platform";

    /** 默认内部调用令牌（dev 兜底；生产以环境变量 {@code panoramic.internal.secret} 覆盖） */
    public static final String DEFAULT_TRUST_TOKEN = "panoramic-mall-internal-dev-token";
}
