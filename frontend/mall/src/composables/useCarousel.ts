import { onBeforeUnmount, onMounted, ref } from 'vue'

/** 自动播放间隔（与样张一致） */
const AUTOPLAY_MS = 5000

/**
 * 轮播行为：自动播放 / 箭头 / 圆点 / 悬停暂停。
 * 只有一张时不自动播放、不出箭头（避免点了没反应）。
 * 点箭头或圆点后重置计时。
 */
export function useCarousel(total: number) {
  const index = ref(0)
  /** 是否可轮播（多于一张） */
  const canLoop = total > 1
  let timer: ReturnType<typeof setInterval> | null = null

  function go(i: number) {
    index.value = total ? ((i % total) + total) % total : 0
  }

  function stop() {
    if (timer) {
      clearInterval(timer)
      timer = null
    }
  }

  function start() {
    stop()
    if (!canLoop) return
    timer = setInterval(() => go(index.value + 1), AUTOPLAY_MS)
  }

  /** 手动切换（箭头 / 圆点）：切完重新开始计时 */
  function goAndRestart(i: number) {
    go(i)
    start()
  }

  onMounted(start)
  onBeforeUnmount(stop)

  return { index, canLoop, go, goAndRestart, start, stop }
}
