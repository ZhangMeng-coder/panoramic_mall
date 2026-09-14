# 全景商城 — 前端

前端按项目拆分为三个子项目（admin / store 各自独立 `package.json` / `node_modules`，互不依赖；mall 目前是零构建静态页，无 npm 依赖）：

| 目录 | 说明 | 技术栈 |
|---|---|---|
| [admin/](./admin/) | 后端管理项目（商品分类/品牌/SPU-SKU、用户/角色/权限、店铺审核） | Vue 3 + Vite + Element Plus |
| [store/](./store/) | 商城店铺端（店主注册登录 + 店铺信息维护 + 在售商品管理；订单/库存占位） | Vue 3 + Vite + Element Plus |
| [mall/](./mall/) | 商城前台（用户购物端）——**当前是风格基准样张**，非最终实现 | 零构建静态页（HTML/CSS/JS，无 npm） |

## 本地开发

```bash
# 管理后台（默认端口 5173）
cd admin && npm install && npm run dev

# 商城店铺端（默认端口 5174）
cd store && npm install && npm run dev
```

**商城前台（mall）不需要 npm**：直接双击 `frontend/mall/index.html` 即可打开风格样张。

admin / store 均通过 Vite dev proxy 转发至网关 `http://localhost:8080`（admin：`/admin`、`/goods`、`/store`、`/discovery`；store：`/store`、`/auth`）。

> 管理后台与店铺端共用同一套设计令牌（`src/styles/tokens.css` 色板 + Element Plus 主题映射），明暗主题各自持久化（`pm-admin-theme` / `pm-store-theme`）。
>
> **商城前台（mall）是另一套令牌，刻意不同源** —— C 端促销风橙红色板，只有 `frontend/mall/styles/tokens.css` 一个换肤入口，与上面两端的靛蓝后台令牌不通用。
>
> ⚠ mall 前台的视觉与结构是**受约束**的基准，见根目录 [CLAUDE.md](../CLAUDE.md) 的「mall 前台（用户端）视觉与结构约定」。要新增区块或改风格，先在 `frontend/mall` 样张里改好、定了，再落到正式页面。
