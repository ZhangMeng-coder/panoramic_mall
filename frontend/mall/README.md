# 全景商城 — 前台商城（mall）

mall 前台（用户购物端）的正式前端工程：**Vue 3 + Vite + TypeScript**。
当前**页面内容全部静态写死**（数据在 `src/mock/`），**不发起任何接口请求**（后端 mall-bff 已就绪，但页面尚未接入）。

> **风格基准**：本工程即 mall 前台的风格基准，换肤唯一入口是 [`src/styles/tokens.css`](./src/styles/tokens.css)。
> 约束条文见根目录 [CLAUDE.md](../../CLAUDE.md) 的「mall 前台（用户端）视觉与结构约定」。
> **前台是单独一套风格**：C 端促销风橙红板，与 admin / store 的靛蓝后台令牌**刻意不同源**，两套不通用。

## 本地开发

```bash
cd mall
npm install
npm run dev      # http://localhost:5175
```

| 脚本 | 作用 |
|---|---|
| `npm run dev` | 开发服务，端口 **5175** |
| `npm run build` | **`vue-tsc --noEmit && vite build`** —— 类型检查不过即构建失败 |
| `npm run type-check` | 只跑类型检查 |
| `npm run preview` | 预览构建产物 |

## 文件结构

```
mall/
├── index.html              Vite 入口（只有 #app，样式由 main.ts 引入）
├── vite.config.ts          端口 5175 + /mall 代理（按契约预留，当前无调用方）
├── tsconfig.json           strict: true
└── src/
    ├── main.ts             createApp + router + 载入三个 css（顺序：令牌 → 基线 → 区块）
    ├── App.vue             <router-view />
    ├── router/index.ts     hash 模式；/ → HomeView，兜底重定向 /
    ├── styles/
    │   ├── tokens.css      ★ 唯一换肤入口（只改这个文件即可整体换色）
    │   ├── base.css        reset + 排版基线 + 容器 + 通用工具类
    │   └── mall.css        六个区块的样式，顺序与 HomeView 一致
    ├── types/mall.ts       Category / Banner / Goods / GoodsTag
    ├── mock/               静态数据：categories / banners / goods / hotwords / session
    ├── utils/              gradient.ts（渐变占位）、format.ts（价格 / 角标）
    ├── composables/        useCarousel.ts（自动播放 / 箭头 / 圆点 / 悬停暂停）
    ├── components/         六个区块 + 页脚（GoodsCard 为商品卡子组件）
    └── views/HomeView.vue  按基准顺序组装六个区块
```

## 页面结构（自上而下，顺序即基准）

| # | 区块 | 组件 |
|---|---|---|
| ① | 顶部用户条 | `TopBar.vue` |
| ② | 万能搜索长框（含热搜词行） | `SearchBar.vue` |
| ③ | 全分类展示（10 个一级分类宫格） | `CategoryGrid.vue` |
| ④ | 大型滚动广告框（自动播放 / 箭头 / 圆点 / 悬停暂停） | `BannerCarousel.vue` |
| ⑤ | 用户信息展示框 | `UserPanel.vue` |
| ⑥ | 热门商品列表（5 列 × 2 行 = 10 件） | `GoodsGrid.vue` + `GoodsCard.vue` |
| — | 极简页脚 | `SiteFooter.vue` |

## 登录态

尚未接入登录，`src/mock/session.ts` 里写死 `loggedIn = false`（未登录态）。
顶栏与用户信息框**两套形态都已实现** —— 把该常量改成 `true` 即可查看已登录版式，不用改任何组件。

## 换风格改哪里

只改 `src/styles/tokens.css` 顶部那一小块「可调整」变量，其余文件不用动：

- `--brand` / `--brand-accent` / `--brand-soft` —— 主色、辅色、浅色底（换色板就改这几个）
- `--n*` —— 中性色（页面底、边框、文字三级灰）
- `--r-*` —— 圆角；`--sh-*` —— 阴影（暖调投影）；`--t-*` —— 字号

## 内容范围是刻意探过边界的

`src/mock/` 里的数据不是随便凑的，每一项都在探一条边界，用来判断「装多少、装不下怎么办」：

| 数据 | 探什么 |
|---|---|
| 分类恰好 10 项 | 宫格是否正好铺满一行，增删后换行好不好看 |
| 轮播第 3 张标题拉长 | 长文案下版式会不会挤爆 / 换行难看 |
| 商品 g1 超长名 | 两行截断够不够 |
| 商品 g2 无原价、无角标 | 版式留白会不会塌 |
| 商品 g3 双角标 | 角标放得下吗、会不会压住图 |
| 商品 g4 销量 0 | 空值显示成「暂无成交」是否可接受 |
| 商品 g5 / g7 销量「10万+」「5.6万+」 | 大数字会不会挤掉价格 |
| 商品 g6 价格带两位小数 | 价格三层字号的宽度 |
| 商品 g8 长名 + 双角标 + 大销量 | 最坏情况叠加 |

⚠ **这是基准，不要随手改小。** 各 `mock/*.ts` 文件顶部的 `[探]` 注释逐条记着该数据在探什么。

## 明确**不做**的事

- ❌ 不接任何接口（无 API、无 mock 服务），数据全在 `src/mock/`
- ❌ 不做手机 / 窄屏适配 —— **只做宽屏**，容器固定 1280px，没有任何媒体查询
- ❌ 不做暗色模式（C 端商城不做，与 admin / store 的 `.dark` 是两回事）
- ❌ 不引外部图片与字体 —— 图位一律用 **CSS 渐变占位**（`utils/gradient.ts`）
- ❌ 页面里**不使用任何 Element Plus 组件**（见下）

## 关于 Element Plus

`element-plus` 在 `package.json` 依赖里，但**仅作后续页面（表单 / 弹窗 / 分页）的备用能力**：
`main.ts` 不注册 EP、不引 EP 样式，首页零 EP 组件 —— **前台保持自己单独一套风格，不做样式变换**。

将来某一页真要用 EP，在**那一页**按需引组件与样式，并把 EP 变量重映射到 `src/styles/tokens.css` 的橙红令牌，
不要全局引 `element-plus/dist/index.css`（会把整站观感拉成后台风）。

## 接口与分层

mall 前台的 BFF **`mall-bff` 已就绪**（`backend/mall-bff`，端口 8085，身份 `type=user`）——
**一期只做 C 端顾客账号**（取码 / 注册 / 登录 / 登出 / me，共 5 条），**不调任何业务域**；
首页数据聚合是二期。契约登记在 [`docs/contracts/mall-bff.md`](../../docs/contracts/mall-bff.md)。

按仓库分层约定，页面只经网关访问端 BFF：`vite.config.ts` 里的 `/mall` 代理已按契约里的网关前缀配好，
**接页面时无需改代理配置**。⚠ 但本工程**当前仍未接入任何接口**——首页六个区块的数据依然全部来自 `src/mock/`。

> 接口形态提醒（接入时照契约表写，不照后端代码写）：账号即手机号、短信为**模拟通道**（固定码 `888888`，
> 取码接口只打日志不发真实短信），登录成功返回 `{token, user}`，后续请求带 `Authorization: Bearer <token>`。
