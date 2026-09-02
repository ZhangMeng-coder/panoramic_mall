# 全景商城 — 前端

前端按项目拆分为两个独立子项目（各有独立的 `package.json` / `node_modules`，互不依赖）：

| 目录 | 说明 | 技术栈 |
|---|---|---|
| [admin/](./admin/) | 后端管理项目（商品分类/品牌/SPU-SKU 管理后台） | Vue 3 + Vite + Element Plus |
| [mall/](./mall/) | 前端商城项目（用户购物端） | 占位，待开发 |

## 本地开发

```bash
# 管理后台（默认端口 5173）
cd admin && npm install && npm run dev

# 商城前台（待脚手架，建议端口 5174）
cd mall && npm install && npm run dev
```

两个子项目均通过 Vite dev proxy 将 `/goods`、`/discovery` 转发至网关 `http://localhost:8080`。
