<script setup lang="ts">
import { goods } from '../mock/goods'
import GoodsCard from './GoodsCard.vue'
</script>

<template>
  <!-- ⑥ 热门商品列表 -->
  <section class="hot">
    <div class="container">
      <div class="section-head">
        <h2 class="section-head__title">热门商品</h2>
        <a class="section-head__more" href="#">
          查看全部
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <path
              d="M9 5l7 7-7 7"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
              stroke-linecap="round"
              stroke-linejoin="round"
            />
          </svg>
        </a>
      </div>

      <ul class="goods">
        <GoodsCard v-for="g in goods" :key="g.id" :goods="g" />
      </ul>
    </div>
  </section>
</template>
