<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { addressApi } from '../api/address'
import type { AddressVO } from '../types/address'

/**
 * 选收货地址 —— **模态里装的内容**（盒子由 `ModalShell` 提供，样式见 styles/modal.css），
 * 三处共用：详情页「立即下单」与购物车「去结算」的选地址、订单详情「修改地址」。
 *
 * ⚠ **它自己不是弹窗**：模态层的实现（遮罩 / 层级 / Esc / 遮罩点击 / 滚动锁）全在 `ModalShell`，
 * 这里只渲染「标题 + 列表 + 两个按钮」——故**不自己写遮罩**，也不假设自己被怎么挂载。
 * ⚠ **不引 Element Plus**：选择项是原生 `radio` + `accent-color`（照购物车勾选框的手法），
 * 不做「卡片式单选」那种自造控件。
 *
 * 三条口径：
 * ① **挂载即拉一次地址**（父级 `v-if` 控制，每次打开都是新实例）——不需要「已加载就不重拉」
 *    那类缓存：地址可能在别处刚改过，打开时拉到的就是当下的。
 * ② **预选列表第一条**：有默认地址时服务端把它排在最前（契约口径），第一条即那条默认；
 *    没有默认时（⚠ **这是常态**，见 `useAddressGate`：有默认就静默下单了，根本不进弹窗）
 *    预选只是省一次点击。⚠ 预选**不等于**提交：下单 / 改地址都要顾客自己按下面那个按钮。
 * ③ **没有地址 / 读不到地址都不静默**：前者给去「收货地址」的入口，后者给「重试」——
 *    「点了没反应」与「地址丢了」都是最差的那种失败。
 *    ⚠ 「没有地址」这条正常走不到（下单前的分支已把顾客送去添加地址），但它**真会发生**：
 *    地址可能在状态读完之后、本列表拉回来之前被删掉。
 */
defineProps<{
  /** 父级提交在途：期间整块禁用（防连点，见 `newRequestId` 的口径） */
  submitting: boolean
  /** 主按钮文案（⚠ 三处动作不同：下单 / 结算 vs 改订单收货地址） */
  confirmText: string
}>()

const emit = defineEmits<{
  /** 顾客按了主按钮且已选中一条地址 —— 父级据此提交（下单 / 结算 / 改订单地址三处各自的动作） */
  confirm: [addressId: number]
  cancel: []
}>()

const addresses = ref<AddressVO[]>([])
const loading = ref(false)
const failed = ref(false)

/** 选中的地址 id；null = 一条都没有 / 还没选（此时主按钮不可点） */
const selectedId = ref<number | null>(null)

async function load(): Promise<void> {
  loading.value = true
  try {
    const list = await addressApi.list()
    addresses.value = list
    // 预选第一条（见文件头 ②）：有默认时它就是默认那条（服务端把它排在最前），没有时只是省一次点击
    selectedId.value = list.length ? list[0].id : null
    failed.value = false
  } catch {
    // 拦截器已弹后端 msg；地址是这一步的必经数据，读不到就整块降级 + 重试（不静默失败）
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

    <!-- 一条地址都没有：给去处，不静默失败（点了按钮却什么都不发生是最差的处理）。
         ⚠ 文案不带「下单」二字：三处入口里有一处是「改订单地址」 -->
    <div v-else-if="!addresses.length" class="addr-pick__empty">
      <p class="addr-pick__empty-text">还没有收货地址，先添加一条</p>
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
          {{ submitting ? '提交中…' : confirmText }}
        </button>
        <button class="addr-pick__cancel" type="button" :disabled="submitting" @click="cancel">
          取消
        </button>
      </div>
    </template>
  </section>
</template>
