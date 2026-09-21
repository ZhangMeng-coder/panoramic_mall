import { ref } from 'vue'
import { cartApi } from '../api/cart'
import { getToken } from './auth'

/**
 * 购物车徽标状态（轻量模块，未引入 pinia —— 与 store/auth.ts 同构）。
 *
 * ⚠ **计数只有这一份**：顶栏徽标、详情页加购后的刷新、购物车页写操作后的刷新都用它。
 * 在组件里各写一个计数 ref 的话，顶栏的数字会与页面各说各话（加完购徽标不动）。
 *
 * 口径（契约「形状与行为口径」）：`GET /cart/count` = **购物车行数**，
 * 与购物车页的 `totalQuantity`（有效行件数之和）**不同**——徽标数「车里有几项」，
 * 页脚算「能买几件、多少钱」。故这里只存服务端给的那个数，不做任何换算。
 */
export const count = ref(0)

/** 就地置数（`refresh()` 内部用；负数没有意义，夹一下免得脏数据把徽标画坏） */
export function setCount(n: number): void {
  count.value = Math.max(0, Math.floor(n))
}

/**
 * 拉一次徽标数。
 *
 * ⚠ **未登录 / 无 token 时不发请求**，直接置 0：徽标不是页面主内容，未登录态的顶栏
 * 本来也不该有数字（登录 / 注册页、公开首页都挂着顶栏）。这也顺手避开了一个坑——
 * 公开首页上的**过期 token** 由路由守卫的 `me({silent401:true})` 清掉（`sessionExpired`
 * 无条件 `clearAuth()`），守卫跑完才渲染页面，所以到这里 `getToken()` 已经是空串，
 * 不会有「游客逛首页被一次徽标请求弹去登录页」这种事。
 *
 * ⚠ 失败**不抛**：徽标只是增强，拿不到就保持原值（拦截器已按 401 / 后端 msg 分流，
 * 这里再弹一遍就成了每翻一页弹一条）。调用方按「发了就不用管」处理。
 */
export async function refresh(): Promise<void> {
  if (!getToken()) {
    count.value = 0
    return
  }
  try {
    setCount(await cartApi.count())
  } catch {
    // 保持原值：拿不到行数不等于车里没东西
  }
}

/** 清 0。**登出时必须调**（与 `clearAuth()` 一起）：否则下一个人登录前，
 *  顶栏会挂着上一个人的车里的数字 */
export function clear(): void {
  count.value = 0
}
