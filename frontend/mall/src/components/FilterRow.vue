<script setup lang="ts">
import type { FacetItem } from '../types/catalog'

/**
 * 一行筛选维度（分类 / 品牌）：标题 + 可点 chips（名称 + 命中数），选中态高亮。
 * 该维度没有可选项时整行不渲染（不留空行）。
 * 样式在 styles/catalog.css —— 组件不引自己的 css（本项目样式统一在 main.ts 按序引入）。
 */
defineProps<{ title: string; items: FacetItem[]; selected: number[] }>()

const emit = defineEmits<{ toggle: [id: number] }>()
</script>

<template>
  <div v-if="items.length" class="filter-row">
    <span class="filter-row__title">{{ title }}</span>
    <div class="filter-row__chips">
      <button
        v-for="it in items"
        :key="it.id"
        class="filter-row__chip"
        :class="{ 'is-on': selected.includes(it.id) }"
        type="button"
        :aria-pressed="selected.includes(it.id)"
        @click="emit('toggle', it.id)"
      >
        {{ it.name }}
        <span class="filter-row__count tnum">{{ it.count }}</span>
      </button>
    </div>
  </div>
</template>
