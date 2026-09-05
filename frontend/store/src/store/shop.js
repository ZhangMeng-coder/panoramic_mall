import { ref, computed } from 'vue'
import { shopApi } from '../api/shop'

/**
 * 店主「我的店铺」响应式状态：
 * - shop：mine() 返回的店铺对象（未创建为 null）；
 * - shopStatus：审核状态（0草稿/1待审核/2已通过/3已驳回），未创建视为草稿(0)。
 * Layout 侧栏「开店后业务入口」显隐、店铺信息页状态渲染均读取本状态；
 * 登录/刷新/提交审核后调用 fetchMyShop() 刷新。
 */
export const shop = ref(null)

export const shopStatus = computed(() => (shop.value ? shop.value.status : 0))

export const isApproved = computed(() => shopStatus.value === 2)

/** 拉取当前店主店铺并刷新本地状态（无店铺置 null） */
export async function fetchMyShop() {
  try {
    shop.value = await shopApi.mine()
  } catch {
    shop.value = null
  }
}

export function clearShop() {
  shop.value = null
}
