<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { hotwords } from '../mock/hotwords'

const route = useRoute()
const router = useRouter()

/** 输入框内容。搜索页的关键词只在地址栏里（搜索页不渲染关键词标题），故初值取自 query */
const keyword = ref(typeof route.query.keyword === 'string' ? route.query.keyword : '')

// 搜索页的关键词只在地址栏里（T9 的 GoodsListView 不渲染它），输入框跟着地址栏走，
// 否则「搜了 A、框里却空着」；前进/后退也能跟上。
// ⚠ 同步方向只有路由 → 输入框，不把 keyword 反向写回 URL（会和用户输入打架）。
watch(
  () => route.query.keyword,
  (v) => {
    keyword.value = typeof v === 'string' ? v : ''
  }
)

/** 跳搜索结果页；空关键词也照跳（等价于「全部商品」） */
function doSearch() {
  const kw = keyword.value.trim()
  void router.push({ path: '/search', query: kw ? { keyword: kw } : {} })
}

/** 点热搜词：回填输入框后同样跳转 */
function pickHot(w: string) {
  keyword.value = w
  doSearch()
}
</script>

<template>
  <!-- ② 万能搜索长框（含下方热搜词行） -->
  <section class="search">
    <div class="container">
      <form class="search__box" autocomplete="off" @submit.prevent="doSearch">
        <svg class="search__icon" viewBox="0 0 24 24" aria-hidden="true">
          <circle cx="11" cy="11" r="6.5" fill="none" stroke="currentColor" stroke-width="1.8" />
          <path
            d="M16 16l4.5 4.5"
            fill="none"
            stroke="currentColor"
            stroke-width="1.8"
            stroke-linecap="round"
          />
        </svg>
        <label class="sr-only" for="searchInput">搜索商品</label>
        <input
          id="searchInput"
          v-model="keyword"
          class="search__input"
          type="search"
          placeholder="搜索商品、品牌、分类…"
        />
        <button class="search__btn" type="submit">搜索</button>
      </form>

      <div class="search__hot">
        <span class="search__hot-label">热搜</span>
        <ul class="search__hot-list">
          <li v-for="w in hotwords" :key="w">
            <a href="#" @click.prevent="pickHot(w)">{{ w }}</a>
          </li>
        </ul>
      </div>
    </div>
  </section>
</template>
