import { request } from './request'
import type { CartItemAddPayload, CartResult } from '../types/cart'

/**
 * 购物车接口（经网关 `/mall/**` 前缀转发到 mall-bff 8085）。
 * 路径与方法**照契约表 docs/contracts/mall-bff.md 写，不照后端代码写**。
 * 权限串一律为空：C 端不接 RBAC。
 *
 * ⚠ 三条**不体现在本文件**的服务端口径，写页面时要照它来（契约「形状与行为口径」）：
 * ① **徽标与页脚口径不同**：`count` 是**购物车行数**（轻口径，不做可见性判定），
 *    购物车页的 `totalQuantity` 是**有效行的件数之和**——不是同一件事，也不是 bug。
 *    `totalQuantity` / `selectedQuantity` / `selectedAmount` / `invalidCount` 都由服务端算好，
 *    前端不要自己乘加一遍（自己算就是第二份会漂移的金额口径）。
 * ② **写路径直透 trade-center**（BFF 不二次判定），且**单行命中 0 行是幂等 no-op**
 *    （行不存在 / 不属于本人都不报错）——故前端不必处理「双击删除」这类竞态。
 * ③ `selectAll` 是**整表操作**（把该顾客**所有**行的 selected 置为传入值，含失效行）；
 *    页面上的「全选」勾选态按有效行推导（见 CartView.vue 的口径注释）。
 */
export const cartApi = {
  /** 购物车列表：服务端一次编排（取行 + 批量详情 + 可见性判定 + 分组汇总），页面只渲染 */
  list(): Promise<CartResult> {
    return request.get<CartResult>('/mall/cart')
  },

  /** 购物车**行数**（顶栏徽标用；与购物车页的 totalQuantity 口径不同，见文件头 ①） */
  count(): Promise<number> {
    return request.get<number>('/mall/cart/count')
  },

  /**
   * 加购，出参是**新行的 id**（不是整份购物车，别照返回值拼列表）。
   * ⚠ 不校验库存（见类型 `CartItemAddPayload` 注释）；商品对 C 端不可见 → 400 中文提示、不落行。
   */
  addItem(payload: CartItemAddPayload): Promise<number> {
    return request.post<number>('/mall/cart/items', payload)
  },

  /** 改单行数量（`{quantity}` 是**目标值**不是增量；单行上限 999 在域侧，超限 400） */
  updateQuantity(id: number, quantity: number): Promise<void> {
    return request.put<void>(`/mall/cart/items/${id}`, { quantity })
  },

  /** 改单行选中态（`{selected}` 是目标值，不是切换） */
  updateSelected(id: number, selected: boolean): Promise<void> {
    return request.put<void>(`/mall/cart/items/${id}/selected`, { selected })
  },

  /** 全选 / 全不选：**整表**操作（含失效行），不要在前端拆成逐行改（那是 N 次请求） */
  selectAll(selected: boolean): Promise<void> {
    return request.put<void>('/mall/cart/selected', { selected })
  },

  /** 批量删除：**购物车没有单行删除端点**，删一行也走这里（`ids: [id]`） */
  removeItems(ids: number[]): Promise<void> {
    return request.post<void>('/mall/cart/items/remove', { ids })
  },

  /** 清空购物车（整表删） */
  clear(): Promise<void> {
    return request.delete<void>('/mall/cart')
  }
}
