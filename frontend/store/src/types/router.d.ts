import 'vue-router'

/**
 * 路由 meta 的约定（跨 router/index.ts 与 Layout / PlaceholderView 共用）。
 *
 * 不写这段的话 `route.meta` 是 `unknown` 索引，取值时只能在消费侧到处 `String(...)` / 断言 ——
 * 把契约声明在这里更诚实。
 */
declare module 'vue-router' {
  interface RouteMeta {
    /** 页面标题（Layout 顶部显示 / 占位页大标题） */
    title?: string
    /** 占位页说明文案（仅 /orders、/stock 这类占位入口用） */
    description?: string
  }
}
