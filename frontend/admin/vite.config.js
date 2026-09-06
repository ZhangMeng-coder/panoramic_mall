import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    // 开发代理：/admin（含 /admin/goods/** 商品模板）与 /discovery 转发到 API 网关（8080）
    // 链路：5173/admin/xxx -> 网关 8080（StripPrefix=1）-> admin 8082 -> 内部 Feign -> goods-center 8081
    // 商品模板不再直连 /goods：goods-center 已下沉为纯域，仅由 admin 内部 Feign 调用。
    proxy: {
      '/admin': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      '/store': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      '/discovery': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
