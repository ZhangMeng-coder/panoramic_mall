import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
// 暗色变量需在浅色变量之后、主题令牌之前引入，保证覆盖顺序：EP浅 -> EP暗 -> 令牌
import 'element-plus/theme-chalk/dark/css-vars.css'
import './styles/tokens.css'
import './styles/base.css'
import './styles/components.css'
import App from './App.vue'
import router from './router'
import perm from './directives/perm'

const app = createApp(App)
app.use(ElementPlus, { locale: zhCn })
app.use(router)
// v-perm 按钮权限指令（无权限自动移除元素）
app.directive('perm', perm)
app.mount('#app')
