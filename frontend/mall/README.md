# 全景商城 — 前台商城（mall）

C 端前台（顾客侧）的正式前端工程：**Vue 3 + Vite + TypeScript**，端口 **5175**。页面只经网关访问端 BFF `mall-bff`（:8085）——`vite.config.ts` 的 `/mall` 代理 → 网关 8080（`StripPrefix=1`）→ mall-bff。

页面域：首页（六区块）、搜索结果与分类商品列表、商品详情、登录 / 注册、账号（个人资料 / 收货地址）。

## 业务边界

- **视觉与结构见根目录 [CLAUDE.md](../../CLAUDE.md) 的「mall 前台（用户端）视觉与结构约定」**——本工程既是那份基准的落地，也是它的唯一风格源头（新增区块或调风格，先在本工程里改好、定了，再往外铺）。
- 登录分级：首页公开；**一涉及商品查询 / 详情与顾客自己的数据就要登录**，401 一律提示并跳登录页（与 store / admin 两端一致）。同上，条文以 CLAUDE.md 为准。
- 页面契约见 [docs/contracts/mall-bff.md](../../docs/contracts/mall-bff.md)：**写 / 改页面只照表写，不照后端代码写**。

## 仅在本文件登记的事

- **唯一换肤入口**：[`src/styles/tokens.css`](./src/styles/tokens.css)。它是 C 端促销风橙红令牌，与 admin / store 两端的靛蓝后台令牌**刻意不同源**，两套不通用；换风格只改该文件顶部「可调整」区块，其余样式文件不用动。
- **`src/mock/` 是首页的静态数据**（`banners` / `goods` / `hotwords`，分别供 ④ 轮播广告、⑥ 热门商品列表、② 热搜词），其中**⑥ 热门商品列表仍读它、不接接口**。数据逐条刻意探过边界，各文件顶部的 `[探]` 注释逐条记着该项在探什么（长文案、无原价、双角标、销量 0 / 万+、价格位数、最坏叠加…）——**这是刻意基准，不要随手改小**。
- ⑤ 用户信息框里的优惠券 / 积分 / 收藏**恒为 0**：这三项接口属二期，不编造假数字。

## 本地开发

```bash
npm install && npm run dev   # → http://localhost:5175
```

`npm run build` = `vue-tsc --noEmit && vite build`（类型不过即构建失败）；`npm run type-check` 只跑类型检查，`npm run preview` 预览构建产物。
