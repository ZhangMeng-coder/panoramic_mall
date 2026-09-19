<script setup lang="ts">
import { computed, ref } from 'vue'
import { authApi } from '../../api/auth'
import { profileApi } from '../../api/profile'
import { getUser, setUser } from '../../store/auth'
import { showToast } from '../../composables/useToast'
import { PHONE_RE, useSmsCode } from '../../composables/useSmsCode'
import { maskPhone } from '../../utils/format'
import type { ProfilePayload } from '../../types/profile'

/**
 * 个人资料页（`/account/profile`，**需登录态**）—— 左右分栏由父级 `AccountLayout` 提供，
 * 本页只写右栏内容。两项任务：改资料、换绑手机号。
 *
 * 四条口径（都不是随手写的）：
 * ① **资料读走 `/auth/me`、写走 `PUT /profile`，没有单独的 GET /profile**（契约「资料读口径」）。
 *    写是**整份替换**：四项每次全传、未传即写 NULL，故本页**不做「非 null 才传」过滤**——
 *    过滤了「清空头像 / 清空生日」就会静默失效。
 * ② **昵称不另判兜底**：`/auth/me` 在资料为空时已把昵称回退成手机号（兜底只此一处）。
 *    本页照读照写——再加一层「昵称 == 手机号就当空」的前端推断，等于在别处又写一份该规则。
 * ③ **短信是模拟通道**（固定码 888888）：「获取验证码」分别对**当前号**与**新号**各调一次
 *    `authApi.sendSmsCode`；换绑成功后服务端**不重签 token**，前端只需刷新一次 store。
 * ④ **用户态可能不在**（守卫对 `/auth/me` 的非 401 失败放行，见 `profileReady` 的注释）——
 *    那时只渲染降级块 + 重试，**不渲染表单**。
 */

const GENDERS: { value: number; label: string }[] = [
  { value: 0, label: '未知' },
  { value: 1, label: '男' },
  { value: 2, label: '女' }
]

/**
 * ⚠ **不假定用户态一定在**：守卫对 `/auth/me` 的**非 401 失败**（超时 / 网络 / 5xx）是**不跳转、直接放行**的
 * （`router/index.ts` 守卫的 catch ② 有长注释论证为什么不能跳：token 还在，跳登录页会被弹回来成环）。
 * 那条路径上 token 仍在、`getUser()` 却是 **null** —— 此时**绝不能渲染表单**：
 * `PUT /profile` 是整份替换，空表单点一次「保存」就把已存的昵称 / 头像 / 生日**静默清光**。
 * 故 `profileReady` 为假时只渲染降级块 + 「重试」（见 `retry()`）。
 */
const profileReady = ref(getUser() !== null)
const retrying = ref(false)

/* ---- 资料：初值由 `initFromUser()` 填（保存 / 重试成功后同样按后端回读刷新 store） ---- */
const nickname = ref('')
const avatar = ref('')
/** null = 未设置（后端列可为 NULL）；选了「未知」是 0，两者不同，别用 0 当默认值 */
const gender = ref<number | null>(null)
/** `<input type="date">` 的值就是 `YYYY-MM-DD`；后端 LocalDate 关掉了时间戳序列化，可直接回传 */
const birthday = ref('')

const saving = ref(false)

/** 头像预览地址（去空白）；与「加载失败的地址」比——同址不重试、改了地址自动再试 */
const previewSrc = computed(() => avatar.value.trim())
const avatarFailed = ref('')
const showPreview = computed(
  () => Boolean(previewSrc.value) && previewSrc.value !== avatarFailed.value
)

function onAvatarError(): void {
  avatarFailed.value = previewSrc.value
}

/** 四项全传：空的文本字段送 null（清空语义），不是「省略不传」 */
function payload(): ProfilePayload {
  return {
    nickname: nickname.value.trim() || null,
    avatar: previewSrc.value || null,
    gender: gender.value,
    birthday: birthday.value || null
  }
}

async function save(): Promise<void> {
  saving.value = true
  try {
    await profileApi.save(payload())
    showToast('资料已保存', 'success')
    // 保存成功后再读一次 me（**普通调用**：不传 silent401）刷新 store —— 顶栏的昵称随之更新。
    // 会话真没了就走 401 出口（清态 + 提示 + 跳登录页），而不是静默留着旧资料装作没事。
    setUser(await authApi.me())
  } catch {
    // 拦截器已弹后端 msg（如「昵称不能超过50个字符」）
  } finally {
    saving.value = false
  }
}

/* ---- 换绑手机号 ---- */
const currentPhone = ref('')
const oldCode = ref('')
const newPhone = ref('')
const newCode = ref('')

/** 两个取码按钮各一个实例（各自 60s 倒计时）：旧码发到当前号，新码发到新号 */
const { countdown: oldCountdown, send: sendOldCode } = useSmsCode(currentPhone)
const { countdown: newCountdown, send: sendNewCode } = useSmsCode(newPhone)

const changing = ref(false)

/**
 * 用 store 里的用户态填满表单（**首屏与「重试」成功后各调一次**——重试成功却只 setUser 不重填，
 * 表单仍是空的，等于没修）。`profileReady` 一并按「用户态在不在」重算。
 */
function initFromUser(): void {
  const current = getUser()
  nickname.value = current?.nickname ?? ''
  avatar.value = current?.avatar ?? ''
  gender.value = current?.gender ?? null
  birthday.value = current?.birthday ?? ''
  currentPhone.value = current?.phone ?? ''
  profileReady.value = current !== null
}

initFromUser()

/**
 * 降级态的重试：**普通调用**（不传 `silent401`，与 `save()` 成功后的那次刷新同口径）——
 * 会话真没了就走 401 出口（清态 + 提示 + 跳登录页），别静默留在降级块上。
 */
async function retry(): Promise<void> {
  retrying.value = true
  try {
    setUser(await authApi.me())
    initFromUser()
  } catch {
    // 拦截器已弹提示；401 会走 401 出口
  } finally {
    retrying.value = false
  }
}

async function changePhone(): Promise<void> {
  const newPhoneValue = newPhone.value.trim()
  // 只做格式级快速反馈（与登录 / 注册页一致）；业务规则（同号 / 重复注册）由后端判并回 400
  if (!oldCode.value.trim()) {
    showToast('请输入当前手机号的验证码')
    return
  }
  if (!PHONE_RE.test(newPhoneValue)) {
    showToast('请输入正确的手机号')
    return
  }
  if (!newCode.value.trim()) {
    showToast('请输入新手机号的验证码')
    return
  }

  changing.value = true
  try {
    await authApi.changePhone({
      oldCode: oldCode.value.trim(),
      newPhone: newPhoneValue,
      newCode: newCode.value.trim()
    })
    showToast('手机号已更换', 'success')
    // 服务端不重签 token，但登录态快照里的手机号已换 —— 读一次 me 让顶栏与页内展示跟上
    const me = await authApi.me()
    setUser(me)
    currentPhone.value = me.phone
    oldCode.value = ''
    newPhone.value = ''
    newCode.value = ''
  } catch {
    // 拦截器已弹后端 msg（验证码错误 / 新手机号已注册 / 与当前手机号相同等）
  } finally {
    changing.value = false
  }
}
</script>

<template>
  <h1 class="acct__title">个人资料</h1>

  <!-- 降级态（用户态拿不到）：**不渲染表单**——`PUT /profile` 是整份替换，
       空表单被点一次「保存」就把已存资料静默清光；也别渲染成空表单让人以为资料真是空的 -->
  <template v-if="!profileReady">
    <p class="acct__desc">暂时读不到你的资料</p>
    <div class="acct__fallback">
      <p class="acct__fallback-text">资料暂不可用，请稍后重试</p>
      <button class="acct__retry" type="button" :disabled="retrying" @click="retry">
        {{ retrying ? '重试中…' : '重试' }}
      </button>
    </div>
  </template>

  <template v-else>
    <p class="acct__desc">昵称与头像会显示在顶栏和首页的用户卡上</p>

    <section class="acct__section">
      <h2 class="acct__section-title">基本资料</h2>

      <form class="acct__form" autocomplete="off" @submit.prevent="save">
        <div class="acct__avatar-row">
          <div class="acct__avatar">
            <!-- 无头像（或地址是死链）时不留替代图形，只剩 .acct__avatar 那层 CSS 渐变占位
                 （不引外部图片 / 字体；加载失败的回退与首页用户卡同一处理） -->
            <img
              v-if="showPreview"
              class="acct__avatar-img"
              :src="previewSrc"
              :alt="nickname || '头像'"
              @error="onAvatarError"
            />
          </div>
          <div class="acct__avatar-side">
            <div class="field">
              <label class="field__label" for="pfAvatar">头像地址</label>
              <div class="field__box">
                <input
                  id="pfAvatar"
                  v-model="avatar"
                  class="field__input"
                  type="text"
                  maxlength="255"
                  placeholder="图片链接，如 https://…"
                />
              </div>
              <p class="field__hint">填图片地址；留空则显示渐变占位头像</p>
            </div>
          </div>
        </div>

        <div class="field">
          <label class="field__label" for="pfNickname">昵称</label>
          <div class="field__box">
            <input
              id="pfNickname"
              v-model="nickname"
              class="field__input"
              type="text"
              maxlength="50"
              placeholder="不超过 50 个字符"
            />
          </div>
        </div>

        <div class="field">
          <span class="field__label">性别</span>
          <!-- 三选一：原生 radio（藏在 .sr-only 里）保证键盘与读屏可达，焦点环画在可见胶囊上 -->
          <div class="seg" role="radiogroup" aria-label="性别">
            <label v-for="g in GENDERS" :key="g.value" class="seg__item">
              <input
                v-model="gender"
                class="sr-only"
                type="radio"
                name="pfGender"
                :value="g.value"
              />
              <span class="seg__opt">{{ g.label }}</span>
            </label>
          </div>
        </div>

        <div class="field">
          <label class="field__label" for="pfBirthday">生日</label>
          <div class="field__box">
            <!-- 原生日期控件：保留自带的日历按钮（`.field__input` 的 appearance:none 会把它一起去掉） -->
            <input
              id="pfBirthday"
              v-model="birthday"
              class="field__input field__input--native"
              type="date"
            />
          </div>
        </div>

        <button class="btn-primary" type="submit" :disabled="saving">
          {{ saving ? '保存中…' : '保存资料' }}
        </button>
      </form>
    </section>

    <section class="acct__section">
      <h2 class="acct__section-title">换绑手机号</h2>

      <form class="acct__form" autocomplete="off" @submit.prevent="changePhone">
        <p class="account__tip">短信为模拟通道：取码只记日志、不发真实短信，验证码固定 <b>888888</b></p>
        <p class="acct__current">当前手机号 <b>{{ maskPhone(currentPhone) }}</b></p>

        <div class="field">
          <label class="field__label" for="pfOldCode">当前手机号的验证码</label>
          <div class="field__box">
            <input
              id="pfOldCode"
              v-model="oldCode"
              class="field__input"
              type="text"
              maxlength="6"
              inputmode="numeric"
              placeholder="6 位验证码"
            />
            <button
              class="field__code"
              type="button"
              :disabled="oldCountdown > 0"
              @click="sendOldCode"
            >
              {{ oldCountdown > 0 ? `${oldCountdown}s 后重发` : '获取验证码' }}
            </button>
          </div>
        </div>

        <div class="field">
          <label class="field__label" for="pfNewPhone">新手机号</label>
          <div class="field__box">
            <input
              id="pfNewPhone"
              v-model="newPhone"
              class="field__input"
              type="tel"
              maxlength="11"
              inputmode="numeric"
              placeholder="请输入 11 位手机号"
            />
            <button
              class="field__code"
              type="button"
              :disabled="newCountdown > 0"
              @click="sendNewCode"
            >
              {{ newCountdown > 0 ? `${newCountdown}s 后重发` : '获取验证码' }}
            </button>
          </div>
        </div>

        <div class="field">
          <label class="field__label" for="pfNewCode">新手机号的验证码</label>
          <div class="field__box">
            <input
              id="pfNewCode"
              v-model="newCode"
              class="field__input"
              type="text"
              maxlength="6"
              inputmode="numeric"
              placeholder="6 位验证码"
            />
          </div>
        </div>

        <button class="btn-primary" type="submit" :disabled="changing">
          {{ changing ? '提交中…' : '确认换绑' }}
        </button>
      </form>
    </section>
  </template>
</template>
