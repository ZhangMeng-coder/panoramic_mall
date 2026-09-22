/**
 * 请求级幂等键（下单 payload 的 `requestId`）的**唯一生成点**。
 *
 * 为什么要有它：`POST /mall/orders` 的 `requestId` 是**必填**的页面契约字段
 * （契约 docs/contracts/mall-bff.md「重复提交」），域侧按 `(customer_id, request_id)`
 * 做第一级幂等——命中即把**首次那批订单原样返回**，不重建、不二次扣库存。
 * 故它的口径是「**每次用户确认下单生成一个**」：同一个键重复提交不会出第二单，
 * 两个不同的键就是两次真实的下单意图。调用点见 GoodsDetailView / CartView 的提交处。
 *
 * ⚠ **不引 id 生成依赖**（本端连 element-plus 都只是备用能力）：`crypto.randomUUID` 是
 * 浏览器原生、无需依赖，但它**只在安全上下文**（https / localhost）可用——本端将来若被以
 * 普通 http 托管在非 localhost 域名上，直接调它会 `TypeError: undefined is not a function`
 * 把下单**整个打死**（不是降级，是点了没反应），故回落分支必须留着。
 *
 * ⚠ **为什么这里的弱随机数可以接受**（别把它「升级」成安全需求）：幂等键的作用域是
 * `(customer_id, request_id)`（trade-center 的 `OrderRepository#occupy`），别人的 id 在
 * **我这个 customer_id 下根本不匹配**。它只需要**唯一性**（不与自己近期的提交撞），
 * 不需要**保密性**——它不是安全令牌，不参与鉴权，也拿不到任何别人的数据。
 */
export function newRequestId(): string {
  const uuid = globalThis.crypto?.randomUUID?.()
  if (uuid) return uuid
  // 回落：36 进制时间戳 + 8 位随机串。同一毫秒内两次调用靠随机部分区分
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`
}
