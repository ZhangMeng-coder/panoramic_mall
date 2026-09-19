# mall 前台 · 首页入口 / 鉴权分级 / 商品详情（执行计划）

base commit: `53ee474`

设计（对话中已批准）要旨：

1. 全站首页入口 —— TopBar 右侧导航首位加「首页」（TopBar 在首页/列表页/账号页/新详情页都有，一处覆盖全站）。
2. 鉴权分级 —— 两侧白名单从 `/catalog/**` 收窄为 `/catalog/categories`：首页（分类宫格）公开，商品分页 / 筛选 / 详情需登录态。
3. 401 提示登录 —— 未登录进 `requiresAuth` 页由路由守卫拦到登录页并回跳；登录态中途失效由拦截器 401 兜底（`silent401` 旁路保住「过期 token 的游客逛公开首页不被弹走」）。
4. 商品详情 —— mall-bff 新增 `GET /catalog/goods/{id}`，调两个**已有** store 内部接口（不新增域接口），可见性口径 = 列表口径。
5. 首页 mock（⑥ 热门商品）原样不动，不链详情。

## 任务 ledger

| 任务 | commit | 裁决 | 残留 |
|---|---|---|---|
| 步骤 1 全站首页入口（TopBar + mall.css） | 3074366 | 落在顶栏右侧导航首位（顶栏四页都有，一处覆盖全站）；当前页高亮**显式判 `route.path === '/'`**——`router-link-active` 会把 `/` 当成所有路径的前缀，高亮跟着跑到每一页 | 无 |
| 步骤 2 鉴权分级 + 登录门禁（2 yml + 守卫 + 拦截器 + 契约） | 531d9fa | ① 白名单由 `/catalog/**` 收窄为 `/catalog/categories` **精确路径**（写成前缀会把商品查询与详情一起放开到公网）；② `AUTH_PATHS` 落叶子模块 `router/paths.ts`，断 `router → api → request → router` 的循环 import；③ `silent401` **只给**守卫重建用户态的 `/auth/me`（过期 token 的游客逛公开首页不该被弹走），跳转用 `window.location.hash` 而非 import router；④ 并发 401 靠「清态后 token 为空」天然去重，不引额外状态 | 无 |
| 步骤 3 商品详情页（mall-bff 接口 + 前端页 + 契约） | 7859a0d | ① 详情走 store **已有的** platform 侧方法，不新增域接口；② 可见性在 BFF **读时重判**（与列表同一不变量），不可见 4 种情形一律业务 404、不区分原因；③ **只有业务 4xx 转 404**——熔断/连接降级是 500，照抛（否则下游一抖就被伪装成「已下架」）；④ 域出参是管理端超集，逐字段手工映射裁剪；⑤ 描述**纯文本**渲染（不 v-html）；⑥ **不做**加购/立即买（后端没有对应接口）；⑦ 首页 mock 商品不链详情（没有真实 id） | 无 |
| 全支评审修复 | 6773322 | 评审抓出 1 处必修：**守卫的静默 401 交接断了**——`/auth/me`（silent401）先清本地 token，同导航内业务接口再 401 时拦截器判不出「本来有登录态」→ 需登录页被报成「商品暂不可用」。修法：守卫自己收口（不动 `request.ts`）。⚠ 复评又抓出收紧项：只判 `requiresAuth` 会在网络/5xx 失败（token 未动）时与「已登录不该待在登录页」**成环**，判据定为 `requiresAuth && !getToken()`。同提交并修两处遗漏计数（`docs/contracts/README.md` / `backend/mall-bff/README.md` 的 8 → 9） | 无 |

**终局**：5 提交在 `53ee474` 之上（3074366 / 531d9fa / 7859a0d / 6773322 + 本提交）。三件需求 + 详情页全部落地：全站顶栏首页入口、白名单收窄到 `/catalog/categories` 精确路径、401 提示并跳登录页（带回跳）、`GET /catalog/goods/{id}` 与 `/goods/:id`。验证止步于编译/静态检查：`node docs/contracts/drift-check.mjs` 退出码 0（mall-bff 9/9、两侧白名单互为子集）、`npm run type-check` 0、`npm run build` 通过、`mvn -pl mall-bff -am compile` 0。未启动服务、未打接口。**残留：无**。

## 验收命令（三步共同，止步于编译/静态检查）

```bash
node docs/contracts/drift-check.mjs          # 契约一致性，退出码 0
cd frontend/mall && npm run type-check       # 前端类型门禁
mvn -pl mall-bff -am compile                 # 仅步骤 3（步骤 2 只动 yml，按全局规则跳过编译）
```

不启动服务、不打接口。
