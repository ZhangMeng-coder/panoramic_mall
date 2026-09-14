<script setup lang="ts">
import { ref } from 'vue'
import { hotwords } from '../mock/hotwords'

const keyword = ref('')
const hint = ref('')

/** 未接入搜索：只出提示，不跳转 */
function doSearch() {
  const kw = keyword.value.trim()
  hint.value = kw
    ? `（演示：搜索未接入，关键词「${kw}」）`
    : '（演示：输入关键词后回车，这里只是提示，不会跳转）'
}

/** 点热搜词：回填输入框并出提示 */
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

      <p class="search__hint" role="status">{{ hint }}</p>

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
