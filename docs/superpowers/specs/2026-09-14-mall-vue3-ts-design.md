# mall 前台：零构建样张 → Vue 3 + TypeScript 工程（设计）

> 日期：2026-09-14
> 范围：`frontend/mall` 原地转正 + 三处文档同步
> 验证口径：前端**只到构建通过**（`npm run build`），不启动 dev server、不截图、不打接口（根 CLAUDE.md 硬规则）

---

## 一、目标与非目标

### 目标
把 `frontend/mall` 从「零构建静态页（双击 `index.html` 即可看）」原地转正为 **Vue 3 + Vite + TypeScript** 工程，
作为 mall 前台的正式实现起点。**页面内容仍全部静态写死**（mock 数据留在仓库内），不接任何接口。

### 硬限定（用户明确）
**前台保持自己单独一套风格，不做任何样式变换。** 三个 CSS 文件是既成事实的视觉基准，只搬运、不重写、不重构。

### 非目标（YAGNI）
- ❌ 不接接口、不建 `src/api`、不装 axios
- ❌ 不新增页面（登录/商品列表/详情等一概不做）
- ❌ 不拆样式进 SFC、不改色板、不做暗色、不加媒体查询
- ❌ 首页不使用任何 Element Plus 组件
- ❌ 不做自动提交（完成后给 merge/PR 选项）

---

## 二、已确认决策

| # | 决策项 | 结论 |
|---|---|---|
| D1 | 项目落位 | `frontend/mall` **原地转正**；样张的 `index.html`/`scripts` 被工程替换；`styles/*.css` 迁入 `src/styles/` 继续做唯一风格源 |
| D2 | 依赖范围 | 对齐 admin/store：**加 Element Plus**，但**只当未来备用能力** —— `main.ts` 不引 EP 样式、首页零 EP 组件 |
| D3 | 样式组织 | 三份全局 CSS **原样搬入** `src/styles/`，组件不写 `<style>` |
| D4 | 演示外壳 | **删除**说明条与「切换登录态」按钮；登录态留成 `src/mock/session.ts` 的常量（默认 `false` = 未登录） |
| D5 | TypeScript | `strict: true`；`npm run build` = `vue-tsc --noEmit && vite build`，类型不过即构建失败 |
| D6 | 文档同步 | 与代码**同一改动内**改完，含根 `CLAUDE.md` / `frontend/README.md` / `docs/contracts/mall-bff.md` / `frontend/mall/README.md` |
| D7 | 路由 | 引入 `vue-router`，hash 模式，`/` → HomeView，兜底重定向 `/`（为后续页面留位） |
| D8 | 端口与代理 | dev port **5175**；proxy `/mall` → `http://localhost:8080`（按契约预留，页面不调用） |

---

## 三、目录结构

```
frontend/mall/
├── index.html                 Vite 入口：<div id="app"> + /src/main.ts（不再 <link> css）
├── package.json
├── package-lock.json          入库（与 admin/store 一致）
├── tsconfig.json              strict:true
├── tsconfig.node.json         给 vite.config.ts
├── vite.config.ts             port 5175 + proxy /mall
├── .gitignore                 照抄 admin
├── README.md                  重写为工程说明
└── src/
    ├── main.ts                createApp + router + import 三个 css（tokens→base→mall）
    ├── App.vue                <router-view />
    ├── env.d.ts               vite/client 类型 + *.vue 模块声明
    ├── router/index.ts
    ├── styles/
    │   ├── tokens.css         ★ 唯一换肤入口，零改动
    │   ├── base.css           零改动
    │   └── mall.css           仅删「ⓞ 演示外壳」区块
    ├── types/mall.ts
    ├── mock/
    │   ├── categories.ts  banners.ts  goods.ts  hotwords.ts
    │   └── session.ts
    ├── utils/
    │   ├── gradient.ts        grad()
    │   └── format.ts          trimNum() / priceParts() / tagClass()
    ├── composables/useCarousel.ts
    ├── components/
    │   ├── TopBar.vue  SearchBar.vue  CategoryGrid.vue
    │   ├── BannerCarousel.vue  UserPanel.vue  SiteFooter.vue
    │   ├── GoodsGrid.vue  GoodsCard.vue
    └── views/HomeView.vue     按基准顺序拼六个区块
```

---

## 四、旧 → 新 映射

| 样张来源 | 去处 | 备注 |
|---|---|---|
| `index.html` 的 `.demo-bar` | — | **删除** |
| `#demoToggle` + `initToggle()` | — | **删除** |
| `renderTopbar()` → `#topbarAccount` | `components/TopBar.vue` | 未登录/已登录两分支 `v-if` |
| `index.html` 的 `.topbar__nav` | `components/TopBar.vue` | 购物车 / 我的订单 |
| `initSearch()` → `#searchForm`/`#hotwords`/`#searchHint` | `components/SearchBar.vue` | 含热搜词点击回填 |
| `renderCats()` → `#catsGrid` | `components/CategoryGrid.vue` | 宫格 10 项 |
| `initCarousel()` + `buildSlide()` → `#carousel` | `components/BannerCarousel.vue` + `composables/useCarousel.ts` | 自动播放 / 箭头 / 圆点 / 悬停暂停 |
| `renderUserCard()` → `#usercard` | `components/UserPanel.vue` | 头像 / 问候 / CTA / 权益小格 |
| `renderGoods()` → `#goodsGrid` | `components/GoodsGrid.vue` + `components/GoodsCard.vue` | 单卡：角标 / 两行截断 / 价格三层 / 销量空值 |
| `priceNode()` | `GoodsCard.vue` template + `utils/format.ts` | |
| `tagClass()` | `utils/format.ts` | |
| `index.html` 的 `.foot` | `components/SiteFooter.vue` | |
| `grad()` | `utils/gradient.ts` | |
| `trimNum()` | `utils/format.ts` | |
| `data.js` 的 `MALL_HOTWORDS/CATS/BANNERS/GOODS` | `src/mock/*.ts` + `src/types/mall.ts` | 逐条照搬，含 `[探]` 边界注释 |
| `DEMO_LOGGED_IN` | `src/mock/session.ts` 的 `loggedIn` | 默认 `false` |

**数据一条不缩水**：10 分类 / 3 Banner / 10 商品 / 6 热搜词逐条照搬。
`data.js` 注释里的「[探] g1 超长名 / g2 无原价 / g3 双角标 / g4 销量 0 / g5 万+ / g6 两位小数 / g7 个位数价 / g8 最坏叠加」
逐条搬进 TS 文件 —— 那是刻意的内容边界基准，不是冗余注释。

---

## 五、零样式变换的兑现方式

1. CSS 用 `git mv` 搬运。`tokens.css` / `base.css` **零字节改动**；`mall.css` 仅删除「ⓞ 样张说明条 + 演示开关」区块
   的 7 个选择器（`.demo-bar`、`.demo-bar__inner`、`.demo-bar__dot`、`.demo-bar strong`、`.demo-bar__sep`、`.demo-toggle`、`.demo-toggle:hover`）
   及其区块注释，其余一字不动。
2. **DOM 契约不变**：标签层级、class 名、`aria-label`、`role`、`autocomplete`、`type` 等属性逐条照抄。
3. 组件**不写 `<style>`**；**不引** `element-plus/dist/index.css` 或任何 EP 主题 CSS。
4. 载入顺序保持 `tokens → base → mall`（与原 `<link>` 顺序一致）。
5. 动态样式改写：
   - `el.style.background = grad(...)` → `:style="{ background: grad(...) }"`
   - `dot.classList.toggle('is-active', ...)` → `:class="{ 'is-active': n === index }"`
   - `prev.style.display = 'none'`（仅 1 张时藏箭头）→ `v-show` / `v-if`
   - `label.style.color = hsl(...)` → `:style="{ color: ... }"`

### ⚠ 验证边界（诚实说明）
仓库规则只到「构建通过」，而**构建通过证明不了视觉零变换**。故额外做一次**静态核对**：
把旧 `main.js` 生成的节点与 class 清单，与新组件 template 逐条对照（见第六节对照表）。
这是本项目规则允许的最强手段，**不等于像素级确认** —— 要真看效果需使用者本机 `npm run dev`。

---

## 六、静态核对表（class / 属性契约）

| 区块 | 必须出现的 class（旧 → 新 一致） | 关键属性 |
|---|---|---|
| ① 顶栏 | `topbar` `topbar__inner` `topbar__account` `topbar__greet` `topbar__divider` `topbar__nav` `topbar__link` `topbar__icon` `topbar__count` `topbar__login` `topbar__reg` `topbar__logout` | `tnum`（购物车数字）；已登录分支用 `<b>` 包昵称 |
| ② 搜索 | `search` `search__box` `search__icon` `search__input` `search__btn` `search__hint` `search__hot` `search__hot-label` `search__hot-list` `sr-only` | `role="status"`（hint）、`autocomplete="off"`、`type="search"`、`<label for>` |
| ③ 分类 | `cats` `cats__grid` `cats__item` `cats__icon` `cats__name` | 图标渐变 `grad(hue,78,95,88)` |
| ④ 轮播 | `banner` `carousel` `carousel__viewport` `carousel__track` `carousel__slide` `carousel__kicker` `carousel__title` `carousel__sub` `carousel__cta` `carousel__arrow` `carousel__arrow--prev/--next` `carousel__dots` `carousel__dot` + `.is-active` | `aria-label="上一张/下一张/第 N 张"`；slide 渐变 `grad(hue,74,52,57,120)` |
| ⑤ 用户框 | `userbox` `usercard` `usercard__avatar` `usercard__main` `usercard__greet` `usercard__sub` `usercard__cta` `usercard__perks` `perk` `perk__num` `perk__num--muted` `perk__label` | 未登录分支 `perk__num--muted` |
| ⑥ 商品 | `hot` `section-head` `section-head__title` `section-head__more` `goods` `goods__item` `goods__thumb` `goods__thumb-label` `goods__tags` `goods__tag` `goods__tag--light` `goods__tag--neutral` `goods__body` `goods__name` `clamp-2` `goods__bottom` `goods__prices` `goods__price` `goods__price-sym` `goods__price-int` `goods__price-dec` `goods__origin` `goods__sales` | `tnum`；缩略图渐变 `grad(hue,60,91,82)`、标签色 `hsl(hue 42% 32%)` |
| — 页脚 | `foot` `foot__inner` `foot__sep` | |
| — 容器 | 每区块内层 `container` | 固定 1280px |

**行为契约**：轮播自动播放 5000ms、单张时藏箭头、悬停暂停、点圆点/箭头后重置计时；热搜词点击回填输入框并出提示；
表单 submit 只出提示不跳转；销量 `'0'` 显示「暂无成交」，否则 `{salesText}人付款`；价格去尾零（`1288.5` / `9.9` / `8999`）。

---

## 七、工具链

- **scripts**：`dev`(vite) / `build`(`vue-tsc --noEmit && vite build`) / `preview`(vite preview) / `type-check`(`vue-tsc --noEmit`)
- **tsconfig**：`strict: true`、`moduleResolution: "bundler"`、`target: ES2020`、`lib: [ES2020, DOM, DOM.Iterable]`、`noEmit: true`、`jsx: preserve`
- **依赖**：`vue ^3.5`、`vue-router ^4.5`、`element-plus ^2.9`（仅依赖，不引样式）；
  devDeps：`vite ^7`、`@vitejs/plugin-vue ^6`、`typescript`、`vue-tsc`（后两者由 npm 解析当前稳定版后记录进 `package.json`）
- **不装**：axios、pinia、@element-plus/icons-vue（首页无 EP 组件，图标全是内联 SVG）
- **路由**：`createWebHashHistory`；`/` → `HomeView`；`/:pathMatch(.*)*` → 重定向 `/`
- **vite proxy**：`/mall` → `http://localhost:8080`（按 `docs/contracts/mall-bff.md` 的网关前缀预留，当前无调用方）

---

## 八、文档同步

| 文件 | 改动 |
|---|---|
| 根 `CLAUDE.md` | 「mall 前台（用户端）视觉与结构约定」段：删「零构建静态页 / 双击 index.html」表述；**保留**色板唯一来源（`src/styles/tokens.css`）、风格不混用、六区块结构基准、只做宽屏、不做暗色、未登录固定形态、不引外部图片与字体；「样张先行」改写为「`frontend/mall` 工程自身即风格基准」；补一句 TS + EP 仅作依赖不引样式 |
| `frontend/README.md` | mall 行技术栈改 Vue3+Vite+TS；本地开发补 `cd mall && npm install && npm run dev`（5175）；删「不需要 npm / 双击」段；令牌路径改 `frontend/mall/src/styles/tokens.css` |
| `docs/contracts/mall-bff.md` | 「前端形态」行改为已完成的现状陈述 |
| `frontend/mall/README.md` | 整体重写为工程说明 |
| 检查器 | 提交前跑 `node docs/contracts/drift-check.mjs`，退出码须为 0 |

### 三处文字微调（内容仍静态，但字面写着「样张」）
1. `<title>`：`全景商城 · 前台（风格样张）` → `全景商城 · 前台`
2. 页脚：`全景商城 · 前台风格样张` → `全景商城 · 前台`
3. 搜索提示：`（样张：…）` → `（演示：…）`

---

## 九、验证与交付

1. `npm install` 成功，`package-lock.json` 生成并入库
2. `npm run build` 通过（含 `vue-tsc --noEmit` 类型检查）
3. 第六节静态核对表逐条走完（文本级核对，不跑页面）
4. `node docs/contracts/drift-check.mjs` 退出码 0
5. 不自动提交；完成后汇报 + 给 merge/PR 选项
