import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5175,
    // 开发代理：/mall 转发到 API 网关（8080）
    // 链路：5175/mall/xxx -> 网关 8080（StripPrefix=1）-> mall-bff(8085)
    // ⚠ 后端 mall-bff 已就绪（见 docs/contracts/mall-bff.md，账号 5 条 + 公开浏览 3 条 = 8 条接口），
    //   本工程页面已经接入：账号走 /mall/auth/**、商品搜索与分类走 /mall/catalog/**，都经下面这条代理。
    proxy: {
      '/mall': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
