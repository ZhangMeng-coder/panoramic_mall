<script setup lang="ts">
import { computed } from 'vue'
import { loggedIn } from '../mock/session'

/* ⑤ 用户信息展示框 */
const perks = computed(() =>
  loggedIn
    ? [
        { num: '12', label: '优惠券' },
        { num: '2860', label: '积分' },
        { num: '34', label: '收藏' }
      ]
    : [
        { num: '0', label: '优惠券' },
        { num: '0', label: '积分' },
        { num: '0', label: '收藏' }
      ]
)
</script>

<template>
  <section class="userbox">
    <div class="container">
      <div class="usercard">
        <div class="usercard__avatar">{{ loggedIn ? '🙂' : '👋' }}</div>

        <div class="usercard__main">
          <div class="usercard__greet">
            <template v-if="loggedIn">下午好，<b>张小明</b></template>
            <template v-else>你好，游客</template>
          </div>
          <div class="usercard__sub">
            {{
              loggedIn
                ? '黄金会员 · 本月已省 ¥128，还有 2 张券即将过期'
                : '登录后享会员价、查看订单与优惠券，新人还能领 188 元礼包'
            }}
          </div>
        </div>

        <a class="usercard__cta" href="#">{{ loggedIn ? '查看我的订单' : '去登录' }}</a>

        <div class="usercard__perks">
          <div v-for="p in perks" :key="p.label" class="perk">
            <div class="perk__num" :class="{ 'perk__num--muted': !loggedIn }">{{ p.num }}</div>
            <div class="perk__label">{{ p.label }}</div>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>
