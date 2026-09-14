import { createApp } from 'vue'
// 载入顺序与原样张的 <link> 顺序一致：令牌 → 基线 → 区块样式
import './styles/tokens.css'
import './styles/base.css'
import './styles/mall.css'
import App from './App.vue'
import router from './router'

/**
 * Element Plus 已列入依赖，但**仅作后续页面（表单 / 弹窗 / 分页）的备用能力**：
 * mall 前台保持自己单独一套风格，所以这里不注册 EP、也不引 EP 的样式。
 * 将来某一页真要用时，在那一页按需引组件与样式，并把 EP 变量重映射到 styles/tokens.css 的橙红令牌。
 */
const app = createApp(App)

app.use(router)
app.mount('#app')
