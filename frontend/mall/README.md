# 全景商城 — 前台商城（mall）

mall 前台（用户购物端）的正式前端工程：**Vue 3 + Vite + TypeScript**。
**账号功能已接入后端 `mall-bff`**（取码 / 注册 / 登录 / 登出 / 当前顾客，共 5 条）；
**首页六个区块的数据仍全部静态写死**（在 `src/mock/`），首页数据聚合属 mall-bff 二期。

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
├── vite.config.ts          端口 5175 + /mall 代理（→ 网关 8080 → mall-bff 8085）
├── tsconfig.json           strict: true
└── src/
    ├── main.ts             createApp + router + 载入四个 css（令牌 → 基线 → 区块 → 账号页）
    ├── App.vue             <router-view /> + <ToastHost />（全局提示条）
    ├── router/index.ts     hash 模式；/ 首页、/login、/register；兜底重定向 /；全局守卫
    ├── styles/
    │   ├── tokens.css      ★ 唯一换肤入口（只改这个文件即可整体换色）
    │   ├── base.css        reset + 排版基线 + 容器 + 通用工具类
    │   ├── mall.css        首页六个区块的样式，顺序与 HomeView 一致
    │   └── account.css     登录 / 注册页 + 轻提示条（只消费令牌）
    ├── types/
    │   ├── mall.ts         Category / Banner / Goods / GoodsTag
    │   └── auth.ts         CurrentUser / LoginResult / 登录注册请求体
    ├── api/
    │   ├── request.ts      axios 实例 + 拦截器（解 RespData、统一报错）
    │   └── auth.ts         5 条账号接口（路径照 docs/contracts/mall-bff.md）
    ├── store/auth.ts       C 端登录态（token 存 localStorage，user 驻留内存）
    ├── mock/               首页静态数据：categories / banners / goods / hotwords
    ├── utils/              gradient.ts（渐变占位）、format.ts（价格 / 角标 / 手机号打码）
    ├── composables/        useCarousel.ts（轮播）、useSmsCode.ts（取码倒计时）、useToast.ts（提示条）
    ├── components/         六个区块 + 页脚 + AccountShell（账号页外壳）+ ToastHost
    └── views/              HomeView.vue（六区块）+ LoginView.vue + RegisterView.vue
```

## 页面结构（首页自上而下，顺序即基准）

| # | 区块 | 组件 |
|---|---|---|
| ① | 顶部用户条 | `TopBar.vue` |
| ② | 万能搜索长框（含热搜词行） | `SearchBar.vue` |
| ③ | 全分类展示（10 个一级分类宫格） | `CategoryGrid.vue` |
| ④ | 大型滚动广告框（自动播放 / 箭头 / 圆点 / 悬停暂停） | `BannerCarousel.vue` |
| ⑤ | 用户信息展示框 | `UserPanel.vue` |
| ⑥ | 热门商品列表（5 列 × 2 行 = 10 件） | `GoodsGrid.vue` + `GoodsCard.vue` |
| — | 极简页脚 | `SiteFooter.vue` |

登录页（`/login`）与注册页（`/register`）用 `AccountShell.vue` 复用同一套顶栏与页脚。

## 账号与登录态（已接入 mall-bff）

| 动作 | 接口 | 入口 |
|---|---|---|
| 获取验证码 | `POST /mall/auth/sms-code` | 登录页 / 注册页 |
| 注册（**注册即登录**） | `POST /mall/auth/register` | `/register` |
| 登录 | `POST /mall/auth/login` | `/login` |
| 退出 | `POST /mall/auth/logout` | 顶栏「退出」 |
| 当前顾客 | `GET /mall/auth/me` | 路由守卫（刷新后重建用户态） |

- **账号即手机号**，验证方式是**手机号 + 短信验证码**，没有密码。
- ⚠ 短信是**模拟通道**：后端取码只打一行日志、不发真实短信，校验与**固定码 `888888`** 比对。
  因此登录 / 注册页上固定有一行「演示环境：短信为模拟通道，验证码固定 888888」——
  **去掉它页面上就没有任何途径得知验证码**，这不是假功能，是契约本身。
- 登录态：`localStorage['pm-mall-token']` 存 token；用户信息只驻留内存，刷新后由守卫拉 `/auth/me` 重建。
- ⚠ **401 不自动跳登录页** —— 与 store / admin 两端的**刻意差异**：mall 首页是公开页，
  带过期 token 的游客不该被弹走，页面照常按未登录态渲染；跳不跳由守卫 / 调用方决定。
- **首页不要求登录**，游客可正常浏览（守卫只做「已登录别去登录页」和「刷新重建用户态」两件事）。
- 用户信息框（⑤）里的优惠券 / 积分 / 收藏**恒为 0**：这三项接口属二期，不编造假数字。

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

- ❌ **首页六区块不接接口**：数据全在 `src/mock/`（账号接口已接入，见上）
- ❌ 不做手机 / 窄屏适配 —— **只做宽屏**，容器固定 1280px，没有任何媒体查询
- ❌ 不做暗色模式（C 端商城不做，与 admin / store 的 `.dark` 是两回事）
- ❌ 不引外部图片与字体 —— 图位一律用 **CSS 渐变占位**（`utils/gradient.ts`）
- ❌ 页面里**不使用任何 Element Plus 组件**（见下）
- ❌ 「购物车 / 我的订单」仍是死链（后端没有对应接口）

## 关于 Element Plus

`element-plus` 在 `package.json` 依赖里，但**仅作后续页面（表单 / 弹窗 / 分页）的备用能力**：
`main.ts` 不注册 EP、不引 EP 样式，**账号页的表单与提示条也都是手写的**（`styles/account.css`）——
**前台保持自己单独一套风格，不做样式变换**。

将来某一页真要用 EP，在**那一页**按需引组件与样式，并把 EP 变量重映射到 `src/styles/tokens.css` 的橙红令牌，
不要全局引 `element-plus/dist/index.css`（会把整站观感拉成后台风）。

## 接口与分层

页面只经网关访问端 BFF：`vite.config.ts` 的 `/mall` 代理 → 网关 8080（`StripPrefix=1`）→ `mall-bff`(8085)。
账号 5 条**已接入**（`src/api/auth.ts`，路径照契约表 [`docs/contracts/mall-bff.md`](../../docs/contracts/mall-bff.md)）。

首页数据聚合是**二期**：届时 mall-bff 经 Feign 调 goods-center 取得商品 / 分类，页面再把 `src/mock/` 换成接口数据。
在此之前，首页六个区块的数据来源就是 `src/mock/`。
