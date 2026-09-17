<script setup lang="ts">
import { computed } from 'vue'

/**
 * 分页条（mall 不注册 Element Plus，自己写；样式在 styles/catalog.css）。
 * 槽位最多 7 个（页码 + 省略号）：首尾页常驻，当前页尽量居中。
 */
const props = defineProps<{ total: number; pageSize: number; page: number }>()

const emit = defineEmits<{ change: [page: number] }>()

/** 页码与省略号共用的槽位形状 */
type Slot = { kind: 'page'; page: number } | { kind: 'gap' }

const MAX_SLOTS = 7
const GAP: Slot = { kind: 'gap' }

function pages(from: number, to: number): Slot[] {
  const list: Slot[] = []
  for (let p = from; p <= to; p++) list.push({ kind: 'page', page: p })
  return list
}

const pageCount = computed(() =>
  Math.max(1, Math.ceil(props.total / Math.max(1, props.pageSize)))
)

/** 当前页夹在 [1, pageCount] 内：父级传了越界页时按有效页渲染，不出现空条 */
const current = computed(() => Math.min(Math.max(1, props.page), pageCount.value))

const slots = computed<Slot[]>(() => {
  const count = pageCount.value
  if (count <= MAX_SLOTS) return pages(1, count)
  const cur = current.value
  // 贴左端右侧补位、贴右端左侧补位、居中时两侧各一个省略号 —— 三种情况都正好 7 个槽位
  if (cur <= 3) return [...pages(1, 5), GAP, ...pages(count, count)]
  if (cur >= count - 2) return [...pages(1, 1), GAP, ...pages(count - 4, count)]
  return [...pages(1, 1), GAP, ...pages(cur - 1, cur + 1), GAP, ...pages(count, count)]
})

function go(page: number): void {
  if (page < 1 || page > pageCount.value || page === current.value) return
  emit('change', page)
}
</script>

<template>
  <nav v-if="pageCount > 1" class="pager" aria-label="分页">
    <button
      class="pager__btn"
      type="button"
      :disabled="current <= 1"
      @click="go(current - 1)"
    >
      上一页
    </button>

    <template v-for="(s, i) in slots" :key="i">
      <span v-if="s.kind === 'gap'" class="pager__gap">…</span>
      <button
        v-else
        class="pager__num"
        :class="{ 'is-on': s.page === current }"
        type="button"
        :aria-current="s.page === current ? 'page' : undefined"
        @click="go(s.page)"
      >
        {{ s.page }}
      </button>
    </template>

    <button
      class="pager__btn"
      type="button"
      :disabled="current >= pageCount"
      @click="go(current + 1)"
    >
      下一页
    </button>
  </nav>
</template>
