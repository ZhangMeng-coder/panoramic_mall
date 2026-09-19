package com.panoramic.common.util;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;

/**
 * 富文本消毒：把**不可信来源的自由录入 HTML**（店主写的商品详情等）变成可安全渲染的 HTML。
 *
 * <p><b>为什么消毒点在端 BFF 的出口，而不在域、也不在前端</b>：域侧只负责原样存取（它知道
 * 「这列是富文本」，但不知道「这一份给谁看」），前端若各自去引清洗库，漏一个就是一处 XSS。
 * 端 BFF 的出口既分得清消费方、又只有一处，故由它洗净再下发 —— 各端共用本类这一份白名单，
 * 口径不会漂。见 docs/contracts/cross-cutting.md 第 21 条。</p>
 *
 * <p><b>白名单</b>用 {@link Safelist#relaxed()}：保留排版类标签（p / h1-h6 / ul / ol / table /
 * img / a 等，录入者贴图、列表、表格是常态），剥掉 script、{@code on*} 事件属性与 style 属性，
 * 并限制链接协议（{@code a[href]} 限 ftp-http-https-mailto，{@code img[src]} 限 http-https）。</p>
 *
 * <p><b>纯文本不是特例，是默认形态之一</b>：库里的存量富文本可能一个标签都没有（录入者就当纯
 * 文本框写、靠换行分段）。那种内容直接清洗会把换行折成空格、整段挤成一坨，故先判有没有标签：
 * 没有则转义 + 换行折 {@code <br>}，两类都汇到同一套白名单清洗 —— 出口形状统一是「安全的
 * HTML」，消费方一种渲染方式覆盖。</p>
 */
public final class HtmlSanitizer {

    /** 富文本白名单：只留排版类标签。策略只此一份、各端共用，避免各写一套后口径漂移 */
    private static final Safelist SAFELIST = Safelist.relaxed();

    /** 输出**不重排**：默认的 prettyPrint 会插缩进与换行，把录入者排好的正文改样 */
    private static final Document.OutputSettings OUTPUT_SETTINGS =
            new Document.OutputSettings().prettyPrint(false);

    private HtmlSanitizer() {
    }

    /**
     * 不可信富文本 → 可安全渲染的 HTML。
     *
     * @param raw 原始内容（可空）
     * @return 已清洗的 HTML；入参为 {@code null} 或空白时**原样返回**（消费方据此显示「未填写」）
     */
    public static String sanitizeRichText(String raw) {
        if (raw == null || raw.isBlank()) {
            return raw;
        }
        String source = containsMarkup(raw) ? raw : plainTextToHtml(raw);
        // baseUri 传空串（也是 Jsoup 单参 clean() 的默认值）：这里并没有「这段内容原本属于哪个
        // 页面」的信息。
        // ⚠ 别据此以为「相对地址一定被剥掉」，两件事都与直觉不同（已核 jsoup 1.23.2 源码）：
        //   ① 协议校验用的是 absUrl，而解析器会把**内容里自带的第一个 <base href>** 当作
        //      baseUri（HtmlTreeBuilder#maybeSetBaseUri）—— 于是相对地址能取到协议、通过校验；
        //   ② 有协议限制的属性（a[href] / img[src]）默认会被 Cleaner 改写成**绝对地址**
        //      （Safelist#shouldAbsUrl 在 preserveRelativeLinks=false 时恒真）。
        //   净效果：内容自带 <base> 时，相对地址会被解析成该 base 下的绝对外链。这不是新的
        //   能力（白名单本就允许绝对外链），故维持默认行为，只在此记一笔。
        //   没有 <base> 时相对地址取不到协议 → 整条属性被剥掉。
        return Jsoup.clean(source, "", SAFELIST, OUTPUT_SETTINGS);
    }

    /**
     * 判入参是不是「带标签的 HTML」：解析成片段后**有元素子节点**即算。
     *
     * <p>用解析器判而不是正则：正则判 {@code <p>} 既会漏（{@code <div class="x">}）也会误
     * （正文里写「a &lt; b」），而解析器对两者的处理正是我们要的 —— 后者会落成文本节点。</p>
     */
    private static boolean containsMarkup(String raw) {
        return !Jsoup.parseBodyFragment(raw).body().children().isEmpty();
    }

    /**
     * 纯文本 → HTML 片段：先转义，再把换行折成 {@code <br>}。
     *
     * <p>顺序不能反 —— 先插 {@code <br>} 再转义会把尖括号自己转掉。只转义 {@code & < >}：
     * 引号只在属性里有含义，而这里构造不出属性。</p>
     */
    private static String plainTextToHtml(String raw) {
        String escaped = raw.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        return escaped.replaceAll("\\r\\n|\\r|\\n", "<br>");
    }
}
