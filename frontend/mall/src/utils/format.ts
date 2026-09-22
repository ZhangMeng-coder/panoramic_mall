/** 价格去掉多余小数：19.90 -> 19.9，8999.00 -> 8999 */
export function trimNum(n: number): string {
  return String(+n.toFixed(2))
}

/**
 * 价格拆三层字号：¥ 小、整数大、小数小。
 * 小数部分带前导点一起返回（如 '.9'、'.50'），供 .goods__price-dec 直接渲染。
 */
export function priceParts(price: number): { int: string; dec: string } {
  const [int, dec] = price.toFixed(2).split('.')
  return { int, dec: `.${dec}` }
}

/**
 * 后端 `LocalDateTime` 的 ISO 串 → 页面展示的「YYYY-MM-DD HH:mm」。
 *
 * ⚠ **不用 `new Date()` 解析**：后端存的是**本地墙钟时间**（`DATETIME`，不带时区），
 * 而 `new Date('2026-09-22T10:30:00')` 在浏览器里按**本机时区**解释——展示值会跟着
 * 访客的时区漂，同一笔订单在不同地方显示成不同时刻。这里只做字符串切分，显示的就是库里的那个时刻。
 * ⚠ `LocalDateTime` 的 ISO 串**秒为 0 时会省略秒**（`2026-09-22T10:30`），故切到分钟即止，
 * 两种长度都得到同一个结果。
 */
export function formatDateTime(value: string): string {
  return value.replace('T', ' ').slice(0, 16)
}

/** 手机号打码：13800138000 -> 138****8000（只用来展示顾客自己的账号，C 端惯例） */
export function maskPhone(phone: string): string {
  return phone.length === 11 ? `${phone.slice(0, 3)}****${phone.slice(7)}` : phone
}

/** 角标样式：促销类实心主色（无修饰符），服务类白底主色字，其余白底灰字 */
export function tagClass(name: string): string {
  if (name === '直降') return ''
  if (name === '包邮' || name === '次日达') return 'goods__tag--light'
  return 'goods__tag--neutral'
}
