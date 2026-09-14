<script setup lang="ts">
import TopBar from '../components/TopBar.vue'
import SearchBar from '../components/SearchBar.vue'
import CategoryGrid from '../components/CategoryGrid.vue'
import BannerCarousel from '../components/BannerCarousel.vue'
import UserPanel from '../components/UserPanel.vue'
import GoodsGrid from '../components/GoodsGrid.vue'
import SiteFooter from '../components/SiteFooter.vue'
</script>

<template>
  <!-- 六区块顺序即风格基准：① 顶栏 → ② 搜索 → ③ 分类 → ④ 轮播 → ⑤ 用户框 → ⑥ 热门商品 -->
  <TopBar />
  <SearchBar />
  <CategoryGrid />
  <BannerCarousel />
  <UserPanel />
  <GoodsGrid />
  <SiteFooter />
</template>
