<script setup lang="ts">
import { computed } from 'vue'

/**
 * 星级（1 ~ 5 星）。**两种用法共用一个组件**：只读展示（商品列表 / 详情 / 评价列表）
 * 与可点选择（订单详情的评价面板）——五颗星的形状只写一遍。
 *
 * ⚠ **星形按四舍五入取整**：商品评分是**平均值**（如 4.7），星级只是它的视觉近似；
 * 展示的**权威数字**永远是旁边那个小数（`.score` 原文下发），别让星形去承担精度。
 * 也不做「半颗星」——本端没有半星图形，凑出来的半星比四舍五入更容易读错。
 *
 * ⚠ 只读态用 `<span>`、可选态用 `<button>`：后者要能聚焦、能回车选中（键盘用户），
 * 且整组挂 `radiogroup` / `radio` 语义 —— 评分是一组**互斥取值**，不是五个独立开关。
 *
 * 样式在 `styles/catalog.css` 的 `.stars*`（与 `AddressPicker` 同一先例：
 * 跨页复用的组件样式落在主要使用页的样式文件里，不另开一个文件）。
 */
const props = withDefaults(
  defineProps<{
    /** 评分（1 ~ 5；展示态可以是平均值如 4.7） */
    score: number
    /** 可点选择（默认只读） */
    interactive?: boolean
    /** 选择在途（禁用整组） */
    disabled?: boolean
  }>(),
  { interactive: false, disabled: false }
)

const emit = defineEmits<{ pick: [score: number] }>()

/** 五颗星的取值，模板按它渲染（不从 0 起也不是为了好看：取值本身就是「几星」） */
const STARS = [1, 2, 3, 4, 5] as const

/**
 * 亮起的颗数：**四舍五入**（见文件头）。
 * ⚠ 夹在 0 ~ 5：下发的评分理论上在 1 ~ 5，但真出现越界值也不该渲染出六颗星或负数颗。
 */
const lit = computed(() => Math.min(5, Math.max(0, Math.round(props.score))))

/** 无障碍名：读屏用户听到的是「4.7 星」，而不是一串 ★ */
const label = computed(() => `评分 ${props.score} 星`)

function pick(star: number): void {
  if (!props.interactive || props.disabled) return
  emit('pick', star)
}
</script>

<template>
  <span v-if="!interactive" class="stars" :aria-label="label">
    <span
      v-for="s in STARS"
      :key="s"
      class="stars__item"
      :class="{ 'is-on': s <= lit }"
      aria-hidden="true"
      >★</span
    >
  </span>

  <span v-else class="stars stars--pick" role="radiogroup" aria-label="选择评分">
    <button
      v-for="s in STARS"
      :key="s"
      class="stars__item"
      :class="{ 'is-on': s <= lit }"
      type="button"
      role="radio"
      :aria-checked="s === lit"
      :aria-label="`${s} 星`"
      :disabled="disabled"
      @click="pick(s)"
    >
      ★
    </button>
  </span>
</template>
