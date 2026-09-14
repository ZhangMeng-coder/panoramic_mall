# mall 前台接入 mall-bff 账号接口（设计）

> 目标：把 `backend/mall-bff` 的 5 条 C 端账号接口接到 `frontend/mall` 页面上——
> 取码 / 注册 / 登录 / 登出 / 当前顾客，替换掉现在写死的假登录态。
> 首页六个区块的数据**保持静态**（`src/mock/`），首页聚合是 mall-bff 二期。

## 一、目标与非目标

### 目标

1. 新建请求层（axios 实例 + 拦截器）与会话存储（token 持久化 + user 内存驻留 + 刷新重建）。
2. 新增 `/login`、`/register` 两个页面，**C 端橙红风格、零 Element Plus 组件**。
3. 顶栏（①）与用户信息框（⑤）改读真实登录态；「请登录 / 免费注册 / 去登录」变真跳转。
4. 提供**非 EP** 的错误提示通道（自写极简提示条），供表单错误与接口报错共用。
5. 同一次改动内同步契约与文档。

### 硬限定（用户明确）

- **登录 / 注册是独立路由页**：`/login`、`/register`，各一条路由，不用弹层。
- **错误提示自写极简提示条**，不引 Element Plus 的 `ElMessage`（mall 禁用 EP）。
- **已登录时的优惠券 / 积分 / 收藏一律显示 `0`**，会员等级文案不得编造权益（接口二期才有）。

### 非目标（YAGNI）

- ❌ 首页六区块**不接接口**，数据仍在 `src/mock/`（二期）。
- ❌ 「购物车 / 我的订单」仍是死链（后端没有对应接口）。
- ❌ 路由守卫**不加** `meta.requiresAuth`：现在没有任何「必须登录才能进」的页。
  将来出现这种页时再加该分支（spec 里记一笔，避免下次忘记守卫为什么这么短）。
- ❌ 不做记住我 / 自动续期 / 多标签页同步登出（无对应后端能力）。
- ❌ 不做短信倒计时的服务端节流（后端取码不落 Redis、无频控）。

## 二、已确认决策

| 决策点 | 结论 | 理由 |
|---|---|---|
| 页面形态 | 独立路由页 + 复用首页顶栏与页脚 | CLAUDE.md：「新增页面的顶栏 / 页脚沿用同一套」 |
| 错误通道 | 自写提示条（`useToast` + `ToastHost`） | mall 禁 EP，`ElMessage` 用不了 |
| 已登录 perks | 全 `0` | 不编造不存在的权益数据 |
| 请求库 | **axios**（新增依赖 `^1.8.0`） | 与 `frontend/store`、`frontend/admin` 的请求层同构，维护者一眼看懂 |
| token 键 | `localStorage['pm-mall-token']` | 与 `pm-store-token` / `pm-admin-theme` 命名同源 |
| 首页是否要登录 | **不要**，游客可逛 | C 端商城默认；守卫因此不拦公开页 |

## 三、分层与链路

```
页面（5175/mall 前台）
  └─ vite dev proxy: /mall  →  网关 8080（StripPrefix=1） → mall-bff 8085
      取码   POST /mall/auth/sms-code   （免鉴权，登录前调用）
      注册   POST /mall/auth/register   （免鉴权，注册即登录）
      登录   POST /mall/auth/login      （免鉴权）
      登出   POST /mall/auth/logout     （带 Bearer）
      当前   GET  /mall/auth/me         （带 Bearer）
```

- 路径**只照 `docs/contracts/mall-bff.md` 写**，不照后端代码写（契约表是唯一裁决点）。
- `vite.config.ts` 的 `/mall` 代理**已就位，不改**。
- 对外接口必包 `RespData{code,msg,data}`，`code===200` 为成功。

## 四、新增文件（10）

| 文件 | 职责 |
|---|---|
| `src/api/request.ts` | axios 实例 + 请求/响应拦截器 |
| `src/api/auth.ts` | 5 条账号接口 |
| `src/store/auth.ts` | 会话存储（token / user / clear） |
| `src/types/auth.ts` | `CurrentUser`、`LoginResult` 类型 |
| `src/views/LoginView.vue` | 登录页 |
| `src/views/RegisterView.vue` | 注册页 |
| `src/components/AccountShell.vue` | 登录 / 注册共用的外壳：顶栏 + 居中卡片 + 页脚 |
| `src/components/ToastHost.vue` | 提示条容器（挂 `App.vue`） |
| `src/composables/useToast.ts` | `showToast(msg, type)`，模块级队列 |
| `src/styles/account.css` | 账号页 + 提示条样式（只消费 `tokens.css` 令牌） |

### 改动文件（7）

| 文件 | 改动 |
|---|---|
| `package.json` | 加 `axios: ^1.8.0` |
| `src/main.ts` | 末尾加 `import './styles/account.css'`（载入顺序变 4 个） |
| `src/App.vue` | `<router-view />` 后挂 `<ToastHost />` |
| `src/router/index.ts` | 加 `/login`、`/register` 路由 + 全局守卫 |
| `src/components/TopBar.vue` | 登录态改读真实会话；链接改 `router-link`；加真实退出 |
| `src/components/UserPanel.vue` | 同上；perks 全 0；副文案改写 |
| `src/utils/format.ts` | 加 `maskPhone()`（手机号打码 `138****8000`） |

### 删除文件（1）

| 文件 | 原因 |
|---|---|
| `src/mock/session.ts` | 唯一用途是写死 `loggedIn` 的假登录态，被真实会话取代后就是第二个真相源，必然与真态打架 |

## 五、请求层口径

### 请求拦截

有 token 时挂 `Authorization: Bearer <token>`。

### 响应拦截

| 情况 | 行为 |
|---|---|
| `code === 200` | 返回 `res.data`（**解包后的业务数据**，调用方拿不到 `RespData` 外壳） |
| `code === 401` | 清本地态 → 抛 `Error('登录已失效，请重新登录')`；**不弹提示条**（见下「实现期定稿」） |
| `code === 403` | 弹提示条 → 抛错 |
| 其它 code | **弹后端 `msg` 原文**（如「验证码错误」「手机号已注册」「手机号未注册」）→ 抛错 |
| 真实 HTTP 401 | 同 `code === 401` |
| 真实 HTTP 403 | 弹「无权限执行该操作」 |
| 其它 HTTP 错误 | 弹 `body.msg` 或「请求失败（HTTP xxx）」 |
| 无响应（网络/超时） | 弹「网络异常，请稍后重试」 |

### ⚠ 与 store-bff 端的有意差异（必须写进代码注释）

**401 时拦截器只清本地态 + 抛错，不自己跳登录页。**

`frontend/store` 的 `request.js` 里 401 会直接改 `window.location.hash` 跳登录页——那是对的，
因为店主端每个页面都要登录。**mall 首页是公开页**：一个带过期 token 的游客逛首页，
不该被弹去登录页。跳不跳由**守卫 / 调用方**决定，拦截器不擅自动 URL。

注释里写明这条差异，防止将来有人「照 store 修一遍」。

**实现期定稿（补一处原设计没写死的地方）**：401 **连提示条也不弹**，只清态 + 抛错。

原设计第 一.4 条说的是「提示条供表单错误与会话失效共用」，落到实现上会让「带过期 token 的游客每次打开首页」
都被弹一句「登录已失效，请重新登录」——**首页是公开页，这是纯噪音**。所以定稿为：
**401 静默清态**（顶栏自己变成「请登录」，这本身就是反馈），抛出的 `error.message` 留给调用方；
真需要提示的动作（将来的「需要登录才能做」的操作）catch 后自行 `showToast(error.message)` 即可。
403 / 其它业务失败仍由拦截器弹后端 `msg` 原文。

## 六、会话存储（`src/store/auth.ts`）

照 `frontend/store/src/store/auth.js` 的轻量模块模式（**不引 pinia**）：

- `token`：`ref`，初值取 `localStorage['pm-mall-token']`；`setToken()` 同步写 / 删 localStorage。
- `user`：`ref<CurrentUser | null>`，**只驻留内存**；刷新后由守卫拉 `/auth/me` 重建。
- `getToken` / `setToken` / `getUser` / `setUser` / `clearAuth`。

不存 `defaultPath`（store 有是因为它登录后要进主页；mall 登录后回来源页或首页，逻辑更短）。

## 七、路由与守卫

新增两条路由（都在 `HomeView` 之外，不套 `AccountShell` 之外的布局）：

```ts
{ path: '/login',    name: 'login',    component: () => import('../views/LoginView.vue'),    meta: { title: '登录' } }
{ path: '/register', name: 'register', component: () => import('../views/RegisterView.vue'), meta: { title: '注册' } }
```

守卫（`router.beforeEach`）只有两条规则，**不拦公开页**：

1. 已登录（有 token）却访问 `/login` 或 `/register` → 回 `?redirect=` 指向的页，没有则回 `/`。
2. 有 token 但内存无 user（**刷新场景**）→ 拉 `authApi.me()` 重建；
   失败**不跳登录页**（拦截器已清态），照常进入目标公开页。

登录 / 注册成功后：`router.replace(接 query.redirect 或 '/')`。

## 八、页面口径

### 登录页（`/login`）

- 字段：手机号、验证码（「获取验证码」按钮带 **60s 本地倒计时**，倒计时中禁用防连点）。
- 「获取验证码」调 `sendSmsCode`；前端先校验手机号格式，不过则不请求。
- 「登录」调 `login` → 存 token + user → 提示条「登录成功」→ 回跳。
- 底部：「还没账号？<免费注册>」→ `router-link` 到 `/register`。
- 回车提交。

### 注册页（`/register`）

- 字段：手机号、验证码（同样倒计时）、昵称（**选填**，≤50）。
- 「注册」调 `register`（**注册即登录**，后端返回 `{token, user}`）→ 存态 → 提示条「注册成功，已自动登录」→ 回跳。
- 底部：「已有账号？<直接登录>」→ `router-link` 到 `/login`。

### 前端校验口径（**只是快速反馈，不是防线**）

| 字段 | 规则 | 与后端一致处 |
|---|---|---|
| 手机号 | `^1[3-9]\d{9}$` | `SmsCodeDTO` / `RegisterDTO` / `LoginDTO` 的 `@Pattern` |
| 验证码 | 非空 | `@NotBlank` |
| 昵称 | 长度 ≤ 50 | `RegisterDTO` 的 `@Size(max = 50)` |

后端照旧全量校验；前端不做任何「替后端挡」的假设。

### ⚠ 演示提示行（用户已同意）

验证码框下方固定一行小字：

> 演示环境：短信为模拟通道，验证码固定 **888888**

理由：契约规定取码接口**只写一行日志、不发真实短信**，校验与固定码比对。
不加这行则页面上**没有任何途径得知验证码**，注册 / 登录实际不可用。
这是演示环境的必要说明，**不是假功能**。

### 顶栏（`TopBar.vue`）

| 态 | 左 | 右 |
|---|---|---|
| 未登录 | `你好，<a>请登录</a>` + 分隔 + `<a>免费注册</a>`（`router-link`） | 购物车 / 我的订单（不变，仍死链） |
| 已登录 | `Hi，<b>{user.nickname}</b>` + 分隔 + `<a>退出</a>` | 同上 |

- 昵称取 `user.nickname`（后端建号时昵称为空则回落手机号，前端不兜底、直接用）。
- 「退出」：调 `logout`（**接口失败也照样本地清态**，token 已失效时服务端本就没得清）→ 提示条「已退出登录」→ 留在当前页（未登录态即时生效）。

### 用户信息框（`UserPanel.vue`）

| 态 | 头像 | 问候 | 副文案 | CTA | perks |
|---|---|---|---|---|---|
| 未登录 | 👋 | 你好，游客 | 登录后享会员价、查看订单与优惠券，新人还能领 188 元礼包（**不变**） | 去登录 → `/login` | 全 0，`perk__num--muted` |
| 已登录 | 🙂 | 下午好，**{nickname}** | `手机号 {maskPhone(phone)} · 会员权益数据待后续开放` | 查看我的订单（死链，不变） | 全 0，正常色 |

- `maskPhone()` 加到 `src/utils/format.ts`（该文件已是价格 / 角标的格式化归口）：`138****8000`。
- perks 数字全 0 是刻意的（不编造权益）；未登录态用 `--muted` 灰化、已登录态正常色，
  两态仍可区分。

## 九、提示条（`useToast` + `ToastHost`）

- `useToast.ts`：模块级 `ref<Toast[]>`（**不依赖组件实例**，可在拦截器里直接调），
  `showToast(msg, type: 'error' | 'success' | 'info' = 'error')`，`id` 自增，2.5s 后自动移除。
- `ToastHost.vue`：`position: fixed` 顶部居中、`z-index` 高于顶栏，`v-for` 渲染，`<TransitionGroup>` 进出。
- 样式在 `account.css` 里，**只消费 `tokens.css`**：error 用 `--danger`、success 用 `--success`、
  info 用 `--n600`；圆角 `--r-md`、阴影 `--sh-lg`、字号 `--t-sm`。**零 EP**。
- 拦截器对「业务失败」直接弹后端 `msg` 原文——后端已有中文提示，前端不再包一层。

## 十、样式口径

- 新增 `src/styles/account.css`：账号页（卡片 / 表单 / 输入 / 主按钮 / 倒计时按钮 / 互链）+ 提示条。
- **只消费 `tokens.css` 令牌**：`--brand` / `--brand-hover` / `--brand-soft` / `--n*` / `--r-*` /
  `--sh-*` / `--t-*` / `--s-*` / `--container`。**禁止硬编码色值 / 圆角 / 阴影。**
- 沿用前台既有视觉语言：输入框圆角照 `.search__box`（胶囊 / `--r-md`）、主按钮照 `.search__btn` 的
  `--brand` 底 + hover/active 三态，卡片照 `.usercard` 的白底 + `--sh-md`。
- **只做宽屏**：容器 `--container`（1280px），**不写媒体查询**。
- **不做暗色模式**。
- `main.ts` 载入顺序：令牌 → 基线 → 区块（`mall.css`）→ 账号页（`account.css`）。

## 十一、契约与文档同步（同一次改动内）

| 文件 | 改什么 |
|---|---|
| `docs/contracts/mall-bff.md` | 第三节末句「前端当前首页仍全部静态写死、不发起请求」→ 账号 5 条已接入、首页数据仍静态 |
| `frontend/mall/README.md` | 头部「不发起任何接口请求」、文件结构（新增 `api/` `store/` `types/auth.ts` `views/LoginView`…）、「登录态」段（假的 `loggedIn` 常量 → 真实会话 + 刷新重建）、「明确不做的事」里「不接任何接口」改为「首页数据仍不接接口，账号已接入」 |
| `frontend/README.md` | mall 行：后端 5 条接口**已接入账号页**，页面不再「尚未接入」 |
| `CLAUDE.md`（根） | 「mall 前台」段首句「页面内容当前全部静态写死（数据在 `src/mock/`）、不发起任何请求」→ 首页数据仍静态、**账号 5 条已接入**；并把「登录态由 `mock/session.ts` 的 `loggedIn` 常量控制」一段替换为真实会话说明；补一句「401 拦截器不擅自跳转」的约定 |
| `README.md`（根） | 已实现功能：mall 前台条目补「已接入 mall-bff 账号接口」；开发路线勾掉「`frontend/mall` 接入接口」中属于账号的部分（首页聚合仍留二期） |

⚠ **`docs/contracts/mall-bff.md` 的接口表本身不改**：接口没变，只是有了消费方。表里 5 行保持原样。

## 十二、验证与交付

按仓库「验证止步于编译通过」：

- ✅ `cd frontend/mall && npm run build` —— 即 `vue-tsc --noEmit && vite build`，**类型不过即失败**
- ✅ `node docs/contracts/drift-check.mjs` —— 退出码 0（提交前必跑）
- ✅ 文本级核对：新增 / 改动文件里**零 `element-plus` 引用**、`account.css` **零硬编码色值**、
  `mock/session.ts` **无残留引用**（`grep -r "mock/session"` 为空）
- ❌ **不**启动 dev / preview、**不**打任何接口、**不**跑 `mvn test`
- ⚠ `npm install`（为 axios 加依赖）属于装依赖，不受限；若离线装不上，改用原生 `fetch`
  重写 `request.ts`（**同一份对外接口不变**）并记录裁决

## 十三、风险

| 风险 | 处置 |
|---|---|
| axios 装不上（离线） | 回退 `fetch` 实现，对外函数签名不变，记录裁决 |
| 后端 `RespData` 成功码不是 200 | 契约 README 明确「成功 `code=200`」，且 store 端同样按 200 解包，风险低 |
| 刷新后 `/auth/me` 失败被误当成未登录 | 守卫只清态、不跳转；游客形态本就是这个页面的合法态 |
| 两页顶栏 / 页脚各写一遍导致漂移 | 抽 `AccountShell.vue`，两页只写卡片内容 |
