<script setup lang="ts">
import { computed } from 'vue'
import type { Goods } from '../types/mall'
import { grad } from '../utils/gradient'
import { priceParts, tagClass, trimNum } from '../utils/format'

const props = defineProps<{ goods: Goods }>()

const price = computed(() => priceParts(props.goods.price))
</script>

<template>
  <li class="goods__item">
    <!-- 商品图：CSS 渐变占位 + 中间一个占位文字 -->
    <div class="goods__thumb" :style="{ background: grad(goods.hue, 60, 91, 82) }">
      <div class="goods__thumb-label" :style="{ color: `hsl(${goods.hue} 42% 32%)` }">
        {{ goods.imgLabel }}
      </div>
      <div v-if="goods.tags.length" class="goods__tags">
        <span v-for="t in goods.tags" :key="t" :class="['goods__tag', tagClass(t)]">{{ t }}</span>
      </div>
    </div>

    <!-- 正文：名称（两行截断）+ 价格 / 销量 -->
    <div class="goods__body">
      <div class="goods__name clamp-2">{{ goods.name }}</div>

      <div class="goods__bottom">
        <div class="goods__prices">
          <span class="goods__price tnum">
            <span class="goods__price-sym">¥</span>
            <span class="goods__price-int">{{ price.int }}</span>
            <span class="goods__price-dec">{{ price.dec }}</span>
          </span>
          <span v-if="goods.originPrice !== null" class="goods__origin tnum">
            ¥{{ trimNum(goods.originPrice) }}
          </span>
        </div>
        <span class="goods__sales tnum">
          {{ goods.salesText === '0' ? '暂无成交' : goods.salesText + '人付款' }}
        </span>
      </div>
    </div>
  </li>
</template>
