<script setup lang="ts">
import { categories } from '../mock/categories'
import { grad } from '../utils/gradient'
</script>

<template>
  <!-- ③ 全分类展示 -->
  <section class="cats">
    <div class="container">
      <ul class="cats__grid">
        <li v-for="c in categories" :key="c.id" class="cats__item">
          <div class="cats__icon" :style="{ background: grad(c.hue, 78, 95, 88) }">
            {{ c.emoji }}
          </div>
          <div class="cats__name">{{ c.name }}</div>
        </li>
      </ul>
    </div>
  </section>
</template>
