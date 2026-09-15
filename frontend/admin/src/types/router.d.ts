import 'vue-router'

/**
 * 路由 meta 的约定（跨 router/index.ts 与 Layout 共用）。
 *
 * 不写这段的话 `route.meta` 是 `unknown` 索引，Layout 里取 `meta.activeMenu` 拿不到 string，
 * 于是只能在消费侧到处 `String(...)` / 断言 —— 把契约声明在这里更诚实。
 */
declare module 'vue-router' {
  interface RouteMeta {
    /** 页面标题（Layout 顶部显示） */
    title?: string
    /** 侧栏高亮项：详情类子页面指回其列表路由（如 `/shop-goods/:id` → `/shop-goods`） */
    activeMenu?: string
  }
}
