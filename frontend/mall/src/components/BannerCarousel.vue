<script setup lang="ts">
import { banners } from '../mock/banners'
import { grad } from '../utils/gradient'
import { useCarousel } from '../composables/useCarousel'

/* ④ 大型滚动广告框（自动播放 + 箭头 + 圆点 + 悬停暂停） */
const { index, canLoop, goAndRestart, start, stop } = useCarousel(banners.length)
</script>

<template>
  <section class="banner">
    <div class="container">
      <div class="carousel" @mouseenter="stop" @mouseleave="start">
        <div class="carousel__viewport">
          <div
            class="carousel__track"
            :style="{ transform: `translateX(${-index * 100}%)` }"
          >
            <div
              v-for="b in banners"
              :key="b.id"
              class="carousel__slide"
              :style="{ background: grad(b.hue, 74, 52, 57, 120) }"
            >
              <span class="carousel__kicker">{{ b.kicker }}</span>
              <h2 class="carousel__title">{{ b.title }}</h2>
              <p class="carousel__sub">{{ b.sub }}</p>
              <a class="carousel__cta" href="#">{{ b.cta }}</a>
            </div>
          </div>
        </div>

        <button
          v-if="canLoop"
          class="carousel__arrow carousel__arrow--prev"
          type="button"
          aria-label="上一张"
          @click="goAndRestart(index - 1)"
        >
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <path
              d="M15 5l-7 7 7 7"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
              stroke-linecap="round"
              stroke-linejoin="round"
            />
          </svg>
        </button>
        <button
          v-if="canLoop"
          class="carousel__arrow carousel__arrow--next"
          type="button"
          aria-label="下一张"
          @click="goAndRestart(index + 1)"
        >
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
        </button>

        <div class="carousel__dots">
          <button
            v-for="(b, i) in banners"
            :key="b.id"
            class="carousel__dot"
            :class="{ 'is-active': i === index }"
            type="button"
            :aria-label="`第 ${i + 1} 张`"
            @click="goAndRestart(i)"
          ></button>
        </div>
      </div>
    </div>
  </section>
</template>
