<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { GoodsListItem } from '../types/catalog'
import { grad } from '../utils/gradient'

/**
 * 列表页的紧凑商品卡（7 列栅格 `<ul>` 的子项）。
 * ⚠ **刻意不复用首页 ⑥ 的 `GoodsCard`**：那张卡是 5 列栅格 + 角标 / 原价 / 销量位的
 * 视觉基准，类名 `.goods__*` 也按 5 列调过；共用类名会让两处互相串样式。
 * 本组件自成一个类名前缀 `cat-card__*`，样式写在 styles/catalog.css。
 */
const props = defineProps<{ goods: GoodsListItem }>()

/** 占位文字取商品名首字；名字为空时兜一个字，避免占位框里什么都没有 */
const label = computed(() => props.goods.name.trim().charAt(0) || '商')

/** 色相由商品 id 派生，同一个商品每次渲染的占位色一致 */
const hue = computed(() => props.goods.id % 360)

const imgFailed = ref(false)

/** 换了商品（图片 URL 变）就重试一次，避免上一次的失败态粘到新图上 */
watch(
  () => props.goods.mainImage,
  () => {
    imgFailed.value = false
  }
)

/** 空串即「无图」：模板里只判真值，不在模板里对 `string | null` 做窄化 */
const imgSrc = computed(() => (imgFailed.value ? '' : (props.goods.mainImage ?? '')))
</script>

<template>
  <li class="cat-card">
    <!-- 主图：有图用图，空 / 加载失败回退 CSS 渐变占位（不引任何外部图片） -->
    <div class="cat-card__thumb" :style="{ background: grad(hue, 60, 91, 82) }">
      <img
        v-if="imgSrc"
        class="cat-card__img"
        :src="imgSrc"
        :alt="goods.name"
        @error="imgFailed = true"
      />
      <span v-else class="cat-card__ph" :style="{ color: `hsl(${hue} 42% 32%)` }">
        {{ label }}
      </span>
    </div>

    <div class="cat-card__body">
      <div class="cat-card__name clamp-2">{{ goods.name }}</div>

      <div class="cat-card__price tnum">
        <template v-if="goods.minPrice !== null">
          <span class="cat-card__price-sym">¥</span>{{ goods.minPrice
          }}<span class="cat-card__price-suffix"> 起</span>
        </template>
        <span v-else class="cat-card__price-tbd">价格待定</span>
      </div>

      <div class="cat-card__store">{{ goods.storeName }}</div>
    </div>
  </li>
</template>
