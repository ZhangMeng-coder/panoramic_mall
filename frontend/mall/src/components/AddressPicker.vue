<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { addressApi } from '../api/address'
import type { AddressVO } from '../types/address'

/**
 * 选收货地址的**行内展开面板** —— 详情页「立即下单」与购物车「去结算」共用同一份。
 *
 * ⚠ **不引模态层**：本端从没有模态层（只有 toast），引入遮罩等于新增一个全局视觉基座
 * （同收货地址页的口径）。故它是**就地展开的一块面板**，由父级用 `v-if` 挂载 / 卸载。
 * ⚠ **不引 Element Plus**：选择项是原生 `radio` + `accent-color`（照购物车勾选框的手法），
 * 不做「卡片式单选」那种自造控件。
 *
 * 三条口径：
 * ① **挂载即拉一次地址**（父级 `v-if` 控制，每次展开都是新实例）——不需要「已加载就不重拉」
 *    那类缓存：地址可能在别处刚改过，展开时拉到的就是当下的。
 * ② **默认地址（服务端排在列表最前）预选中**：它本来就是「不指定时该用的那条」，
 *    预选只用省一次点击，**下单仍然要顾客按「确认下单」**——替他直接提交才是越界。
 * ③ **没有地址 / 读不到地址都不静默**：前者给去「收货地址」的入口，后者给「重试」——
 *    「点了没反应」与「地址丢了」都是最差的那种失败。
 */
defineProps<{
  /** 父级提交在途：期间整块禁用（防连点，见 `newRequestId` 的口径） */
  submitting: boolean
}>()

const emit = defineEmits<{
  /** 顾客按了「确认下单」且已选中一条地址 —— 父级据此构造 payload（含本次的 requestId） */
  confirm: [addressId: number]
  cancel: []
}>()

const addresses = ref<AddressVO[]>([])
const loading = ref(false)
const failed = ref(false)

/** 选中的地址 id；null = 一条都没有 / 还没选（此时「确认下单」不可点） */
const selectedId = ref<number | null>(null)

async function load(): Promise<void> {
  loading.value = true
  try {
    const list = await addressApi.list()
    addresses.value = list
    // 默认地址由服务端排在最前（契约口径），故预选第一条＝预选默认地址
    selectedId.value = list.length ? list[0].id : null
    failed.value = false
  } catch {
    // 拦截器已弹后端 msg；地址是下单的必经一步，读不到就整块降级 + 重试（不静默失败）
    addresses.value = []
    selectedId.value = null
    failed.value = true
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void load()
})

/** 省市区可为空（自由文本单列），空就不占位 */
function fullAddress(a: AddressVO): string {
  return [a.region, a.detailAddress].filter(Boolean).join(' ')
}

function confirm(): void {
  if (selectedId.value === null) return
  emit('confirm', selectedId.value)
}

function cancel(): void {
  emit('cancel')
}
</script>

<template>
  <section class="addr-pick">
    <h2 class="addr-pick__title">选择收货地址</h2>

    <p v-if="loading" class="addr-pick__state" role="status">正在加载收货地址…</p>

    <div v-else-if="failed" class="addr-pick__fallback">
      <p class="addr-pick__fallback-text">收货地址暂不可用，请稍后重试</p>
      <button class="addr-pick__retry" type="button" @click="load()">重试</button>
    </div>

    <!-- 一条地址都没有：给去处，不静默失败（点了「立即下单」却什么都不发生是最差的处理） -->
    <div v-else-if="!addresses.length" class="addr-pick__empty">
      <p class="addr-pick__empty-text">还没有收货地址，先添加一条再来下单</p>
      <router-link class="addr-pick__empty-link" to="/account/addresses">
        去添加收货地址
      </router-link>
    </div>

    <template v-else>
      <ul class="addr-pick__list">
        <li v-for="a in addresses" :key="a.id">
          <label class="addr-pick__opt" :class="{ 'is-on': selectedId === a.id }">
            <input
              v-model="selectedId"
              class="addr-pick__radio"
              type="radio"
              name="addr-pick"
              :value="a.id"
              :disabled="submitting"
            />
            <span class="addr-pick__body">
              <span class="addr-pick__line">
                <b class="addr-pick__name">{{ a.receiverName }}</b>
                <span class="addr-pick__phone tnum">{{ a.receiverPhone }}</span>
                <span v-if="a.isDefault === 1" class="addr-pick__tag">默认</span>
              </span>
              <span class="addr-pick__text">{{ fullAddress(a) }}</span>
            </span>
          </label>
        </li>
      </ul>

      <div class="addr-pick__ops">
        <button
          class="addr-pick__submit"
          type="button"
          :disabled="selectedId === null || submitting"
          @click="confirm"
        >
          {{ submitting ? '提交中…' : '确认下单' }}
        </button>
        <button class="addr-pick__cancel" type="button" :disabled="submitting" @click="cancel">
          取消
        </button>
      </div>
    </template>
  </section>
</template>
