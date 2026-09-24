import { onBeforeUnmount, ref, watch } from 'vue'
import type { Ref } from 'vue'

/* ============================================================================
   待支付倒计时 —— 订单详情页与订单列表页共用（两处各写一份 = 两个会漂移的口径）。

   ⚠ 倒计时是**提示**，不是闸门：能不能支付由域侧说了算（过了截止时刻，支付接口回
   400「该订单已过期（超过支付时限），请重新下单」）。故这里到 0 只是「该重新拉一次」的信号 ——
   订单是不是**已被自动取消**，只有服务端知道（取消它的是一条域内定时任务，最坏晚一个扫描周期）。

   ⚠ 时刻的读法：`expireTime` 是后端 `LocalDateTime` 的 ISO 串（**不带时区**，是墙钟时间），
   `new Date('2026-09-23T12:10:00')` 按**浏览器本机时区**解释它 —— 与 `formatDateTime` 刻意
   避开 `new Date()` 的理由是同一个。这里做差是**不得不**用 `new Date`（要算毫秒），
   故倒计时严格来说按**顾客本机时钟**走：本机时钟被改过 / 时区与服务器差一大截时，它会偏。
   偏差的后果**只是提示不准**——真到支付那一刻，域侧仍会如实拒掉。不要为了「更准」在本层
   按 `createTime + 固定分钟数` 再算一遍：那正是第二份会漂移的口径。

   ⚠ 每一跳都重新取 `Date.now()` 而**不是**把计数减 1：浏览器会给后台标签页的定时器降频，
   减一式的计数会越走越慢；取绝对时刻则每次自动校正回来。
   ========================================================================== */

/** 走秒的间隔（毫秒） */
const TICK_MS = 1000

/**
 * 「现在」的走秒值（毫秒）—— 只在 `active()` 为真时挂定时器，反之为假时摘掉。
 *
 * ⚠ `active` 传 getter 而不是布尔值：挂载那一刻的值决定不了后面（订单是异步拉回来的），
 * 由 `watch` 盯着它开 / 停。页面上没有待支付的单时就不该有一个定时器一直在跳。
 * ⚠ 返回的 ref 每次变化都会让依赖它的模板重算 —— 页面只需把 `remainingMs(..., now)` 写进
 * `computed`，不必自己管刷新。
 *
 * @param active     是否需要一个走动的时钟（传 getter）
 * @param intervalMs 走秒间隔，默认 1 秒
 */
export function useNowTick(active: () => boolean, intervalMs: number = TICK_MS): Ref<number> {
  const now = ref(Date.now())
  let timer: ReturnType<typeof setInterval> | null = null

  function stop(): void {
    if (timer !== null) {
      clearInterval(timer)
      timer = null
    }
  }

  function start(): void {
    if (timer !== null) return
    // 立刻对齐一次：挂上 / 从后台切回来的那一帧不能等满一个间隔才准
    now.value = Date.now()
    timer = setInterval(() => {
      now.value = Date.now()
    }, intervalMs)
  }

  const stopWatch = watch(active, (on) => (on ? start() : stop()), { immediate: true })
  onBeforeUnmount(() => {
    stop()
    stopWatch()
  })

  return now
}

/**
 * 距截止时刻的剩余毫秒。
 *
 * @param expireTime 域下发的支付截止时刻；**`null` 表示无超时**（本列上线前的老单）
 * @param nowMs      当前时刻（毫秒，取自 {@link useNowTick}）
 * @return 剩余毫秒；无截止时刻时返回 `null`；`<= 0` 表示已过期（到点即过期，与域侧同一句闭区间）
 */
export function remainingMs(expireTime: string | null, nowMs: number): number | null {
  if (!expireTime) return null
  return new Date(expireTime).getTime() - nowMs
}

/**
 * 剩余时长的展示文案：`mm:ss`（满 1 小时则 `h:mm:ss`）。
 *
 * ⚠ 入参是**已算好的剩余毫秒**，不是时刻——本函数不碰 `Date`，也就不会二次引入时区读法。
 *
 * @param ms 剩余毫秒（`<= 0` 时统一给 `00:00`）
 */
export function countdownText(ms: number): string {
  const total = Math.max(0, Math.floor(ms / 1000))
  const h = Math.floor(total / 3600)
  const m = Math.floor((total % 3600) / 60)
  const s = total % 60
  const mm = String(m).padStart(2, '0')
  const ss = String(s).padStart(2, '0')
  return h > 0 ? `${h}:${mm}:${ss}` : `${mm}:${ss}`
}
