# 项目约定（Panoramic Mall）

## 代码生成与分层约定

生成/修改 Java 服务层代码时，一律遵循以下规则（admin 与 goods-center 均已按此重构）：

- **CRUD 交给 MyBatis-Plus 基类**：每个实体有独立 service，接口 `extends IService<T>`、实现 `extends ServiceImpl<XxxMapper, Xxx>`。own-entity 的增删改查（`save`/`updateById`/`removeById`/`getById`/`list`/`page`/`count` 等）直接用基类内置方法，能用组件实现就不新写逻辑、不直接用 own Mapper。纯关联表（如 `sys_user_role`、`sys_role_permission`）也要建 service。
- **审计字段交给自动填充，禁止手写赋值**：所有业务实体一律 `extends BaseEntity`，`create_user/update_user/create_time/update_time` 由 common 的 `MyMetaObjectHandler` 经 `UserContext` 自动填充，代码**不得**显式赋值（不得出现 `setCreateUser/setUpdateUser/setCreateTime/setUpdateTime`），也不得绕过 `save/updateById` 等触发填充的 MP 基类方法。纯关联表（无审计列、物理删除，如 `sys_user_role`/`sys_role_permission`）除外，维持现状。
  - **审计值格式为 `UserType:UserId` 字符串**（如 `admin:1` / `store:7`）：多端身份空间（admin/store/user）下同一 id 可能指向不同的人，带类型前缀消歧。故 `BaseEntity.createUser/updateUser` 是 **`String`**，数据库列为 `VARCHAR(32)`（不是 INT）；取不到 userId 时留空，类型缺失时由 `UserContext.getUserType()` 回退 `admin`。
- **跨实体只走 owner service**：Service 内禁止直接持有/调用其他实体的 Mapper；要读写别的实体，必须调用该实体自己的 service（对方缺能力时先在对方 service 上加方法再回来调）。
- **内容归其所属实体的 controller**：不局限在实体 controller 里查询无关内容——要查什么内容，就调什么实体的 controller/service。例：不要在 RoleController/RoleService 里查 User 相关内容。
- **数据验证层的循环引用**用 Spring `@Lazy` 断环（需在模块 `lombok.config` 加 `lombok.copyableAnnotations += org.springframework.context.annotation.Lazy` 使其进入构造参数）。
- **歧义先问**：规则适用或归属有歧义时，先向用户确认，不要自己判断。

## 分层与内部服务调用（BFF 化，进行中）

目标分层：**前端页面只经网关访问"端 BFF"**（admin / store-bff / mall-bff）；**业务域服务不向页面暴露公网路由，只由 BFF 经 Feign 内部调用**。生成/修改代码时按此归属：页面聚合/编排 → BFF；数据归属与领域能力 → 域服务；不得把实体/表复制进 BFF。⚠ 网关公网入口已收敛为**端 BFF 白名单**（`gateway` 配置 `panoramic.gateway.bff-services`，由 `BffRouteGuardFilter` 强制校验，名单外服务经网关一律 403）：当前为 **`admin` / `store-bff` / `mall-bff`**（store 域已拆出 store-bff 下沉纯域、不再对外）；goods-center 同样已下沉纯域、不开放公网路由。trade-center 仍属后续待建项（todo.md）。新代码一律按目标分层写，不延续直连、不给域服务开公网路由。

**模块归属（鉴权装配层已独立成模块）**：`common`=纯基座（`RespData`/`BaseEntity`/异常/分页/`LoginUser`/`UserContext`/`MyMetaObjectHandler`/Feign 契约）；`common-auth`=鉴权装配层（`SecurityConfig`/`AuthTokenFilter`/`JwtService`/`LoginUserCacheService`，带 Redis 与 JJWT）。**只有端 BFF 依赖 `common-auth`**；业务域（goods-center / store）只依赖 `common`，结构上拿不到认证链与 Redis，因此不装配鉴权、不需要 `datasource-redis.yml` / `auth.yml`。新增需要鉴权/Redis 的公共类，放 `common-auth`；只被业务代码共用的放 `common`。

**Feign 内部接口规约（BFF → 域，M0 起一律遵守）**：
- **熔断**：经 Feign 调业务域必须配熔断器，下游故障不得拖垮调用方（编排接口降级/快速失败）。
  - **⚠ 业务 4xx 不得计入熔断失败率（2026-09-10 修正）**：域内业务校验失败（如「已上架 SKU 不可修改」「商品不存在」）经 `common` 的 `InternalApiErrorDecoder` 还原为 `ServiceException`，属**调用方语义/参数错误**，不是下游健康度信号。端 BFF 的 `resilience4j.circuitbreaker.configs.default` 必须配 `ignore-exceptions: [com.panoramic.common.exception.ServiceException]`，否则店主连续几次操作失误就会打开熔断，把后续**正常**请求也降级成 500「…暂不可用」。
  - **4xx/5xx 的分野在 `InternalApiErrorDecoder`，按 HTTP 状态码划分**：**4xx** → `ServiceException`（熔断忽略，端 BFF 原样透传给页面）；**5xx** → 回落 `Default()` 产出 `FeignException`（**照常计入失败率**，下游故障保护不变）。⚠ 域内兜底 `@ExceptionHandler(Exception.class)` 返回的正是 **HTTP 500 + `{code,msg}`**——**5xx 绝不能也还原成 `ServiceException`**，否则会连真故障一起被忽略，熔断永远不打开。新增域的兜底异常处理时须保持这个形状。
  - **异常剥壳 + 降级一律走 `common` 的 `BffFeignCall`（2026-09-12）**：端 BFF 调域**不要**再各写一份 `call(Supplier)`——统一用 `BffFeignCall.call(下游名, 降级文案, action)`：沿 cause 链剥开熔断/异步包装找出原始 `ServiceException`，**400/403/404 原样透传**（由统一异常处理还原给页面），其余（熔断/连接/序列化/其它业务码）打日志后降级为各端自己的「…暂不可用，请稍后重试」。
- **公共类型**：Feign interface 的入参/出参 DTO 在 common 维护（与接口同源），调用方与被调用方引用**同一份类型**，禁止各自复制一份导致漂移。
- **不包 RespData**：内部 Feign 方法**直接返回业务结果类型**（`Xxx`/`List<Xxx>`/`boolean`…），错误走异常/统一处理传播；RespData（`{code,msg,data}`）仅用于对外页面/网关接口。
- **信任与防线（鉴权与权限判定全部收敛在端 BFF）**：**域服务不做任何鉴权、不做任何权限判断、不校验 token**。内部调用只透传身份头 `X-User-Id` / `X-User-Type`（网关注入 → 端 BFF 经 Feign 原样转发），域服务把它直取填 `UserContext`，**仅用于两件事**：审计字段自动填充（`UserType:UserId`）与 `audit_by` 留痕——读 ≠ 判断，读身份不等于做鉴权。端 BFF 的 `@PreAuthorize` 是唯一授权点，其各操作权限串与域接口一一对应（goods:brand/category/spu 的 list/add/edit/delete）。⚠ **不再有内部令牌 `X-Internal-Token`**（已删除）：域端口只在内网可达是前提，否则可伪造 `X-User-Id`——防线在网络层，不在应用层。
  - **缺头即不填充、不拦截**：域内身份过滤器（`GoodsUserIdentityFilter` / `StoreUserIdentityFilter`）在缺 `X-User-Id` 时直接放行（审计留空），**不得回 401**——那等于在域内做鉴权。
- **store 域的数据权限（D5，已按新边界改写）**：store 采用 **store_id 通用数据权限适配**：域内不持 store_user，owner 侧方法带 `store_id` 参数、只作用于「id==store_id 的店」（**账号店同 ID**，D4，store_shop 主键==店主账号 id、无 owner_user_id 列，店主归属收敛在 store-bff）；platform 侧方法不带 store_id、全量。**owner/platform 的分流由「端 BFF 调哪一侧接口」决定，不由 `X-User-Type` 在域内判断**（`assertOwner`/`requirePlatformAdmin` 之类的域内断言已删除）；store-bff 从登录态取 store_id 传给域，admin 走 platform 侧并由 `@PreAuthorize` 把关。`audit_by` 直取 X-User-Id 仅记录，不与平台账号联查（D6）。新增 store 域方法时按「作用对象表是否带 store_id 列 + 调用意图」决定套 owner(限 store_id)/platform(全量) 哪一侧。
  - **store 域现有 owner 侧能力**：店铺 `store_shop`（mine/save/submit）与店主在售商品 `store_goods_spu`/`store_goods_sku`（`/internal/store/goods/**`，带 `storeId` 且按该列过滤；SKU 经 `spuId` 归属，不再单带 store_id）。**「店铺已审核通过」的门禁不在域内**（域不查店铺状态），由 store-bff 调域前判定并回 `403`。
  - **store 域现有 platform 侧能力（2026-09-12 新增）**：`/internal/store/goods/platform/spu/{page,{id}}` 跨店全量分页与详情（**不带 store_id**，`categoryIds` 多值过滤、回填 `storeName`/`skuCount`）与 `/platform/spu/{id}/lock|unlock`（平台锁定/解锁），供 admin BFF 的「店铺商品管理」编排，权限由 admin 的 `@PreAuthorize store:goods:list|lock` 把关；另有 `/internal/store/shops/options`（店铺下拉）。**平台分页走 `POST + @RequestBody`**（`categoryIds` 是集合，规避 Feign `@SpringQueryMap` 的集合序列化口径问题）。
  - **上下架是推导量、由域内单一写者维护**：`StoreGoodsSpuServiceImpl#refreshShelfStatus` 是 `SPU上架 ⟺ ≥1 SKU 上架` 的唯一写者（上架任一 SKU → SPU 上架；SKU 全下架 → SPU 下架），前端不得直接传 SPU 上下架；已上架 SKU 锁定其规格/价格（须先下架才能改/删），存在上架 SKU 时 SPU 规格配置只读、SPU 不可删除。
  - **中台版本比对属编排职责（store-bff 做）**：域只落库/回读 `center_version` 与商品字段，不调中台、不判版本；「更新提示 + 同步覆盖」（覆盖与否由店主决定、不阻断保存）在 store-bff 详情编排里组装。
  - **平台锁定（R12，2026-09-12）**：`store_goods_spu` 的 `lock_status/lock_reason/lock_user/lock_time` 四列即锁定态（不建独立锁定表）。**锁定 = 名下已上架 SKU 级联下架 → 由 `refreshShelfStatus` 推导 SPU 下架**（不变量仍是唯一写者，绝不直接改 `shelf_status`）；**锁定期 owner 侧整行只读**（编辑/删除/改 SKU/上下架一律拒绝，`assertNotLocked` 域内强制，不只靠前端禁用按钮）；**解锁只清锁定字段、不恢复上架**（店主手动重上）；锁定写入用条件更新（`where lock_status=0`）防并发重复锁定，解锁必须 `lambdaUpdate().set(col, null)` 显式清（`updateById` 跳过 null）。`lock_user` 是**业务列**（D7，可在 service 内显式写入），存审计同格式 `UserType:UserId`（如 `admin:1`）；**商户端不展示锁定人**，仅管理端展示。
  - **跨域「分类全路径」由端 BFF 读时解析（2026-09-12）**：域只存「分类 id 引用 + 名称快照」，**不持分类表、不解析路径**；端 BFF 读时按页内去重后的 `categoryId` 批量调 goods-center `/categories/paths` 补 `categoryPath`（一次调用，非 N+1），**解析失败只告警、路径留空**，前端回退快照名（展示增强不得拖垮主流程）。分类**子树匹配**同理：前端只传单个 `categoryId`，由端 BFF 用分类树展开成「该节点 + 全部后代」的 `categoryIds` 再传域（域只做 `IN`）。

**鉴权与登录态（各端各管各的）**：每个端 BFF 只提供**自己身份**的登录接口（admin → `/auth/login` 签 `type=admin`；store-bff → `/auth/register|login` 签 `type=store`；mall-bff → `/auth/sms-code|register|login` 签 `type=user`）。三端共用同一把 `jwt-secret` 与同一套 `type` claim 契约（`LoginUser.CLAIM_USER_TYPE`），但**登录用户模型与 Redis 键命名空间按身份隔离**：

- **⚠ 端 BFF 未必都接 RBAC**：**只有 admin（平台管理员）有 RBAC**，其 `@PreAuthorize` 是唯一授权点。**store-bff（店主）与 mall-bff（C 端顾客）都没有、也不应有任何 `@PreAuthorize`**——店主登录后对自己店全权限、顾客对自己的数据全权限，**这是预期状态，不是漏登记**。新增 BFF 端时按「该端是否有角色/权限维度」决定，不要把「controller 里没有 `@PreAuthorize`」当成缺陷去补。

- **Redis 登录态键 = `panoramic:login:{userType}:{userId}`**（如 `panoramic:login:admin:1` / `panoramic:login:store:7` / `panoramic:login:user:42`）。前缀由 Nacos `auth.yml` 的 `panoramic.auth.redis-prefix`（= `panoramic:login`）提供，网关按 JWT 的 `type` claim 拼同一把键校验——**键格式是网关 ↔ 端 BFF 的共享契约，不可单边改动**。
- 网关只做「验签 + 查登录态 + 注入 `X-User-Id`/`X-User-Type`」，不做身份类型判断；身份类型由签发的 `type` claim 携带、全链路透传。
- 域服务不参与登录态（无 Redis），见上「信任与防线」。

**术语防呆（避免跨域加错表）**：`goods-center`=标准商品模板库（标准商品平台）；"店铺在售商品/库存/信誉"属 store 域；"顾客/购物车/订单/评价"属未来 trade 域。别把别域实体塞进 goods-center。

## 对外契约清单（docs/contracts）

**所有服务的对外契约统一登记在 [`docs/contracts/`](docs/contracts/README.md)**，不在各模块 README 或本文件里另立一份。三层：① 页面级（`admin.md` / `store-bff.md` / `mall-bff.md`）② 内部 Feign（`goods-center.md` / `store.md` / `trade-center.md`，后两者中 `trade-center.md` 待建）③ 跨服务隐式（`cross-cutting.md`，共 15 条），外加基础设施（`gateway.md`）。

⚠ **检查器的端 BFF 名单不硬编码**：`drift-check.mjs` 从网关 `bff-services` 的值推导服务名单、从路由（`uri: lb://<svc>` ↔ `Path=/<前缀>/**`）推导前缀，进而逐个核对「两侧白名单互为子集」；某个端 BFF 推不出唯一前缀即**直接失败**（不降级为警告）。新增端 BFF 时不要回头去改检查器的名单。

**⚠ 契约表是页面契约的唯一裁决点：写/改前端时只照表写，不照后端代码写。** 表里「路径 / 方法 / 权限串 / 入出参类型」即全部契约；字段定义去 `common` 的 DTO 类看，表里**不抄字段**（抄一份就是制造第二个会漂移的地方）。

生成/修改代码时遵守三条硬规则：

1. **改任何对外接口**（增删改路径 / 方法 / 权限串 / 入出参类型）时，**同一改动内**更新对应 `<服务>.md`。只改代码不改契约表，视为未完成。
2. **契约先行**：接口可以先定契约、后写实现——契约先行写下的行，把「状态」列填 **`待实现`**（留空即「已实现」）；**实现完成后同一改动内把该列摘回留空**，不摘检查器会报错（反向哨兵，防标记烂掉后这张表开始骗人）。`待实现` 行只校验路径/方法/权限串的写法，**不查**入出参类型是否存在、权限串是否已在种子里——契约先行时那些同样还没写，查了契约就落不了盘。前端可以照 `待实现` 的行先把页面写起来。
3. **改跨服务隐式契约**（响应形状、身份头、Redis 键、熔断 4xx/5xx 分野、Nacos 加载矩阵、权限串与路由一致性等）时，**必须**同步更新 `docs/contracts/cross-cutting.md`——这些约定任何一方单边改动都不会编译报错，只会在运行时静默断链，所以只能靠登记 + 核对。
4. **提交前跑一次检查器**，差集非空不得提交：`node docs/contracts/drift-check.mjs`（退出码 0 = 一致；非 0 按输出逐条修正）。

**文件专项专用**：模块 README 只写服务说明（职责 / 架构位置 / 实体标记 / 边界），❌ 不列接口清单；契约文件只写接口与形状，❌ 不写业务规则散文；`db/*.sql` 只写表结构与种子。一个文件只有一个职责，同一内容不写两遍。

## 前端三端统一技术形态（2026-09-14 拉平）

`frontend/` 下三端（`admin` / `store` / `mall`）**技术形态一致**：**Vue 3 + Vite + TypeScript（`strict`）+ axios**。各自的 `package.json` / `node_modules` 独立，互不依赖，但写法与门禁统一：

- **一律 TypeScript**：源码只写 `.ts`（含 `vite.config.ts`），SFC 只写 `<script setup lang="ts">`。新增前端文件不得落 `.js`；出现 `src/**/*.js` 即视为未完成拉平。
- **`tsconfig.json` 三份内容一致**，唯一差异：**admin / store 的 `compilerOptions.types` 多一项 `element-plus/global`**（这两端在 `main.ts` 全局 `app.use(ElementPlus)`，模板里的 `<el-*>` 才拿得到类型；mall 不注册 EP，故只有 `vite/client`）。⚠ **不要**为了「和 mall 对齐」把 `element-plus/global` 删掉 —— 删了模板里所有 EP 组件全部报错。改 tsconfig 选项时**三端同改**，不许各端漂移。
- **类型检查是构建门禁**：三端 `npm run build` = `vue-tsc --noEmit && vite build`，**类型不过即构建失败**；另有 `npm run type-check` 只查类型不出产物。⚠ **不许为了让错误消失而放松 tsconfig** —— 不关 `strict`、不把 `noUnusedLocals` / `noUnusedParameters` 打开变关闭、不把报错文件 `exclude` 掉、不写 `: any` / `as any` / `@ts-ignore` / `@ts-nocheck`。
- **HTTP 客户端一律 axios**，每端只有 `src/api/request.ts` 一个入口（**不存在** `fetch` / `XMLHttpRequest` 直调）。拦截器只做校验与错误分流，**解包由其后带泛型的 `ApiClient`（`get<T>` / `post<T>` / `put<T>` / `delete<T>`）统一做**，故**调用方直接拿到 `data` 本身**（`Promise<T>`）。⚠ **admin / store 的 `request.ts` 代码逐字相同**（仅头部互相指认端名的两条注释不同），是刻意的重复（各自独立工程，去重要引入 workspace / 共享包，暂不在范围内）；改一处要**同步改另一处**，两端 `request.ts` 头注释互相标注了这个约束。
- **类型放哪**：只在**跨文件复用**的形状（`RespData<T>` / `PageResult<T>` / 分页参数 / 登录用户 / 菜单权限树 / 各 api 模块的入出参）放 `src/types/` 与各 `src/api/*.ts`；**页面私有的表单对象、列表行**就近在各自 SFC 内声明小 interface，不外移。字段照后端 VO/DTO 写，**可空列一律 `| null`**（别用可选字段糊）。
- **登录态 401 行为三端刻意不同**：admin / store 的拦截器 **401 清登录态并跳登录页**；mall **不跳**（首页公开，见下节）。别「照另一端修一遍」。
- 契约仍以上面「对外契约清单」为准：**写 / 改前端只照 `docs/contracts/<端>.md` 写，不照后端代码写**。

## mall 前台（用户端）视觉与结构约定

`frontend/mall` 是 mall 前台的**正式前端工程**（Vue 3 + Vite + **TypeScript**，端口 5175）：**账号功能已接入端 BFF `mall-bff`**（取码 / 注册 / 登录 / 退出 / 当前顾客，契约见 `docs/contracts/mall-bff.md`），**首页六区块的数据仍是静态写死的**（在 `src/mock/`），首页数据聚合属二期。⚠ 它约束的是**视觉与结构，不是技术形态**——技术形态按上面的目标分层走，但**长什么样、分哪几块，以该工程为准**。生成/修改 mall 前台任何页面时一律遵守：

- **色板唯一来源**：只消费 `frontend/mall/src/styles/tokens.css` 的令牌，**禁止硬编码**色值 / 圆角 / 阴影；换肤只改这一个文件。
- **风格不混用**：前台是 **C 端促销风（橙红主色）**，与 admin / store 的靛蓝后台令牌**刻意不同源**；不要把后台那套 `tokens.css` 引进来，也不要把橙色板反向引回后台。**前台是单独一套风格，不做样式变换**——`element-plus` 虽在依赖里，但只作后续页面（表单 / 弹窗 / 分页）的备用能力：**不注册 EP、不引 EP 样式、页面里不出现 EP 组件**；将来某页要用，在那一页按需引入并把 EP 变量重映射到前台令牌，不全局引 `element-plus/dist/index.css`。
- **结构基准**：首页六区块顺序即基准——顶部用户条 → 万能搜索长框 → 全分类展示 → 大型滚动广告框 → 用户信息展示框 → 热门商品列表；新增页面的顶栏 / 页脚沿用同一套（`src/styles/mall.css` 的 `.topbar` / `.foot`）。
- **只做宽屏**：容器固定 1280px，**不写媒体查询**，不做手机 / 窄屏适配。
- **不做暗色模式**（C 端商城不做，与 admin / store 的 `.dark` 两回事）。
- **未登录态固定形态**：顶栏左侧「请登录 / 免费注册」文字 link，右侧「购物车 / 我的订单」。⚠ 前两个 link **已接真实路由**（`/login`、`/register`），不要改回 `href="#"`；右侧「购物车 / 我的订单」**仍是死链**（后端没有对应接口）。
- **不引外部图片与字体**：占位或无图场景用 **CSS 渐变占位**，不接外链图床 / CDN / 外部字体。
- **基准先行**：要新增区块或调整风格时，**先在 `frontend/mall` 工程里改好、定了，再往外铺**；该工程始终是唯一风格源头，不各页各写一套。

⚠ **内容范围**（10 分类铺满一行、商品卡 5 列 × 2 行、价格三层字号、角标 / 原价 / 销量位）与 `src/mock/*.ts` 里逐条探边界的假数据（各文件顶部的 `[探]` 注释），是刻意的基准，不要随手改小。
⚠ 演示外壳（原样张的「风格样张」说明条与「切换登录态」按钮）**已删除**；登录态是**真实会话**——token 存 `localStorage['pm-mall-token']`，用户信息驻留内存、刷新后由路由守卫拉 `/auth/me` 重建（`src/store/auth.ts`）。**前台首页公开、不要求登录**，守卫不拦游客。
⚠ **401 拦截器不自动跳登录页**——这是与 store / admin 两端的**刻意差异**：前台首页公开，带过期 token 的游客不该被弹走，页面照常按未登录态渲染；跳不跳由守卫 / 调用方决定。别「照 store 修一遍」。
⚠ 短信是**模拟通道**（固定验证码 `888888`，后端取码只打日志、不发真实短信），登录 / 注册页上有一行说明——**这不是假功能，是契约本身**；去掉它页面上就没有任何途径得知验证码。
⚠ 类型检查已挂进构建（三端一致），见上面「前端三端统一技术形态」。

## 代码验证只到“编译通过”

本仓库生成/修改代码后，验证一律**止步于编译通过**：

- ✅ **允许**：仅做编译级验证，例如 `mvn -q -N install`、`mvn -pl <模块> -am compile`、`mvn -pl <模块> compile`、`mvn test-compile` 等（确保能编译即可）。
- ❌ **禁止**：为验证而进行的任何**程序执行与接口测试**，包括但不限于：
  - 启动/重启服务去验证（Nacos、gateway、goods-center 等 `spring-boot:run`）；
  - 运行前端 dev/preview 去验证；
  - `mvn test`/接口测试/`curl` 打接口验证行为；
  - 任何把“跑起来看结果”当作验收手段的行为。

判断原则：不清楚某操作是否属于“执行/接口测试”时，默认**不做**，除非用户当场明确要求。

## 例外与边界

- 用户**明确要求**运行的验证（如“跑一下接口”“启动看效果”）不算违规——本规则约束的是**默认行为**，不替用户做决定。
- 数据库/数据类操作（例如用个人 skill `mysql-connect` 连远程库、同步/查询数据）属于数据处理，不属于“验证生成代码”，用户要求时照常执行；但**不得**把它当作验证本仓库刚生成代码的手段。
- 本规则针对代码产物的验证；其他与验证无关的必要操作（装依赖、编译缓存清理等）照常。

## 执行 todo.md 大改动前的数据库备份

- **凡按 `todo.md` 执行一次“大的项目改动”**（例如新增/重构服务、改表结构、灌权限种子、动业务库数据等，会改变当前库状态的操作），**开工前先对当前数据库做一次备份**。
- 备份方式：经个人 skill `mysql-connect` 连库导出全量备份（或库级 dump），备份产物存到仓库内/临时目录并记下文件位置与时间点，改动出问题可回滚。
- 备份属于数据操作（用户已授权直接操作数据库），不属于“验证代码”范畴。
- 无法确认某改动是否算“大改动”时，先与用户确认是否需要备份。
