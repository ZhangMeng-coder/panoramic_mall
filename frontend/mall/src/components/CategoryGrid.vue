<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { catalogApi } from '../api/catalog'
import type { CategoryNode } from '../types/catalog'
import { grad } from '../utils/gradient'

/**
 * ③ 全分类展示 —— 宫格读 mall-bff `/catalog/categories` 的真实分类树，只取顶级
 * （level === 1）：一行铺满，数量由后端决定（不再写死 10）。
 * 拉取失败（BFF 降级）或没有顶级分类时**整块不渲染**，不留空白骨架。
 * 形状与 `CatalogCard` 的「有图用图」口径一致：模板只判真值，不在模板里窄化 `string | null`。
 */
const router = useRouter()

/** 顶级分类（顺序用后端给的 sort 序，前端不再排一遍） */
const topLevel = ref<CategoryNode[]>([])

/** 图标加载失败的分类 id —— 失败后退回渐变圆占位 */
const iconFailed = ref(new Set<number>())

/** 宫格单元 */
interface CatCell {
  id: number
  name: string
  /** 图标地址；空串 = 无图 / 加载失败 → 走渐变圆 + 名称首字 */
  icon: string
  /** 占位色相由 id 派生，同一个分类每次渲染的色一致 */
  hue: number
  /** 占位文字取名称首字；名称空时兜一个字，免得上一个圆里什么都没有 */
  label: string
}

const cells = computed<CatCell[]>(() =>
  topLevel.value.map((c) => ({
    id: c.id,
    name: c.name,
    icon: iconFailed.value.has(c.id) ? '' : (c.icon ?? ''),
    hue: c.id % 360,
    label: c.name.trim().charAt(0) || '类'
  }))
)

/** 点宫格 → 该分类的商品页（`/category/:categoryId`，公开页） */
function openCategory(id: number) {
  void router.push(`/category/${id}`)
}

onMounted(async () => {
  try {
    const tree = await catalogApi.categories()
    topLevel.value = tree.filter((c) => c.level === 1)
  } catch {
    // 拦截器已弹后端提示；宫格是首页的入口区，拿不到就整块不渲染
    topLevel.value = []
  }
})
</script>

<template>
  <!-- ③ 全分类展示 -->
  <section v-if="cells.length" class="cats">
    <div class="container">
      <ul class="cats__grid" :style="{ '--cols': Math.min(cells.length, 10) }">
        <li v-for="c in cells" :key="c.id" class="cats__item" @click="openCategory(c.id)">
          <div class="cats__icon" :style="{ background: grad(c.hue, 78, 95, 88) }">
            <img
              v-if="c.icon"
              class="cats__img"
              :src="c.icon"
              :alt="c.name"
              @error="iconFailed.add(c.id)"
            />
            <template v-else>{{ c.label }}</template>
          </div>
          <div class="cats__name">{{ c.name }}</div>
        </li>
      </ul>
    </div>
  </section>
</template>

<style scoped>
/* 列数 = 顶级分类数（上限 10），保住「一行铺满」的基准又不写死 10。
   mall.css 的 .cats__grid 是 repeat(10, 1fr)，这里只覆盖列数：本规则多一个
   [data-v-*] 属性选择器，特异性更高。--cols 恒有值（v-if 保证 cells 非空）。
   ⚠ 写在组件里是因为本次不动 styles/mall.css。 */
.cats__grid {
  grid-template-columns: repeat(var(--cols), 1fr);
}

/* 分类图标多为方形透明底 logo：用 contain 完整显示，不裁切 */
.cats__img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}
</style>
