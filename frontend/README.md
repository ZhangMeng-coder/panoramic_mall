# 全景商城 — 前端

`frontend/` 下按端拆成**三个互相独立的前端工程**（各自 `package.json` / `node_modules`，互不依赖）：

| 目录 | 职责 | 端口 |
|---|---|---|
| [admin/](./admin/) | 平台管理后台：分类 / 品牌 / 标准商品（SPU-SKU）/ 用户 / 角色 / 权限 / 店铺列表 / 店铺商品（跨店） | 5173 |
| [store/](./store/) | 店主端：注册登录、店铺信息与审核状态、在售商品与 SKU、库存 | 5174 |
| [mall/](./mall/) | C 端前台（顾客）：首页、搜索与分类商品、商品详情、账号与收货地址 | 5175 |

三端**技术形态一致**（Vue 3 + Vite + TypeScript + axios）——写法、门禁（类型检查即构建门禁）与共同约定见根目录 [CLAUDE.md](../CLAUDE.md) 的「前端三端统一技术形态」；mall 的视觉与结构另是一套受约束的基准，见同一文件的「mall 前台（用户端）视觉与结构约定」。

**页面契约的唯一裁决点是 [docs/contracts/](../docs/contracts/README.md)**：写 / 改前端只照表写，不照后端代码写。

## 本地开发

```bash
cd admin && npm install && npm run dev   # → http://localhost:5173
cd store && npm install && npm run dev   # → http://localhost:5174
cd mall  && npm install && npm run dev   # → http://localhost:5175
```

三端都经 Vite dev proxy 把本端前缀转发到 API 网关 `http://localhost:8080`（端口与前缀逐个写在各自 `vite.config.ts` 里）；跑起来前需先起网关与该端的端 BFF。各端构建 / 类型检查脚本同名，见 [CLAUDE.md](../CLAUDE.md) 的「前端三端统一技术形态」。
