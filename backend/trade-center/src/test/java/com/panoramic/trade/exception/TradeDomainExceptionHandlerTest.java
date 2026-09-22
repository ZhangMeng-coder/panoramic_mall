package com.panoramic.trade.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 「调用方入参错误」的两类 MVC 异常 → **400**，而不是兜底的 500。
 *
 * <p>⚠ 守的是哪条默认路径：{@code MissingServletRequestParameterException} /
 * {@code MethodArgumentTypeMismatchException} 都**不是** {@code IllegalArgumentException} 的子类，
 * 不显式接住就落到域兜底 {@code Exception} → 500。同一件事于是有两种失败模式
 * （DTO 字段漏传 → 400，裸 {@code @RequestParam} 漏传 → 500），
 * 且 5xx 会被端 BFF 按 cross-cutting 第 13 条**计入熔断失败率**——调用方漏一个参数就能打开熔断。</p>
 *
 * <p>⚠ <b>为什么走真实路由（standalone MockMvc）而不是直接调 handler 方法</b>：这两类的修复内容
 * 就是「谁被路由到哪个处理器 + 回什么状态码」。直接调方法只能钉住「该方法返回 400」——
 * 把 {@code @ExceptionHandler(MissingServletRequestParameterException.class)} 注解摘掉、让请求落回兜底 500，
 * 用例照样绿。故这里用 {@code standaloneSetup} 装配真控制器 + 真 advice，
 * 让 Spring 自己按注解选处理器（不起 Spring 上下文、不连库，代价只是几次内存内的派发）。</p>
 */
class TradeDomainExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new SampleController())
                .setControllerAdvice(new TradeDomainExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("缺必填的 @RequestParam → 400，文案带参数名")
    void missingRequestParamIsBadRequest() throws Exception {
        mockMvc.perform(get("/sample"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.msg").value(containsString("customerId")));
    }

    @Test
    @DisplayName("查询参数取值类型不符（customerId=abc）→ 400，文案带参数名")
    void typeMismatchOnQueryParamIsBadRequest() throws Exception {
        mockMvc.perform(get("/sample").param("customerId", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.msg").value(containsString("customerId")));
    }

    @Test
    @DisplayName("路径变量取值类型不符（/sample/abc）→ 400，文案带参数名")
    void typeMismatchOnPathVariableIsBadRequest() throws Exception {
        mockMvc.perform(get("/sample/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.msg").value(containsString("id")));
    }

    @Test
    @DisplayName("兜底仍是 500：别把真故障一并改成 4xx（那会让熔断永远打不开）")
    void fallbackStaysInternalServerError() throws Exception {
        mockMvc.perform(get("/sample/boom"))
                .andExpect(status().isInternalServerError());
    }

    /**
     * 只为把异常抛到 advice 上而存在的控制器：三个入口各对应上面一条用例
     *
     * <p>⚠ {@code /sample/boom} 是**字面路径**，优先级高于 {@code /sample/{id}} 模板——
     * 故它不会被类型转换拦下，而是真的走到方法里抛 {@code IllegalStateException}（兜底那一支）。</p>
     */
    @RestController
    @RequestMapping("/sample")
    static class SampleController {

        @GetMapping
        String sample(@RequestParam("customerId") Long customerId) {
            return "ok";
        }

        @GetMapping("/{id}")
        String byPathVariable(@PathVariable("id") Long id) {
            return "ok";
        }

        @GetMapping("/boom")
        String boom() {
            throw new IllegalStateException("模拟域内未预期的系统异常");
        }
    }
}
