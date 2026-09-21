<!-- contract-meta
service: cross-cutting
layer: cross-cutting
-->

# 跨服务隐式契约（第 ③ 层）

> 这一页登记**不属于任何单个服务、却由多方共同遵守**的约定。
> 它们的共同特征是：**任何一方单边改动都不会编译报错，只会在运行时静默断链。**
>
> 本页的改动**必须**与代码同一改动内提交（见 [README.md](./README.md) 维护规则第 2 条）。
> 文末的哨兵清单由 `drift-check.mjs` 读取执行。

以下 21 条中，标 ⚠ **已知风险** 的是当前已经存在的重复实现/不一致点 —— **本次只登记、不修复**。
登记的目的就是让它们可见；要不要修是独立决策。

---

## 一、响应与数据形状

### 1. 对外统一 `RespData{code,msg,data}`

| | |
|---|---|
| 契约 | 页面级接口一律返回 `RespData{code,msg,data}`；成功 `code=200`、业务失败 `code=400`（带中文提示）、系统异常 `code=500` |
| 定义位置 | `common/src/main/java/com/panoramic/common/vo/RespData.java`；形状规则写在 `backend/README.md` |
| 消费位置 | admin 9 个 Controller、store-bff 3 个 Controller、mall-bff 1 个 Controller；前端 axios 拦截器按此解包 |
| 破坏后果 | 前端统一解包与统一异常提示全部失效 |
| 核对方式 | 检查器第 6 项：页面级 Controller **必须**出现 `RespData` |

### 2. 域内接口**不包** `RespData`

| | |
|---|---|
| 契约 | 内部 Feign 方法直接返回业务结果类型（`Xxx` / `List<Xxx>` / `boolean`…），错误走异常传播；域接口错误返回真实 HTTP 状态 + `{code,msg}` |
| 定义位置 | `CLAUDE.md`「Feign 内部接口规约 · 不包 RespData」 |
| 消费位置 | goods-center 3 个 Controller、store 2 个 Controller |
| 破坏后果 | 域侧包上 `RespData` → Feign 出参类型对不上，反序列化失败或字段全空 |
| 核对方式 | 检查器第 6 项反向哨兵：域 Controller **必须不**出现 `RespData` |

### 3. 分页契约 `BasePageVO` 入 / `PageResult<T>` 出

| | |
|---|---|
| 契约 | 分页查询入参 `extends BasePageVO`，出参 `PageResult<T>` |
| 定义位置 | `common/.../common/vo/BasePageVO.java` |
| 消费位置 | 所有分页接口 |
| ⚠ 已知风险 | `PageResult` 存在**三个同名独立类型**：`contract.goods.vo.PageResult`、`contract.store.vo.PageResult` 与 admin 自己的 `admin.vo.PageResult`（不同包）。跨域复用时类型不兼容，需显式转换。⚠ 同类还有一对**规格值对象** `SpecAttr` / `SpecConfigItem`：`contract.goods.dto` 与 `contract.store.dto` 各一份，形状逐字相同（2026-09-19 拆分前 store 契约与 store 域直接引 goods 那份，拆分时切成各域自持）。**这一对没有编译期以外的保护 —— 改一份忘改另一份不会报错**，只会在两端 JSON 形状上悄悄分叉 |
| 破坏后果 | 误用另一域的 `PageResult` → 编译期即报错（属"会炸得明显"的一类，风险较低）；`SpecAttr` / `SpecConfigItem` 两份漂移 → **不报错**，静默分叉 |
| 核对方式 | 检查器第 5 项（入出参类型名可在该服务的 `typeDirs` 找到）；规格值对象两份是否仍逐字一致**靠人工核对**（2026-09-19 裁决：只登记风险，不给检查器加逐字比对规则） |

---

## 二、身份与登录态

### 4. JWT `type` claim

| | |
|---|---|
| 契约 | 登录令牌携带 `type` claim 区分身份空间（`admin` / `store` / `user`），全链路透传 |
| 定义位置 | `common/src/main/java/com/panoramic/common/security/LoginUser.java:32` `CLAIM_USER_TYPE = "type"`；值常量 `USER_TYPE_ADMIN`/`USER_TYPE_STORE`/`USER_TYPE_USER`（:23/:26/:29） |
| 签发位置 | admin `service/AuthService.java:74`（`type=admin`）、store-bff `service/AuthService.java:113`（`type=store`）、mall-bff `service/AuthService.java:137,147`（`type=user`）、写入在 `common-auth/JwtService.java:58` |
| ⚠ 签发处自查 | 每个端 BFF 都有**两处**必须成对一致：`loginUser.setUserType(...)` 与 `jwtService.generateToken(id, ...)`。mall-bff 的这两处都是 `USER_TYPE_USER`，**改一处漏一处 → 网关拼错 Redis 键 → 该端全部 401** |
| 消费位置 | 网关 `gateway/filter/AuthGlobalFilter.java:73`；服务侧 `common-auth/AuthTokenFilter.java:71` |
| ⚠ 已知风险 | 网关侧把 claim 名**字面量重写**为 `"type"`（`AuthGlobalFilter.java:38`），**未引用** `LoginUser.CLAIM_USER_TYPE`；网关不依赖 `common`，改常量网关不会跟随 |
| 破坏后果 | claim 名改动 → 网关取不到身份类型 → Redis 键拼错 → **全端 401** |
| 核对方式 | 哨兵 `CLAIM_USER_TYPE`、`"type"` |

### 5. Redis 登录态键 `panoramic:login:{userType}:{userId}`

| | |
|---|---|
| 契约 | 键格式 = `{redis-prefix}:{userType}:{userId}`，**是网关 ↔ 端 BFF 的共享契约，不可单边改动** |
| 定义位置 | Nacos 共享配置 `auth.yml` 的 `panoramic.auth.redis-prefix`（值 `panoramic:login`） |
| 拼装位置（3 处） | 写方 `common-auth/.../LoginUserCacheService.java:104`；验方 `gateway/filter/AuthGlobalFilter.java:75`；store-bff 经 `LoginUserCacheService` |
| ⚠ 已知风险 | 键拼装在**3 处独立书写**，靠约定对齐、无共享工具方法 |
| 破坏后果 | 单边改格式 → 网关按新格式查、BFF 按旧格式写 → 登录态全部失效 |
| 核对方式 | 哨兵 `panoramic:login` |

### 6. 内部身份头 `X-User-Id` / `X-User-Type`

| | |
|---|---|
| 契约 | 网关验签后注入身份头，端 BFF 经 Feign **原样透传**，域直取填 `UserContext`；**仅用于审计填充与 `audit_by` 留痕，读 ≠ 判断** |
| 定义位置 | `X-User-Type` 有常量 `LoginUser.HEADER_USER_TYPE`（:35）；⚠ `X-User-Id` **无 Java 常量**，头名只由 Nacos `auth.yml` 的 `panoramic.auth.header-name` 提供，且默认值 `X-User-Id` 在 **11 处 `@Value`（散在 10 个文件）**里各自兜底——2026-09-21 按 grep 实数校准过一次（此前记作 5 处，是当时漏数的） |
| 注入位置（唯一） | `gateway/filter/AuthGlobalFilter.java:89-90` |
| 透传位置 | `goods-center-interface/.../contract/goods/api/GoodsFeignConfiguration.java:30-56`、`store-interface/.../contract/store/api/StoreFeignConfiguration.java:32-59`、`customer-center-interface/.../contract/customer/api/CustomerFeignConfiguration.java:31-58`、`trade-center-interface/.../contract/trade/api/TradeFeignConfiguration.java:31-58` |
| 消费位置 | `store/config/StoreUserIdentityFilter.java:36,56`、`goods-center/config/GoodsUserIdentityFilter.java:36,56`、`customer-center/config/CustomerUserIdentityFilter.java:36,56`、`trade-center/config/TradeUserIdentityFilter.java:38,58`、`common-auth/AuthTokenFilter.java:71` |
| ⚠ 已知风险 | **四个**同职责的 Feign 配置**行为不对称**，是**四种**行为：`GoodsFeignConfiguration:55-56` 缺 `X-User-Type` 时**回退为 `admin`**；`StoreFeignConfiguration:57-60` **不做回退**；`CustomerFeignConfiguration:56-58` **不做回退，且 `userType` 为空时干脆不发该头**；`TradeFeignConfiguration:56-58` **照 `CustomerFeignConfiguration` 那份抄**（不做回退、空值不发头）——C 端身份一旦被盖成 `admin`，域内审计留痕就失真。这是有意的选择（理由写在代码里），**新域照哪个抄要自己判，别默认跟 goods 那份**。⚠ 新增域时**必须**同步登记本枚举（漏登记等于把这个刻意选择埋掉） |
| 破坏后果 | 头名不一致 → 域取不到身份 → 审计字段静默留空（不报错，最难发现的一类） |
| 核对方式 | 哨兵 `X-User-Type`、`X-User-Id`、`panoramic.auth.header-name` |

### 7. 缺身份头即**不填充、不拦截**

| | |
|---|---|
| 契约 | 域内身份过滤器在缺 `X-User-Id` 时**直接放行**（审计留空），**不得回 401** —— 那等于在域内做鉴权 |
| 定义位置 | `CLAUDE.md`「信任与防线 · 缺头即不填充、不拦截」 |
| 消费位置 | `StoreUserIdentityFilter`、`GoodsUserIdentityFilter`、`CustomerUserIdentityFilter`、`TradeUserIdentityFilter`（缺 `X-User-Id` 时直接放行、审计留空） |
| 破坏后果 | 域内回 401 → 直连调用（无网关头）全部失败，破坏"域不做鉴权"的分层 |
| 核对方式 | 人工核对（静态哨兵无法表达"缺头时不拦截"这一语义） |

### 8. 审计字段格式 `UserType:UserId`

| | |
|---|---|
| 契约 | `create_user` / `update_user` 值为 `UserType:UserId` 字符串（如 `admin:1` / `store:7`），列类型 **`VARCHAR(32)`**，实体字段类型 **`String`** |
| 定义位置 | `common/.../vo/BaseEntity.java:14,23,35`（`:14` 类 javadoc 声明 `VARCHAR(32)` 约定；`:23`/`:35` 为 `createUser` / `updateUser` 字段声明） |
| 写值位置 | `common/.../config/MyMetaObjectHandler.java:52`（拼 `{userType}:{userId}`，取不到 userId 留空） |
| 回退规则 | `common/.../util/UserContext.java:68-72` `getUserType()` 缺省回退 `admin` |
| 业务列复用 | `store_goods_spu.lock_user`（`store/.../entity/StoreGoodsSpu.java:126`）沿用同一格式 |
| 破坏后果 | 改成 INT 或去掉类型前缀 → 多端身份空间（admin / store / user）无法消歧，同一 id 指向不同人 |
| 核对方式 | 检查器第 5 项同类：`BaseEntity` 的 `createUser` 类型为 `String`（哨兵 `VARCHAR(32)`） |

### 9. 端 BFF 身份类型绑定 `panoramic.auth.user-type`

| | |
|---|---|
| 契约 | 每个端 BFF **只接受本端身份类型**的登录态：`AuthTokenFilter` 从 Redis 快照重建出 `LoginUser` 后比对 `userType`，不匹配即按**未认证**处理（HTTP 401 + `{code:401,msg}`，由 `AuthenticationEntryPoint` 统一产出，不在此自写响应） |
| 定义位置 | 各端**自己的** `application.yml`：`panoramic.auth.user-type` = `admin`（admin）/ `store`（store-bff）/ `user`（mall-bff）；取值须与 `LoginUser.USER_TYPE_*` 常量（第 4 条）逐字一致 |
| 消费位置 | `common-auth/.../AuthTokenFilter.java`——在 `resolveLoginUser` 之后统一比对，**网关透传头与 Bearer 兜底两条路都覆盖**；装配于 `common-auth/.../SecurityConfig.java` |
| ⚠ 不得放共享配置 | 三端值不同，**不能**放进 Nacos 共享 `auth.yml`（网关与三端 BFF 都加载该 dataId，会一并拿到错的值） |
| ⚠ 无默认值 | `@Value("${panoramic.auth.user-type}")` **不带兜底**，漏配 → **启动即失败**。取向同 `config.import` 不带 `optional:`：宁可起不来，也不要静默失去隔离 |
| ⚠ 网关侧**仍不绑定** | `AuthGlobalFilter` 只按 `type` claim 拼 Redis 键查登录态，**不校验该 type 与目标路由前缀是否匹配**（第 10 条的路由与身份类型之间没有绑定关系）。**本断言是跨端隔离的唯一防线**，删掉它不会编译报错 |
| 破坏后果 | 断言缺失/失效 → 任一端的 token 可被另一端的 BFF 当成本端身份。已实测实例：**顾客 token 打 `/store/**` 被当作店主**——`StoreShopBffService#currentStoreId` / `StoreGoodsBffService#currentStoreId` 把 `loginUser.getId()` 当 `store_id` 用；`mall_user.id` 与 `store_shop.id` **同库自增、必然撞号**，且 C 端注册公网可达（固定码 `888888`）→ **注册即越权**，可定向刷号命中目标 `store_id` |
| 核对方式 | 哨兵 `panoramic.auth.user-type`（消费处）+ 三端 yml 各一处 `user-type:` 声明 |

---

## 三、基础设施

### 10. 网关路由与 BFF 白名单

| | |
|---|---|
| 契约 | 公网只路由到端 BFF；`panoramic.gateway.bff-services` 是**唯一放行名单**，名单外一律 403 |
| 定义位置 | `gateway/src/main/resources/application.yml:31-51`（3 条路由）、`:65`（`bff-services = admin,store-bff,mall-bff`） |
| 强制位置 | `gateway/filter/BffRouteGuardFilter.java`：`getOrder() = -200`，**先于鉴权** `AuthGlobalFilter`（-100）；**空配置 = 拒绝一切**（默认拒绝）；未命中路由直接放行；仅 `scheme=lb` 且在名单内放行 |
| 消费位置 | 全部公网流量 |
| 破坏后果 | 新端 BFF 上线忘了进白名单 → 该端**全部 403**（会炸得明显）；线序调整 → 未鉴权先过守卫 |
| 核对方式 | 检查器 gateway 专项（路由 ↔ `application.yml`；白名单 ↔ 实际存在的 BFF 模块）。⚠ 端 BFF 名单与路由前缀**由网关配置推导**（`bff-services` 的值 + `uri: lb://<svc>` ↔ `Path=/<前缀>/**`），推不出唯一前缀即**直接失败**——不降级为警告，否则新增端 BFF 时该项会静默跳过 |

### 11. 鉴权白名单**在网关与服务两处各写一份**

| | |
|---|---|
| 契约 | 免鉴权路径需**同时**登记在网关侧与各服务本地侧；**网关侧带前缀、服务侧不带** |
| 定义位置 | 网关 `gateway/application.yml:67`（`/admin/auth/login,/store/auth/login,/store/auth/register,/mall/auth/login,/mall/auth/register,/mall/auth/sms-code,/mall/catalog/categories,/discovery/**`）；各服务 `application.yml` 的 `panoramic.auth.whitelist-paths`（admin `/auth/login`；store-bff `/auth/login,/auth/register`；mall-bff `/auth/login,/auth/register,/auth/sms-code,/catalog/categories`） |
| ⚠ 易漏项 | **登录前调用的接口**（C 端的「获取验证码」`/auth/sms-code`）最容易漏——它也必须在两处白名单里，否则按钮直接 401。⚠ **C 端登记的是精确路径 `/catalog/categories`（首页宫格的分类树），不是 `/catalog/**` 前缀**：mall 的分级是「**首页免登录，一涉及商品查询与详情就鉴权**」，写成前缀会把商品分页 / 筛选 / 详情一并放开到公网（前台裸奔）。改这一行必须两侧同一改动内一起改，且不要把精确路径换回前缀 |
| ⚠ 分级配套 | 白名单收窄只挡得住**直连**：C 端前端还有一层「需登录页」路由级拦截（`/search`、`/category/:id`、`/goods/:id` 的 `meta.requiresAuth`，未登录即带 `redirect` 跳登录页），以及拦截器 401 兜底（清本地态 + 提示 + 带 `redirect` 跳登录页）。**唯一静默的 401 是守卫刷新重建登录态的 `/auth/me`**——公开首页上的重建失败不该把游客弹走，见 [mall-bff.md](./mall-bff.md) |
| ⚠ 静默 401 由守卫自己收口 | `/auth/me` 的静默分支会**先清掉本地 token**，随后同一导航内的业务接口再吃 401 时，拦截器的「本来有登录态吗」判据（`getToken()`）已为假 → 既不提示也不跳转，需登录页会渲染成「商品暂不可用」（把**未登录**报成**下游故障**）。故守卫在重建失败时若当前页是 `requiresAuth`，**自己**落登录页（`loginLocation`），**不能把交接推给拦截器**。⚠ 判据必须是 `!getToken()`（会话真没了）而**非**只看 `requiresAuth`：清态只有 401 分支会做，`me()` 因网络 / 5xx 失败时 token 未动，此时若也跳登录页会**成环**——token 还在 → 登录页命中「已登录不该待在登录页」又被弹回原页 → 再 `me()`…直到 vue-router 无限重定向保护中止导航。删掉这个分支不会编译报错，只会在「token 过期后刷新需登录页」这条路径上静默退化 |
| 消费位置 | 网关 `AuthGlobalFilter` 与服务侧 `AuthTokenFilter` |
| 破坏后果 | 只改一处 → 登录接口被拦（登不进去）或本应鉴权的接口裸露 |
| 核对方式 | 检查器 gateway 专项：两侧白名单去前缀后应互为子集关系 |

### 12. Nacos 共享配置 data-id

| | |
|---|---|
| 契约 | 4 个 data-id：`datasource-mysql` / `datasource-redis` / `auth` / `feign-circuitbreaker`；GROUP 一律 `DEFAULT_GROUP`；服务侧 `spring.config.import` **不带 `optional:`** |
| 定义位置 | `backend/nacos-config/`（源文件）；发布态在 Nacos 控制台 |
| 消费位置 | 网关与各服务（加载矩阵见原 `nacos-config/README.md` 已迁入本页） |
| 破坏后果 | 缺任一 data-id **启动即失败**（属"会炸得明显"的一类）；漏配新服务 → 起不来 |
| 核对方式 | 检查器第 9 项：各服务 `application.yml` 的 `import` 列表**不得出现 `optional:`**。⚠ 下方的**加载矩阵为人工维护、无机器核对**——「矩阵某列 ✅ ↔ 该服务 import 里真有该 data-id」这一对应关系静态哨兵表达不了（矩阵是表格，不是源码字面量）。新增服务时**须手工补列并手工核对**，漏补不会被检查器抓到（哨兵清单里并没有 `datasource-mysql`；此处曾误称有，2026-09-19 订正） |

**加载矩阵**：

| data-id | 网关 | admin | store-bff | mall-bff | goods-center | store | customer-center | trade-center |
|---|:--:|:--:|:--:|:--:|:--:|:--:|:--:|:--:|
| `datasource-mysql.yml` | — | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| `datasource-redis.yml` | ✅ | ✅ | ✅ | ✅ | — | — | — | ✅ |
| `auth.yml` | ✅ | ✅ | ✅ | ✅ | — | — | — | — |
| `feign-circuitbreaker.yml` | — | ✅ | ✅ | ✅ | — | — | — | — |

> 规律：**端 BFF（admin / store-bff / mall-bff）一律加载四个**；域服务只依赖 `common`，结构上拿不到认证链，所以不加载 `auth` / 熔断配置。
>
> ⚠ mall-bff **已接** goods-center（分类树）与 store（商品分页 / 筛选聚合），其 `feign-circuitbreaker.yml` **不再是空转**——三端 BFF 都已在调域，「端 BFF 一律加载四个」的规律不变。
>
> ⚠ **`trade-center` 是唯一加载 `datasource-redis` 的域**（购物车加购去重与计数缓存），这是**有意的登记例外**，不是「域也开始依赖 Redis」的转向：它用 Redis 只做**缓存与提示**，MySQL 始终是唯一事实源、两个方向的错判都由写路径自愈（见 [trade-center.md](./trade-center.md) 第一节）。⚠ 但它**仍不加载 `auth.yml`**——Redis 不等于登录态：域内不做鉴权、不校验 token，身份头只读来填 `UserContext`（第 6、7 条）。⚠ 新增域时若也要 Redis，**同样要在此矩阵补列并说明用途**，别让「域不加载 redis」这条规律被静默打破。

### 13. 熔断契约：4xx 不计失败率，5xx 计入

| | |
|---|---|
| 契约 | 业务 4xx（`ServiceException`）**不计**熔断失败率、原样透传给页面；5xx / 连接 / 熔断**计入**并降级为各端「…暂不可用」 |
| 定义位置 | Nacos `feign-circuitbreaker.yml:43-44` 的 `resilience4j.circuitbreaker.configs.default.ignore-exceptions` = `com.panoramic.common.exception.ServiceException` |
| 实现位置 | `common/.../feign/InternalApiErrorDecoder.java` —— **按 HTTP 状态码分野**：4xx → `ServiceException`；5xx → 回落 `Default()` 产出 `FeignException` |
| 消费位置 | admin、store-bff、**mall-bff**（三端均已调域，均引 resilience4j）；降级包装走 `common/.../feign/BffFeignCall.java` |
| 破坏后果 | 5xx 也还原成 `ServiceException` → 熔断**永远打不开**，下游故障直接拖垮调用方；反之若 4xx 计入 → 店主连续几次操作失误就把熔断打开，后续**正常**请求被降级成 500 |
| 核对方式 | 哨兵 `ignore-exceptions`、`ServiceException`、`InternalApiErrorDecoder` |

### 14. Feign 入出参类型必须**同源于该域的接口模块**

| | |
|---|---|
| 契约 | Feign interface 的入参/出参 DTO 在**该域的接口模块**（`goods-center-interface` / `store-interface` / `customer-center-interface` / `trade-center-interface`，包根 `com.panoramic.contract.<域>`）维护，调用方与被调用方引用**同一份类型**，禁止各自复制。⚠ 2026-09-19 起这些类型**不再放 `common`**（`common` 已收敛为纯基座，不含任何域的契约类型） |
| 定义位置 | `CLAUDE.md`「Feign 内部接口规约 · 公共类型」 |
| 消费位置 | `goods-center-interface/.../contract/goods/api/GoodsCenterClient.java`、`store-interface/.../contract/store/api/StoreClient.java`、`customer-center-interface/.../contract/customer/api/CustomerCenterClient.java`、`trade-center-interface/.../contract/trade/api/TradeCenterClient.java` 与其域侧实现；类型清单见各服务契约页的「类型所在包」 |
| 破坏后果 | 各端复制一份 → 字段漂移，反序列化**静默丢字段** |
| 核对方式 | 检查器第 5 项：契约表里的入出参类型名必须能在该服务 `contract-meta` 的 `typeDirs` 里找到对应 `.java` |
| 结构保障 | 各域模块只依赖**自己那个** `-interface`（`common` 里没有契约类型），跨域引用会**当场编译失败**；新建域时必须同步建 `<域>-interface` 模块 |

### 15. 域端口只在内网可达（**安全前提，非代码约束**）

| | |
|---|---|
| 契约 | 域服务（goods-center 8081 / store 8083 / customer-center 8086 / trade-center 8087）**不做任何鉴权**，其安全性完全依赖"端口只在内网可达" |
| 定义位置 | 各域 `application.yml` 注释；`goods-center/config/GoodsSecurityConfig.java:16`、`store/config/StoreSecurityConfig.java:17`、`customer-center/config/CustomerSecurityConfig.java:17`、`trade-center/config/TradeSecurityConfig.java:19` |
| 消费位置 | 全部部署环境 |
| 破坏后果 | 域端口一旦暴露公网 → 可伪造 `X-User-Id` → **防线整体失效**（域内不做鉴权是有意设计，不是疏漏）。⚠ **「锚点即数据权限」的两个域上这条更硬**（customer-center 8086 / trade-center 8087）：域侧 `customerId` **直接取自请求路径且不做任何鉴权**（见 [customer-center.md](./customer-center.md) 与 [trade-center.md](./trade-center.md) 第一节），**能连到这两个端口的人就能读写任意顾客的资料、地址簿、购物车与订单**（含下单 / 支付 / 发货 / 收货四项动作）——该口径只在「路径上的 `customerId` 由端 BFF 从登录态填」+「该端口不可从公网抵达」两条**同时**成立时才成立。⚠ trade-center 的 Redis 不改变这一点：那两个键只存 sku 与行数，**没有登录态**，抄不走任何身份 |
| 核对方式 | **无法静态核对**。本页仅登记，属部署/运维前提 |

### 16. 权限串与前端路由一致性

| | |
|---|---|
| 契约 | ① **权限串三方一致**：`@PreAuthorize` 字面量 ↔ `sys_permission.perms` 种子 ↔ 前端 `v-perm`；② **路由逐字一致**：前端 `router/index.ts` 的 `path` ↔ `sys_permission.route`（页面型，`type=2`） |
| 定义位置 | 后端注解在 `admin/**/controller/**`；种子在 `admin/src/main/resources/db/*.sql`；前端在 `frontend/admin/src/{router/index.ts,views/**}` |
| 消费位置 | RBAC 菜单渲染、按钮显隐、后端授权判定 |
| 破坏后果 | 权限串对不上 → 菜单点不开 / 按钮不显示 / 403；路由差一字 → 侧栏菜单点不开 |
| 核对方式 | 检查器第 2、3、4 项 |

---

## 四、C 端商品浏览与跨店查询

### 17. C 端商品展示口径固定在端 BFF，不在域

| | |
|---|---|
| 契约 | mall-bff 调 store 域商品查询（分页 + 筛选聚合）时**固定传** `shopStatus=2`（已审核通过店铺）+ `shelfStatus=1`（上架）+ `lockStatus=0`（未被平台锁定）；store 域的「跨店通用」接口**只按传入条件过滤，不含任何 C 端隐含约束** |
| 定义位置 | `mall-bff/service/CatalogBffService.java`（`SHOP_STATUS_APPROVED` / `SHELF_ON` / `LOCK_OFF` 三个常量，`goods()` 与 `facets()` 两处都设） |
| 消费位置 | C 端 `POST /catalog/goods` 与 `POST /catalog/facets`——两处必须同一套口径，否则筛选面板的命中数与列表对不上 |
| 破坏后果 | 漏传任一条件 → **未过审店铺的商品 / 平台锁定商品漏到公网前台**（静默，列表照常渲染） |
| 核对方式 | **人工核对**（静态哨兵无法表达"必须传某值"这一语义）。新增 C 端商品查询时必须保持这三个条件 |

### 18. 筛选维度聚合「排除自身维度」

| | |
|---|---|
| 契约 | facets 的分类维度**不受已选分类影响**、品牌维度**不受已选品牌影响**：同一维度内的筛选条件不得施加到该维度自身的聚合上 |
| 定义位置 | `store/service/impl/StoreGoodsSpuServiceImpl.java#facetBy`（`isCategory` 分支决定施加 `filterBrandIds` 还是 `filterCategoryIds`） |
| 消费位置 | C 端筛选面板（`POST /catalog/facets`） |
| 破坏后果 | 施加了自身维度 → 选中某个分类后该维度的其他选项 count 归零 / 消失，**用户无法再切换或取消筛选** |
| 核对方式 | **人工核对**（无法静态表达"某条件下不得出现某筛选"） |

### 19. 跨店分页通用化：admin BFF 与 mall-bff 共用域接口

| | |
|---|---|
| 契约 | store 域 `POST /goods/cross-shop/spu/page`（与 `POST /goods/facets`）是**跨店通用**接口：**无数据权限锚点，限定条件由调用方自设**——`pageStoreGoodsCrossShop` 由 admin BFF 与 mall-bff 共用、`crossShopFacets` 目前只有 mall-bff 消费；域内不判身份、不做端别分流。⚠ 域返回的 VO 是**管理端超集**（含 `lockUser` / `lockReason` / `lockTime` / `goodsSpuId` 等），**C 端输出前必须由端 BFF 逐字段裁剪** |
| 定义位置 | `store-interface/.../contract/store/api/StoreClient.java#pageStoreGoodsCrossShop` / `#crossShopFacets`；域实现 `store/controller/GoodsController.java` + `StoreGoodsSpuServiceImpl#crossShopPage` / `#facets` |
| 消费位置 | admin BFF `ShopGoodsBffService`（管理端：不传 C 端三条件、走全量）；mall-bff `CatalogBffService#toMallItem`（C 端：**手工逐字段映射，刻意不用 `BeanUtils.copyProperties`**） |
| 破坏后果 | 改成整对象拷贝 → 域 VO 日后加字段会**自动漏到 C 端**（锁定原因、锁定人一并外泄） |
| 核对方式 | **人工核对**（字段级裁剪无法用静态哨兵表达） |

---

### 20. C 端商品可见性：端 BFF **读时重判**（列表 / 详情 / 购物车同一不变量）

| | |
|---|---|
| 契约 | C 端「商品可见」只有**一个口径**：店铺 `status=2`（已审核通过）+ `shelfStatus=1`（上架）+ `lockStatus=0`（未锁定）。⚠ **三处落点、判定位置各不相同，别只改一处**：① **列表**把这三个条件**当查询参数传给域**（跨店通用接口，域只做等值/IN 过滤）；② **详情**按 id 取一条（域侧 `platformStoreGoodsDetail`，**本身不含任何可见性约束**）与 ③ **购物车行**（域侧 `trade-center` 只回 `spuId` / `skuId` 原始行）都必须取回后**在端 BFF 逐条重判**。三处是同一不变量的三个落点，**单边改动不会编译报错**，只会让「列表搜得到、点进去说下架」或「车里还留着已下架商品」 |
| 定义位置 | mall-bff `CatalogBffService#goods`（把条件传下去）；判定的**实现只有一处**：`#isVisible`（+ 店铺状态按 `storeId` 记忆化的 memo），由 `#visibleDetailOrNull`（单条：详情 `#detail` 与加购校验共用）与 `#visibleSpuIds`（批量：购物车列表用）两个出口复用 —— ⚠ **新增需要判可见性的读，一律走这两个出口，不要再写第三份判定**；域侧 `store-interface/.../contract/store/api/StoreClient.java#platformStoreGoodsDetail` / `#platformSpuBatch` + `store/controller/GoodsController.java` |
| 消费位置 | mall-bff `CatalogController` 的 `/catalog/goods`、`/catalog/goods/{id}`，`CartController` 的 `/cart`（购物车读）与 `POST /cart/items`（加购前置校验）；同一个域方法另有 admin BFF 消费（管理端不走 C 端口径） |
| ⚠ 购物车行的差异 | 购物车的不可见行**不 404、也不从列表里删掉**：打 `invalid` 标记后**照常下发**（顾客要看得见才敢删它），只是不进件数与金额——见 [mall-bff.md](./mall-bff.md)「购物车不可买口径」。⚠ **SPU 上架 ≠ 名下每个 SKU 都在售**（第 20 条的不变量只保证「至少一个在售」），故购物车行与加购**还要**多判一条「该 SKU 在售」 |
| 不可见响应 | 不存在 / 已下架 / 被锁定 / 店铺未过审 → 一律业务码 **404**「商品不存在或已下架」，**不区分原因**（区分了就等于给外人一个探测商品是否存在 / 是否被锁的接口） |
| 4xx/5xx 分野 | **只有业务 4xx**（400/403/404）才转成 404；熔断 / 连接降级是 5xx，**照抛**——否则下游一抖，「商品服务挂了」会被伪装成「商品已下架」（第 13 条在本场景的落点） |
| 破坏后果 | 详情漏判某一条件 → 平台锁定 / 未过审店铺的商品可被 `/goods/{id}` 直接打开（列表搜不到，但 id 可枚举）；把 5xx 也当 404 → 下游故障时全站商品看起来都下架了 |
| 核对方式 | **人工核对**（可见性重判无法用静态哨兵表达） |

---

### 21. 富文本字段：写入侧原样存，**消毒在消费端 BFF 的出口**

| | |
|---|---|
| 契约 | store 域的富文本列（当前是 `store_goods_spu.description`）是**店主自由录入的 HTML**（store / admin 两端的录入框提示语就是「支持 HTML」、库列注释是「商品详情（富文本）」）：**域侧原样存取、不清洗，也不做任何内容裁决**。渲染它的消费端 BFF **必须在自己出口清洗**，出参即「可直接渲染的 HTML」。消毒点为什么在 BFF、不在域也不在前端：域侧不持身份、拿不到「这一份给谁看」；前端各自去引清洗库，漏一个就是一处 XSS。⚠ 店主输入**不可信** —— 原样下发再 `v-html`，等于把渲染它的那个页面交给店主注入 |
| 定义位置 | 域侧列：`store/src/main/resources/db/*.sql`（`store_goods_spu.description`）。**清洗件只此一份**：`common/src/main/java/com/panoramic/common/util/HtmlSanitizer.java`（`Safelist.relaxed`：剥 `script` / `on*` 事件属性 / `style`，`a[href]` 限 ftp/http/https/mailto、`img[src]` 限 http/https；无标签的纯文本先转义再把换行折 `<br>`，两类汇到同一套清洗，出口形状统一是 HTML）。⚠ **别各端各写一份白名单** —— 那种重复迟早漂移 |
| 消费位置 | ① mall-bff `CatalogBffService#toMallDetail`（出口调 `HtmlSanitizer`）→ `frontend/mall/src/views/GoodsDetailView.vue` 的 `v-html`；② admin BFF `ShopGoodsBffService#detailGoods`（出口调同一件）→ `frontend/admin/src/views/shopgoods/ShopGoodsDetail.vue` 的 `v-html`；③ store-bff（`StoreGoodsBffService` → `StoreGoodsSpuDetailBffVO` → 店主编辑表单）**不该洗** —— 那条链路是「读出来 → 店主改 → 写回去」，洗了会把库里已存的 HTML 吃掉且静默存回残文；其前端本来也没有 `v-html` sink。⚠ admin 端 `SpuPreviewDialog.vue` 另有一处 `v-html` 渲染的是 `goods_spu.description`（**标准商品模板库**，写路径只有 admin 自己的 `GoodsTemplateBffService`，store-bff / mall-bff 只调读方法），不属本条的店主输入，另有其口径 |
| 破坏后果 | 去掉清洗 → 渲染该字段的页面被店主注入。**这不是假设**：admin 端 2026-09-19 之前一直如此（`detailGoods` 直接回域 VO + 页面 `v-html`），店主在 store 端存一条 `<img src=x onerror=…>` 即可在平台管理员的会话里执行脚本（admin token 在 localStorage → 等同于店主提权到平台账号），同日修复；改回 `{{ }}` 纯文本插值 → 录入者写的 `<p>` 原样露在页面上（mall 端同日的一次真实回退）；新增端形态（小程序 / APP）若不在自己的出口自洗一遍，同样中招 —— 域侧不会替它洗 |
| 核对方式 | **人工核对**（白名单策略无法用静态哨兵表达） |

---

## 五、已删除的契约（**反向哨兵**）

这些契约曾经存在、已被有意移除。任何人重新引入都意味着**回退了一次架构决策**，因此列为"必须不存在"的哨兵：

| 已删契约 | 删除时间 | 为何不能复活 |
|---|---|---|
| 内部令牌 `X-Internal-Token` | 2026-09-10 | 域内应用层鉴权已整体移除；防线收敛到网络层。重新引入 = 在域内做鉴权，破坏 BFF 分层 |
| `InternalTrustFilter` | 2026-09-10 | 同上 |

⚠ 注意：这两个字面量**出现在文档里是正常的**（本页、`CLAUDE.md`、各 README 都有"已删除"的说明）。
哨兵检查**只扫源码**（`.java` / `.yml`），不扫 `.md`。

---

<!-- contract-sentinels
{
  "presence": [
    { "literal": "CLAIM_USER_TYPE", "in": ["backend/common/src/main/java/com/panoramic/common/security/LoginUser.java"], "why": "JWT type claim 常量定义处（第 4 条）" },
    { "literal": "panoramic:login", "in": ["backend/nacos-config", "backend/gateway", "backend/common-auth"], "why": "Redis 登录态键前缀，网关↔端 BFF 共享（第 5 条）" },
    { "literal": "X-User-Id", "in": ["backend/nacos-config"], "why": "X-User-Id 头名的唯一权威源（第 6 条）" },
    { "literal": "X-User-Type", "in": ["backend/gateway", "backend/goods-center-interface/src/main/java/com/panoramic/contract/goods/api", "backend/store-interface/src/main/java/com/panoramic/contract/store/api", "backend/customer-center-interface/src/main/java/com/panoramic/contract/customer/api", "backend/trade-center-interface/src/main/java/com/panoramic/contract/trade/api"], "why": "身份头注入与透传（第 6 条）；⚠ 每个域客户端都要在此枚举里，漏一个则从该客户端删掉透传时检查器看不见" },
    { "literal": "bff-services", "in": ["backend/gateway"], "why": "网关 BFF 白名单键（第 10 条）" },
    { "literal": "${panoramic.auth.user-type}", "in": ["backend/common-auth"], "why": "端 BFF 身份类型绑定的消费处（第 9 条）；⚠ 必须带 ${} 占位符形式——裸属性名会被类注释里的散文假性满足" },
    { "literal": "user-type:", "in": ["backend/admin", "backend/store-bff", "backend/mall-bff"], "why": "三端 BFF 各须显式声明本端身份类型（第 9 条）" },
    { "literal": "ignore-exceptions", "in": ["backend/nacos-config"], "why": "熔断忽略 ServiceException（第 13 条）" },
    { "literal": "InternalApiErrorDecoder", "in": ["backend/common/src/main/java/com/panoramic/common/feign"], "why": "4xx/5xx 分野的实现处（第 13 条）" }
  ],
  "absence": [
    { "literal": "X-Internal-Token", "in": ["backend"], "ext": [".java", ".yml", ".yaml"], "why": "2026-09-10 已删除的内部令牌，不得复活" },
    { "literal": "InternalTrustFilter", "in": ["backend"], "ext": [".java", ".yml", ".yaml"], "why": "2026-09-10 已删除，不得复活" }
  ]
}
-->
