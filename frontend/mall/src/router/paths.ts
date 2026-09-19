/**
 * 登录 / 注册页路径。
 *
 * ⚠ 单独成模块（而不是留在 `router/index.ts`）是为了**断环**：
 * 路由守卫要跳登录页，HTTP 拦截器 401 时也要跳登录页，而
 * `router/index.ts → api/auth.ts → api/request.ts → router/index.ts` 一旦成环，
 * 请求层拿到的 `AUTH_PATHS` 会在模块初始化期是未初始化的绑定。
 * 放这个叶子模块（不 import 任何项目模块）后，两侧都只依赖它。
 *
 * 导出给登录 / 注册页用于回跳校验：**回跳目标不能落在这些路径上**
 * （否则守卫会把自己弹给自己，成环）。
 */
export const AUTH_PATHS: readonly string[] = ['/login', '/register']
