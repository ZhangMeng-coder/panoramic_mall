<script setup lang="ts">
import { computed } from 'vue'
import { getToken, getUser } from '../store/auth'
import { maskPhone } from '../utils/format'

/* ⑤ 用户信息展示框 */
const loggedIn = computed(() => Boolean(getToken()))
const nickname = computed(() => getUser()?.nickname || '')
const phone = computed(() => getUser()?.phone || '')

/**
 * ⚠ 三项数字**恒为 0**：优惠券 / 积分 / 收藏的接口还没有（属 mall-bff 二期），
 * 这里不编造假数字——「不骗人」优先于「版式好看」。
 */
const perks = [
  { num: '0', label: '优惠券' },
  { num: '0', label: '积分' },
  { num: '0', label: '收藏' }
]
</script>

<template>
  <section class="userbox">
    <div class="container">
      <div class="usercard">
        <div class="usercard__avatar">{{ loggedIn ? '🙂' : '👋' }}</div>

        <div class="usercard__main">
          <div class="usercard__greet">
            <template v-if="loggedIn">下午好，<b>{{ nickname }}</b></template>
            <template v-else>你好，游客</template>
          </div>
          <div class="usercard__sub">
            <template v-if="loggedIn">手机号 {{ maskPhone(phone) }} · 会员权益数据待后续开放</template>
            <template v-else>登录后享会员价、查看订单与优惠券，新人还能领 188 元礼包</template>
          </div>
        </div>

        <router-link v-if="!loggedIn" class="usercard__cta" to="/login">去登录</router-link>
        <a v-else class="usercard__cta" href="#">查看我的订单</a>

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
