<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { addressApi } from '../../api/address'
import { showToast } from '../../composables/useToast'
import type { AddressPayload, AddressVO } from '../../types/address'

/**
 * 收货地址页（`/account/addresses`，**需登录态**）—— 左右分栏由父级 `AccountLayout` 提供，
 * 本页只写右栏内容。三件事：列表、新增 / 编辑（**共用一份表单**）、删除与设默认。
 *
 * 五条口径（都不是随手写的）：
 * ① **顺序与默认位一律以服务端为准**：列表默认地址排最前（服务端返回时就排好），
 *    每个写操作之后**重拉列表**而不是在本地挪行 / 拼条目——否则「首条自动默认」这条
 *    服务端行为就得在前端再实现一遍。
 * ② **删除用行内两步确认**，不弹窗、不用 `window.confirm`：本端从没有模态层（只有 toast），
 *    引入遮罩等于新增一个全局视觉基座；原生 confirm 又与 C 端观感不符且阻塞事件循环。
 * ③ **删掉默认地址后只提示、不递补**（spec D7）：删掉的若是默认地址且还有别的地址，
 *    提示用户去设新的默认，**不在前端顺手再调一次 `setDefault`**。
 * ④ **客户端校验不严于域侧**：只做「非空 + 长度上限」（上限由各输入的 `maxlength` 兜住）。
 *    ⚠ 手机号**不套** `useSmsCode` 的 `PHONE_RE`（`^1[3-9]\d{9}$`）：收货电话可能是固话 / 分机，
 *    域侧只要求非空 ≤20 字符，加正则会拒掉后端本来接受的合法输入。
 * ⑤ **列表加载失败要有页面级降级**（整块「暂不可用」+ 重试），与「一条都没有」的空态
 *    **刻意分两块渲染**：把「读不到」渲染成「没有」，用户会以为自己的地址丢了。
 */

/** 页面私有：新增 / 编辑共用的表单（`region` 可空，表单里用空串表示未填） */
interface AddressForm {
  receiverName: string
  receiverPhone: string
  region: string
  detailAddress: string
}

/** 空表单：`openCreate()` 与提交成功后的收尾共用同一份，别在两处各写一遍字段 */
const EMPTY_FORM: AddressForm = {
  receiverName: '',
  receiverPhone: '',
  region: '',
  detailAddress: ''
}

const addresses = ref<AddressVO[]>([])
const loading = ref(false)
const loadFailed = ref(false)

/** 正在确认删除的那一行（同一时刻至多一行：点别行的「删除」或「取消」即恢复） */
const confirmingId = ref<number | null>(null)

/**
 * 行内写操作（删除 / 设默认）进行中的行 id **集合**：防连点，按钮就地显进行中文案。
 *
 * ⚠ 用集合而不是**单一 id**：两行各有一个请求在途是正常场景（A 行「设为默认」还没回来，
 * B 行已点「确认删除」）。单一 id 会被后发者覆盖，先发者返回时又把后发者的标记清掉——
 * B 行的按钮提前恢复可点，用户再点一次就是**第二个 DELETE**（后端必然 404，页面上
 * 「地址已删除」的成功 toast 会紧跟一条「地址不存在」）。集合下每行各记各的，
 * 谁的请求回来只清谁——也**不是**「整页锁死到最后一个请求结束」，别的行照常可操作。
 */
const busyIds = ref<number[]>([])

function busyStart(id: number): void {
  if (!busyIds.value.includes(id)) busyIds.value = [...busyIds.value, id]
}

function busyEnd(id: number): void {
  busyIds.value = busyIds.value.filter((v) => v !== id)
}

/** 表单是否展开；`editingId` 为 null 时是「新增」，否则是「编辑这一条」 */
const formOpen = ref(false)
const editingId = ref<number | null>(null)
const form = reactive<AddressForm>({ ...EMPTY_FORM })
const saving = ref(false)

/**
 * 拉列表。`hard = true`（首次进入 / 降级重试）时显示加载态；增删改之后的刷新走
 * `hard = false`：列表就地换新，不闪一层「正在加载」。
 *
 * ⚠ **普通调用**，不传 `silent401`（那是路由守卫刷新重建用户态专用的）：
 * 会话真没了就走 401 出口（清态 + 提示 + 跳登录页），而不是静默留在页面上。
 */
async function load(hard = true): Promise<void> {
  if (hard) loading.value = true
  try {
    addresses.value = await addressApi.list()
    loadFailed.value = false
  } catch {
    // 拦截器已弹后端 msg（下游故障时是「地址服务暂不可用，请稍后重试」）
    loadFailed.value = true
  } finally {
    if (hard) loading.value = false
  }
}

onMounted(() => {
  void load()
})

/** 展示用完整地址：`region` 可空，空的那段不留出多余空格 */
function fullAddress(a: AddressVO): string {
  return [a.region ?? '', a.detailAddress].filter((part) => part !== '').join(' ')
}

/* ---- 新增 / 编辑：同一份表单 ---- */

function openCreate(): void {
  confirmingId.value = null
  editingId.value = null
  Object.assign(form, EMPTY_FORM)
  formOpen.value = true
}

function openEdit(a: AddressVO): void {
  confirmingId.value = null
  editingId.value = a.id
  form.receiverName = a.receiverName
  form.receiverPhone = a.receiverPhone
  // region 可空：null 在表单里就是空串（提交时再还原成 null）
  form.region = a.region ?? ''
  form.detailAddress = a.detailAddress
  formOpen.value = true
}

function cancelForm(): void {
  formOpen.value = false
  editingId.value = null
}

/** 只判「非空」：长度上限由 `maxlength` 兜住，其余规则由后端判并回 400（见文件头 ④） */
function validate(): boolean {
  if (!form.receiverName.trim()) {
    showToast('请填写收件人姓名')
    return false
  }
  if (!form.receiverPhone.trim()) {
    showToast('请填写收件人手机号')
    return false
  }
  if (!form.detailAddress.trim()) {
    showToast('请填写详细地址')
    return false
  }
  return true
}

function payload(): AddressPayload {
  return {
    receiverName: form.receiverName.trim(),
    receiverPhone: form.receiverPhone.trim(),
    // 可空列：空串按「未填」送 null，别把空串当值写进库
    region: form.region.trim() || null,
    detailAddress: form.detailAddress.trim()
  }
}

async function submit(): Promise<void> {
  if (!validate()) return
  // 先取值再判：`editingId` 是可变 ref，收进局部常量后类型收窄才稳
  const id = editingId.value

  saving.value = true
  try {
    if (id === null) {
      await addressApi.create(payload())
      showToast('地址已新增', 'success')
    } else {
      await addressApi.update(id, payload())
      showToast('地址已更新', 'success')
    }
    cancelForm()
    // 重拉而不是本地拼：新增首条会被服务端自动置默认、顺序也随之变
    await load(false)
  } catch {
    // 拦截器已弹后端 msg（姓名 / 电话 / 详细地址的非空与超长校验都在服务端，前端不重复实现）
  } finally {
    saving.value = false
  }
}

/* ---- 删除（行内两步确认）与设默认 ---- */

function askRemove(a: AddressVO): void {
  confirmingId.value = a.id
}

function cancelRemove(): void {
  confirmingId.value = null
}

/**
 * 删除。⚠ 默认地址被删后**服务端不自动递补**（spec D7）：删掉的若是默认地址、且还有别的地址，
 * 只提示用户去设新的默认——**不在前端顺手再调一次 `setDefault`**（「默认位唯一」的维护在服务端，
 * 前端替它挑一条等于把一条业务规则复制到页面里）。删掉最后一条时无事可设，也不必提示。
 *
 * ⚠ **失败路径同样要「复位 + 重拉」**（不只是成功路径）：该地址已在别处被删时点「确认删除」会吃
 * 404，那正是「服务端状态与页面不一致」的证据——不重拉的话那一行会停在「确认删除？」上、
 * 页面上还留着一条服务端早已不存在的记录，用户再点仍是 404。catch 里**不自己弹提示**（拦截器已弹）。
 */
async function remove(a: AddressVO): Promise<void> {
  const wasDefault = a.isDefault === 1
  busyStart(a.id)
  try {
    await addressApi.remove(a.id)
    // 正在编辑的正是被删的这条：收起表单，免得再点「保存」打到一条已不存在的地址
    if (editingId.value === a.id) cancelForm()
    await load(false)
    if (wasDefault && !loadFailed.value && addresses.value.length > 0) {
      showToast('默认地址已删除，请重新设置一条默认地址', 'info')
    } else {
      showToast('地址已删除', 'success')
    }
  } catch {
    // 拦截器已弹后端 msg（地址不存在 / 不属于本人 → 404「地址不存在」）
    // 先就地复位（不等重拉结束，用户立刻能再操作），再重拉把页面拉回与服务端一致
    confirmingId.value = null
    await load(false)
  } finally {
    // 成败都复位本行确认态；`busyEnd` 只清**本行**的标记
    confirmingId.value = null
    busyEnd(a.id)
  }
}

async function makeDefault(a: AddressVO): Promise<void> {
  busyStart(a.id)
  try {
    await addressApi.setDefault(a.id)
    showToast('已设为默认地址', 'success')
    // 同样重拉：默认排最前的顺序由服务端给，前端不自己挪行
    await load(false)
  } catch {
    // 拦截器已弹后端 msg
  } finally {
    busyEnd(a.id)
  }
}
</script>

<template>
  <h1 class="acct__title">收货地址</h1>
  <p class="acct__desc">默认地址会排在列表最前，新增的第一条地址会自动成为默认地址</p>

  <!-- 新增 / 编辑**共用这一份表单**：靠 editingId 区分（null = 新增），字段与校验只有一处 -->
  <section v-if="formOpen" class="acct__section">
    <h2 class="acct__section-title">{{ editingId === null ? '新增收货地址' : '编辑收货地址' }}</h2>

    <form class="acct__form" autocomplete="off" @submit.prevent="submit">
      <div class="field">
        <label class="field__label" for="adName">收件人姓名</label>
        <div class="field__box">
          <input
            id="adName"
            v-model="form.receiverName"
            class="field__input"
            type="text"
            maxlength="50"
            placeholder="不超过 50 个字符"
          />
        </div>
      </div>

      <div class="field">
        <label class="field__label" for="adPhone">收件人手机号</label>
        <div class="field__box">
          <!-- ⚠ 不限手机号形态：固话 / 分机也是合法收货电话，域侧只要求非空 ≤20 字符 -->
          <input
            id="adPhone"
            v-model="form.receiverPhone"
            class="field__input"
            type="tel"
            maxlength="20"
            placeholder="手机号或固话，如 0571-88888888"
          />
        </div>
      </div>

      <div class="field">
        <label class="field__label" for="adRegion">所在地区</label>
        <div class="field__box">
          <input
            id="adRegion"
            v-model="form.region"
            class="field__input"
            type="text"
            maxlength="100"
            placeholder="省市区，如：广东省 深圳市 南山区"
          />
        </div>
        <p class="field__hint">选填，不填也能保存</p>
      </div>

      <div class="field">
        <label class="field__label" for="adDetail">详细地址</label>
        <div class="field__box">
          <input
            id="adDetail"
            v-model="form.detailAddress"
            class="field__input"
            type="text"
            maxlength="255"
            placeholder="街道、门牌号、楼层、房间号等"
          />
        </div>
      </div>

      <div class="acct__form-ops">
        <button class="btn-primary" type="submit" :disabled="saving">
          {{ saving ? '保存中…' : '保存地址' }}
        </button>
        <button class="acct__link" type="button" @click="cancelForm">取消</button>
      </div>
    </form>
  </section>

  <section class="acct__section">
    <h2 class="acct__section-title">我的地址</h2>

    <p v-if="loading" class="acct__desc" role="status">正在加载地址…</p>

    <!-- 降级态：读不到整块给「暂不可用」+ 重试，形状照 ProfileView 的那块 -->
    <div v-else-if="loadFailed" class="acct__fallback">
      <p class="acct__fallback-text">地址服务暂不可用，请稍后重试</p>
      <button class="acct__retry" type="button" @click="load()">重试</button>
    </div>

    <template v-else>
      <!-- 表单展开时收起新增入口：留着它再点一次会把已填的内容清空 -->
      <button v-if="!formOpen" class="acct__retry" type="button" @click="openCreate">
        新增地址
      </button>

      <!-- 空态与上面的降级块**刻意不同形**（虚线框 / 无底 / 引导新增），别把两者合成一块 -->
      <div v-if="!addresses.length" class="acct__empty">
        <p class="acct__empty-text">还没有收货地址</p>
        <button v-if="!formOpen" class="acct__retry" type="button" @click="openCreate">
          添加第一条地址
        </button>
      </div>

      <ul v-else class="acct__addr-list">
        <li v-for="a in addresses" :key="a.id" class="acct__addr-item">
          <div class="acct__addr-main">
            <div class="acct__addr-line">
              <span class="acct__addr-name">{{ a.receiverName }}</span>
              <span class="acct__addr-phone">{{ a.receiverPhone }}</span>
              <span v-if="a.isDefault === 1" class="acct__tag">默认</span>
            </div>
            <p class="acct__addr-text">{{ fullAddress(a) }}</p>
          </div>

          <div class="acct__addr-ops">
            <!-- 删除是**行内两步**：本行按钮组就地变成「确认删除 / 取消」，文案显式区分，
                 不弹窗、不用 window.confirm（本端没有模态层，见文件头 ②） -->
            <template v-if="confirmingId === a.id">
              <span class="acct__addr-confirm">确认删除？</span>
              <button
                class="acct__link acct__link--danger"
                type="button"
                :disabled="busyIds.includes(a.id)"
                @click="remove(a)"
              >
                {{ busyIds.includes(a.id) ? '删除中…' : '确认删除' }}
              </button>
              <button class="acct__link" type="button" @click="cancelRemove">取消</button>
            </template>

            <template v-else>
              <button class="acct__link" type="button" @click="openEdit(a)">编辑</button>
              <!-- 默认地址只留「默认」标记，不再给它一个必然无效的「设为默认」 -->
              <button
                v-if="a.isDefault !== 1"
                class="acct__link"
                type="button"
                :disabled="busyIds.includes(a.id)"
                @click="makeDefault(a)"
              >
                {{ busyIds.includes(a.id) ? '设置中…' : '设为默认' }}
              </button>
              <button class="acct__link" type="button" @click="askRemove(a)">删除</button>
            </template>
          </div>
        </li>
      </ul>
    </template>
  </section>
</template>
