<script setup lang="ts">
import { onBeforeUnmount, onMounted } from 'vue'

/**
 * 模态基座（遮罩 + 居中弹窗）—— 本端**唯一**的模态层，三处共用：
 * 详情页「立即下单」与购物车「去结算」的选地址、订单详情「修改地址」。
 *
 * 六条口径（都不是随手写的）：
 * ① **手写、不引 Element Plus**：本端没有组件库（只有自绘的 toast），模态无非「遮罩 + 居中盒子」，
 *    引一个库换来的是整包体积 + 一套与 tokens 不同源的视觉（同 `main.ts` 对 EP 的处置）。
 *    盒子只负责**外框**（白底 / 圆角 / 投影 / 宽度 / 内滚），内容由插槽给——样式见 `styles/modal.css`。
 * ② **层级 90，低于 toast 的 100**：见 `styles/modal.css` 的注释（失败提示必须盖在弹窗上看得见）。
 * ③ **Teleport 到 body**：本端有吸底元素（`.cart-sum` 是 sticky + z-index）与各自的层叠上下文，
 *    留在原地会被父级的 `overflow` / `transform` 裁掉或压住。
 * ④ **Esc 与遮罩点击都关**——两者走**同一个** `requestClose()`，行为不分家。
 * ⑤ **`closable=false` 时两者都不生效**（⚠ 不是只禁用其中一条）：提交在途时能关掉弹窗，
 *    顾客会以为「没提交」，而订单可能已经建了；取消入口在内容里也是按这个标记禁用自己。
 * ⑥ **打开即锁滚动，关闭还原「打开前」的值**（不写死空串——别处若本就锁着滚动，写死等于替它解锁）。
 *
 * ⚠ 不做焦点陷阱（本端此前没有任何模态层，也就没有任何一套焦点约定）——只保证结构上的
 * `role="dialog"` / `aria-modal` 可被读屏识别。
 */
/** ⚠ 两个都是**必填**（不设默认值）：三处调用各自都要显式表态能否关闭，别让默认值替它表态 */
const props = defineProps<{
  /** 弹窗的无障碍名（读屏用；视觉标题由内容自己渲染） */
  label: string
  /** 能否关闭：提交在途时传 `false`（见文件头 ⑤） */
  closable: boolean
}>()

const emit = defineEmits<{
  /** 请求关闭（Esc / 点遮罩）—— 由父级决定怎么关（清哪个标记、是否中止） */
  close: []
}>()

function requestClose(): void {
  if (!props.closable) return
  emit('close')
}

function onKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape') requestClose()
}

let previousOverflow = ''

onMounted(() => {
  previousOverflow = document.body.style.overflow
  document.body.style.overflow = 'hidden'
  window.addEventListener('keydown', onKeydown)
})

onBeforeUnmount(() => {
  document.body.style.overflow = previousOverflow
  window.removeEventListener('keydown', onKeydown)
})
</script>

<template>
  <!-- 点遮罩即关：`.self` 让「点盒子内部」不触发（盒子里的交互不归本组件管） -->
  <Teleport to="body">
    <div class="modal" @click.self="requestClose">
      <div class="modal__panel" role="dialog" aria-modal="true" :aria-label="props.label">
        <slot />
      </div>
    </div>
  </Teleport>
</template>
