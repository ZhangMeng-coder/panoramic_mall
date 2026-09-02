import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    // 开发代理：/goods 与 /discovery 转发到 API 网关（8080）
    // 链路：5173/goods/xxx -> 网关 8080（StripPrefix=1）-> goods-center 8081
    proxy: {
      '/goods': {
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
