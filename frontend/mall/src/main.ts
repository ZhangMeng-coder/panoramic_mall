import { createApp } from 'vue'
// 载入顺序：令牌 → 基线 → 首页区块 → 账号页与提示条 → 商品列表页（catalog）→ 购物车页（cart）
// ⚠ tokens.css 必须留在第一个（base.css 及后续样式都依赖它的令牌）
import './styles/tokens.css'
import './styles/base.css'
import './styles/mall.css'
import './styles/account.css'
import './styles/catalog.css'
import './styles/cart.css'
import App from './App.vue'
import router from './router'

/**
 * Element Plus 已列入依赖，但**仅作后续页面（表单 / 弹窗 / 分页）的备用能力**：
 * mall 前台保持自己单独一套风格，所以这里不注册 EP、也不引 EP 的样式。
 * 账号页的表单与提示条都是手写的（样式在 styles/account.css，只消费 tokens.css 令牌）。
 * 将来某一页真要用 EP 时，在那一页按需引组件与样式，并把 EP 变量重映射到香橙红令牌。
 */
const app = createApp(App)

app.use(router)
app.mount('#app')
