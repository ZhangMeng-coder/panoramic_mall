import { computed, ref } from 'vue'
import type { ComputedRef, Ref } from 'vue'
import { useRouter } from 'vue-router'
import { addressApi } from '../api/address'
import { showToast } from './useToast'

/** 「还没有收货地址」时把顾客送去的去处（全站唯一落点：收货地址页） */
const ADDRESSES_PATH = '/account/addresses'

/**
 * 三分支的判定结果。
 * ⚠ 只有「直接用默认地址」那条带 id——另两条**没有**地址可用，不该给调用方一个可能为 null 的 id 去判。
 */
type Branch = { kind: 'none' } | { kind: 'choose' } | { kind: 'use'; addressId: number }

/** 两个下单入口（详情页「立即下单」/ 购物车「去结算」）共用的那套状态与动作 */
export interface AddressGate {
  /** 在途：读状态 + 静默下单都算（入口按钮据此禁用并换进行中文案） */
  busy: ComputedRef<boolean>
  /** 选地址弹窗开着没有（`choose` 那条支路）——父级据此 `v-if` 挂 `ModalShell` */
  picking: Ref<boolean>
  /** 弹窗里的提交在途（传给 `AddressPicker` 与 `ModalShell` 的 `closable`） */
  submitting: Ref<boolean>
  /** 点「立即下单」/「去结算」：读状态 → 判分支 → 该下单就下单 */
  start(): Promise<void>
  /** 弹窗里按了主按钮（选中的地址 id） */
  confirm(addressId: number): Promise<void>
  /** 弹窗请求关闭（取消 / Esc / 点遮罩，三处都走这里） */
  close(): void
}

/**
 * 下单前的**地址分支判定** —— 两个下单入口共用这一处实现（R1）。
 *
 * 三条支路（照契约「地址状态读」那行）：
 * ① **完全没有地址** → 提示一句 + 送去收货地址页，**不提交订单**；
 * ② **有地址但没有默认** → 开选地址弹窗，顾客选一条再按主按钮；
 * ③ **有地址且有默认** → **静默用默认地址下单**，不问、不弹（它本来就是「不指定时该用的那条」）。
 *
 * ⚠ **判定只此一处**：两个入口（详情页 / 购物车）的差别只有 `submit`（建单参数与下单后的去向），
 * 分支逻辑不各写一遍——两处各写一遍必然在某次改动后分家。
 * ⚠ 页面上**没有**「先看看有没有默认地址再决定按钮长什么样」这种预判：分支在**点击那一刻**判，
 * 因为地址状态随时会变（缓存 TTL 30 分钟 + 四个写路径失效，别处刚改过页面并不知道）。
 *
 * @param options.submit 用某条地址提交订单；成功即 resolve、失败抛错（提示由拦截器统一弹）
 */
export function useAddressGate(options: {
  submit: (addressId: number) => Promise<void>
}): AddressGate {
  const router = useRouter()

  const checking = ref(false)
  const picking = ref(false)
  const submitting = ref(false)

  /** 两个在途标记合起来就是「入口按钮该禁用吗」（读状态期间也不该再点一下） */
  const busy = computed(() => checking.value || submitting.value)

  /**
   * 读一次状态并判分支 —— **唯一一处判定**。
   * ⚠ 读失败**不吞**（抛出交给调用方）：吞掉就没法区分「没有地址」与「读不到地址」，
   * 而这两者的处置完全相反（一个去添加、一个就该停住）。
   */
  async function resolve(): Promise<Branch> {
    const status = await addressApi.status()
    if (!status.hasAddress) return { kind: 'none' }
    if (status.defaultAddressId === null) return { kind: 'choose' }
    return { kind: 'use', addressId: status.defaultAddressId }
  }

  /** 提交一次；返回是否成功（失败时拦截器已弹后端 msg，这里只回报结果） */
  async function run(addressId: number): Promise<boolean> {
    submitting.value = true
    try {
      await options.submit(addressId)
      return true
    } catch {
      return false
    } finally {
      submitting.value = false
    }
  }

  /**
   * 用默认地址提交**失败之后**的兜底。
   *
   * ⚠ 服务端给的 `defaultAddressId` 出自**缓存**（TTL + 四个写路径失效），陈旧的 id 会被域侧
   * 按 404「地址不存在」拒掉——契约明确要求页面按这条失败**回退到重选**。
   * ⚠ 但错误对象只带 msg、不带业务码（见 `api/request.ts`），故**不按 404 判别**，而是重读一次状态：
   * 地址真被删了，重读必然拿到「有地址但无默认」→ 开弹窗；其余失败（库存不足 / 商品已下架 /
   * 下游故障）状态不变，弹窗**不开**——重选地址也解决不了库存问题，白开一次只是添乱。
   */
  async function fallback(): Promise<void> {
    try {
      const again = await resolve()
      if (again.kind === 'choose') picking.value = true
      else if (again.kind === 'none') await router.push(ADDRESSES_PATH)
    } catch {
      // 重读仍失败：首次失败已给过提示，这里不再叠一条（两句话说的是同一件事）
    }
  }

  /** 点入口按钮：三分支见文件头 */
  async function start(): Promise<void> {
    if (busy.value) return
    checking.value = true
    try {
      const branch = await resolve()
      if (branch.kind === 'none') {
        showToast('还没有收货地址，先添加一条再来下单', 'info')
        await router.push(ADDRESSES_PATH)
        return
      }
      if (branch.kind === 'choose') {
        picking.value = true
        return
      }
      if (!(await run(branch.addressId))) {
        await fallback()
      }
    } catch {
      // 读状态失败：拦截器已弹 msg。⚠ **不猜分支**：猜「没有默认」会把顾客塞进一个可能空的面板，
      // 猜「有默认」会拿一个不知道存不存在的 id 去下单——两条都比「就此停住」更坏
    } finally {
      checking.value = false
    }
  }

  /** 弹窗里按了主按钮：**成功才关**弹窗（失败留着让顾客换一条，同两个入口原有的口径） */
  async function confirm(addressId: number): Promise<void> {
    if (submitting.value) return
    if (await run(addressId)) picking.value = false
  }

  /** 关弹窗（取消 / Esc / 点遮罩）—— 提交在途不关，与 `ModalShell` 的 `closable` 同一口径 */
  function close(): void {
    if (submitting.value) return
    picking.value = false
  }

  return { busy, picking, submitting, start, confirm, close }
}
