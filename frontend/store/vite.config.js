import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5174,
    // 开发代理：/store 转发到 API 网关（8080）
    // 链路：5174/store/xxx -> 网关 8080（StripPrefix=1）-> store-center 8083
    proxy: {
      '/store': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
