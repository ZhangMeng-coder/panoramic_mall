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
| 步骤 1 全站首页入口（TopBar + mall.css） | | | |
| 步骤 2 鉴权分级 + 登录门禁（2 yml + 守卫 + 拦截器 + 契约） | | | |
| 步骤 3 商品详情页（mall-bff 接口 + 前端页 + 契约） | | | |
| 全支评审 + 终局状态 | | | |

## 验收命令（三步共同，止步于编译/静态检查）

```bash
node docs/contracts/drift-check.mjs          # 契约一致性，退出码 0
cd frontend/mall && npm run type-check       # 前端类型门禁
mvn -pl mall-bff -am compile                 # 仅步骤 3（步骤 2 只动 yml，按全局规则跳过编译）
```

不启动服务、不打接口。
