<script setup lang="ts">
import { computed, ref } from 'vue'
import { getToken, getUser } from '../store/auth'
import { maskPhone } from '../utils/format'

/* ⑤ 用户信息展示框 */
const loggedIn = computed(() => Boolean(getToken()))
const nickname = computed(() => getUser()?.nickname || '')
const phone = computed(() => getUser()?.phone || '')

/**
 * 头像取自顾客资料（`/auth/me` 的 `avatar`）。
 * ⚠ 原来是写死的 emoji（`🙂` / `👋`），现在有真实数据源了就接上——**不留一个假头像配真昵称**；
 * 无头像（或未登录）时**不塞任何替代图形**，只剩 `.usercard__avatar` 那层 CSS 渐变占位
 * （与资料页同一处理；不引外部图片 / 字体 / 图标库）。
 */
const avatar = computed(() => getUser()?.avatar || '')

/**
 * 头像可能是个死链（URL 由顾客自己填）：加载失败就退回渐变占位，别在首屏卡面上挂个碎图图标。
 * 记「失败的那个地址」而不是一个布尔量——地址一换（顾客改完资料）就自动再试。
 */
const avatarFailed = ref('')
const showAvatar = computed(() => Boolean(avatar.value) && avatar.value !== avatarFailed.value)

function onAvatarError(): void {
  avatarFailed.value = avatar.value
}

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
        <div class="usercard__avatar">
          <img
            v-if="showAvatar"
            class="usercard__avatar-img"
            :src="avatar"
            :alt="nickname || '头像'"
            @error="onAvatarError"
          />
        </div>

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
