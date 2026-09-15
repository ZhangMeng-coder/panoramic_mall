import type { Directive } from 'vue'
import { hasPerm } from '../store/auth'

/**
 * v-perm 按钮权限指令：
 * 当前登录用户缺少对应 perms 时，直接移除该元素。
 * 用法：
 *   <el-button v-perm="'system:user:add'">新增</el-button>
 *   <el-button v-perm="['goods:brand:edit','goods:brand:delete']">管理</el-button>
 *
 * 说明：页面在“用户上下文已就绪”后才挂载（路由守卫先拉 /me），
 * 因此 mounted 时读一次即可，无需响应式追踪 perms。
 *
 * 绑定值类型为 `string | string[]`（与 `hasPerm` 的入参一致）。
 */
const perm: Directive<HTMLElement, string | string[]> = {
  mounted(el, binding) {
    if (!hasPerm(binding.value)) {
      el.remove()
    }
  }
}

export default perm
