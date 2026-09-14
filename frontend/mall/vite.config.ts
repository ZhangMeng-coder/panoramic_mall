import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5175,
    // 开发代理：/mall 转发到 API 网关（8080）
    // 链路：5175/mall/xxx -> 网关 8080（StripPrefix=1）-> mall-bff(8085)
    // ⚠ 后端 mall-bff 已就绪（见 docs/contracts/mall-bff.md，取码/注册/登录 5 条接口），
    //   但本工程页面尚未接入、当前不发任何请求；代理前缀与契约一致，接页面时无需改这里。
    proxy: {
      '/mall': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
