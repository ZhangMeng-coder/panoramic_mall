import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    // 开发代理：/admin（含 /admin/goods/** 商品模板、/admin/shop/** 店铺管理）与 /discovery 转发到 API 网关（8080）
    // 链路：5173/admin/xxx -> 网关 8080（StripPrefix=1）-> admin 8082（端 BFF）-> 内部 Feign -> goods-center/store
    // 商品模板、店铺管理不再直连 /goods、/store：业务域已下沉为纯域，仅由 admin BFF 内部 Feign 调用。
    proxy: {
      '/admin': {
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
