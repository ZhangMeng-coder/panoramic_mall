# 全景商城 — 前端

前端按项目拆分为三个独立子项目（各有独立的 `package.json` / `node_modules`，互不依赖）：

| 目录 | 说明 | 技术栈 |
|---|---|---|
| [admin/](./admin/) | 后端管理项目（商品分类/品牌/SPU-SKU、用户/角色/权限、店铺审核） | Vue 3 + Vite + Element Plus |
| [store/](./store/) | 商城店铺端（店主注册登录 + 店铺信息维护 + 在售商品管理；订单/库存占位） | Vue 3 + Vite + Element Plus |
| [mall/](./mall/) | 前端商城项目（用户购物端） | 占位，待开发 |

## 本地开发

```bash
# 管理后台（默认端口 5173）
cd admin && npm install && npm run dev

# 商城店铺端（默认端口 5174）
cd store && npm install && npm run dev

# 商城前台（待脚手架，建议端口 5175）
cd mall && npm install && npm run dev
```

各子项目均通过 Vite dev proxy 转发至网关 `http://localhost:8080`（admin：`/admin`、`/goods`、`/store`、`/discovery`；store：`/store`、`/auth`）。

> 管理后台与店铺端共用同一套设计令牌（`src/styles/tokens.css` 色板 + Element Plus 主题映射），明暗主题各自持久化（`pm-admin-theme` / `pm-store-theme`）。
