<!-- contract-meta
service: mall-bff
layer: page
baseUrl: /mall
scanDirs: backend/mall-bff/src/main/java/com/panoramic/mallbff/controller
typeDirs: backend/goods-center-interface/src/main/java, backend/store-interface/src/main/java, backend/common/src/main/java, backend/mall-bff/src/main/java
-->

# 商城前台 BFF（mall-bff）· 第 ① 层

> 商城**前台（C 端顾客）**的端 BFF，端口 **8085**，网关前缀 `/mall/**`（`StripPrefix=1`）。
> 它是本仓库的**第三个端 BFF**（另两个是 admin / store-bff），签发**第三套身份** `type=user`。
> 服务范围 = **顾客账号骨架**（取码 / 注册 / 登录 / 登出 / me / 换绑手机号）+ **顾客资料与收货地址**
> （资料保存 / 地址增删改查 / 设默认）+ **C 端商品浏览**（分类树 / 商品分页 / 筛选聚合 / 商品详情）。
> 已接 **goods-center**（分类树）、**store**（商品分页 / 筛选聚合 / 详情）与 **customer-center**（顾客资料）
> 三个业务域；顾客侧**哪些行尚未落地，以下表「状态」列为准**（本文件正文不另记进度与条数），
> 地址经内部 Feign 接 **customer-center**（见 [customer-center.md](./customer-center.md)）；
> ⚠ 换绑手机号**不经任何域**——手机号是 `mall_user` 的列（本端独有），`customer_profile` 没有该字段；
> 首页「热门商品列表」区块仍是静态 mock。

## 一、接口形态

页面级通用规则见 [README.md](./README.md)：**必包** `RespData{code,msg,data}`。
字段定义**不在本表**，去下列类型所在的源码看（表里不抄字段，抄一份就是制造第二个会漂移的地方）。

| 类型 | 所在包 |
|---|---|
| `SmsCodeDTO` / `RegisterDTO` / `LoginDTO` | `backend/mall-bff/src/main/java/com/panoramic/mallbff/dto/` |
| `ProfileSaveDTO` | `backend/mall-bff/src/main/java/com/panoramic/mallbff/dto/` |
| `AddressSaveDTO` / `ChangePhoneDTO` | `backend/mall-bff/src/main/java/com/panoramic/mallbff/dto/`（⚠ 是否已落地见下表「状态」列） |
| `MallGoodsPageQueryDTO` / `MallFacetQueryDTO` | `backend/mall-bff/src/main/java/com/panoramic/mallbff/dto/` |
| `CurrentUserVO` / `LoginResultVO` | `backend/mall-bff/src/main/java/com/panoramic/mallbff/vo/` |
| `AddressVO` | `backend/mall-bff/src/main/java/com/panoramic/mallbff/vo/`（⚠ 是否已落地见下表「状态」列） |
| `MallGoodsItemVO` / `MallFacetVO` / `MallFacetItemVO` / `MallGoodsDetailVO` / `MallGoodsSkuVO` | `backend/mall-bff/src/main/java/com/panoramic/mallbff/vo/` |
| `CategoryTreeVO` | `backend/goods-center-interface/src/main/java/com/panoramic/contract/goods/vo/` |
| `SpecConfigItem` / `SpecAttr` | `backend/store-interface/src/main/java/com/panoramic/contract/store/dto/`（详情页的规格配置与 SKU 规格属性）⚠ 数据**来自 store 域**（域 SKU/详情 VO 里就是这一份），故本端 VO 用的是 **store** 那份；`contract.goods.dto` 下另有一份同形同名的孪生类，**别引错**（见 [cross-cutting.md](./cross-cutting.md) 第 3 条） |
| `PageResult` | `backend/store-interface/src/main/java/com/panoramic/contract/store/vo/` ⚠ 与 `contract.goods.vo.PageResult` 同名不同包（见 [cross-cutting.md](./cross-cutting.md) 第 3 条）；本模块用的是 **store** 那个 |
| `RespData` | `backend/common/src/main/java/com/panoramic/common/vo/` |

## 二、接口清单（16 条）

| 方法 | 路径 | 权限串 | 入参 | 出参 | 声明位置 | 状态 |
|---|---|---|---|---|---|---|
| POST | /auth/sms-code | — | `SmsCodeDTO` | `Void` | AuthController.java:39 | |
| POST | /auth/register | — | `RegisterDTO` | `LoginResultVO` | AuthController.java:48 | |
| POST | /auth/login | — | `LoginDTO` | `LoginResultVO` | AuthController.java:56 | |
| POST | /auth/logout | — | — | `Void` | AuthController.java:64 | |
| GET | /auth/me | — | — | `CurrentUserVO` | AuthController.java:76 | |
| GET | /catalog/categories | — | — | `List<CategoryTreeVO>` | CatalogController.java:46 | |
| POST | /catalog/goods | — | `MallGoodsPageQueryDTO` | `PageResult<MallGoodsItemVO>` | CatalogController.java:61 | |
| POST | /catalog/facets | — | `MallFacetQueryDTO` | `MallFacetVO` | CatalogController.java:69 | |
| GET | /catalog/goods/{id} | — | `Long` | `MallGoodsDetailVO` | CatalogController.java:80 | |
| PUT | /profile | — | `ProfileSaveDTO` | `Void` | ProfileController.java:36 | |
| GET | /addresses | — | — | `List<AddressVO>` | AddressController.java:46 | |
| POST | /addresses | — | `AddressSaveDTO` | `Long` | AddressController.java:54 | |
| PUT | /addresses/{id} | — | `Long`, `AddressSaveDTO` | `Void` | AddressController.java:62 | |
| DELETE | /addresses/{id} | — | `Long` | `Void` | AddressController.java:71 | |
| POST | /addresses/{id}/default | — | `Long` | `Void` | AddressController.java:80 | |
| POST | /auth/phone | — | `ChangePhoneDTO` | `Void` | AuthController.java:86 | |

⚠ **本表按行分批实现**（契约先行）：**某一行是否已落地，以上表的「状态」列为准**
（留空 = 已实现，`待实现` = 已定契约、代码未写）——本文件正文不另记进度与条数，写死只会在下次改动时失真。
标 `待实现` 的行，路径 / 方法 / 权限串已定而代码尚未写，故 `声明位置` 填 `—`（写计划落点只会变成新的漂移点）；
实现完成后须在**同一改动内**把该行的「状态」摘回留空，否则检查器的反向哨兵会报错（见 [README.md](./README.md)）。
前端可照 `待实现` 的行先把页面写起来。

⚠ **权限串一律为空**：C 端顾客**不接 RBAC**（与店主端同理），本模块没有、也不应有任何 `@PreAuthorize`。
登录后顾客对自己的数据全权限——**这是预期状态，不是漏登记**。

### 形状与行为口径（表里放不下的）

| 项 | 口径 |
|---|---|
| 账号形态 | **账号即手机号**：`phone` 为登录账号（`mall_user` 唯一键 `uk_phone`）；`username` 不是独立列，导出到快照与 `CurrentUserVO` 时与 `phone` 同值 |
| 验证方式 | 手机号 + **短信验证码**，无密码。⚠ 短信为**模拟实现**：取码只打日志、不放真实短信、不落库、不落 Redis；校验与固定码 `panoramic.mall.sms-fixed-code`（默认 `888888`）比对 |
| 免鉴权路径 | `/auth/sms-code`、`/auth/register`、`/auth/login`、`/catalog/categories`（**两处各写一份**，见 [gateway.md](./gateway.md) 第三节）。⚠ `sms-code` 在登录**之前**被调用，漏登记则「获取验证码」直接 401；`/catalog/categories` 是**首页宫格分类树的精确路径**，不是 `/catalog/**` 前缀——写成前缀会把商品查询与详情一起放开到公网 |
| 鉴权分级 | **首页公开、一涉及商品查询与详情就要登录态**：免鉴权只有上一条那 4 条，`/catalog/goods`、`/catalog/facets`、`/catalog/goods/{id}` 一律需顾客登录态。前端配套三层：① 需登录页（`/search`、`/category/:id`、`/goods/:id`）由路由守卫拦，未登录带 `redirect` 跳 `/login`，登录后回原页；② 登录态**中途失效**由拦截器 401 兜底：清本地态 + 提示「请先登录」+ 带 `redirect` 跳登录页；③ **唯一静默的 401** 是路由守卫刷新重建用户态的 `/auth/me`（`silent401`）——公开首页上的重建失败不该把游客弹走。⚠ 静默分支**会先清掉本地 token**，所以同一导航里后续接口再吃 401 时，拦截器已判不出「本来有登录态」（既不提示、也不跳转）；**需登录页上「会话真没了」（`!getToken()`）必须由守卫自己收口**（跳登录页），不能推给拦截器，否则页面会渲染成「商品暂不可用」——把未登录报成下游故障。⚠ 判据带 `!getToken()` 是必要的：`me()` 因网络 / 5xx 失败时 token 未动，那种情况**不跳**（跳了会与「已登录不该待在登录页」来回弹成环） |
| 未认证响应 | HTTP **401** + `{code:401,msg}`（`common-auth` 的 `AuthenticationEntryPoint` 写出，网关侧同形）。⚠ 对本端前端而言 401 是**可预期的日常分支**（提示登录并跳转），不是故障：别把它与「商品暂不可用」那类下游降级混在一个出口里 |
| 错误码 | 验证码错误 `400`；手机号已注册 `400`；手机号未注册 `400`；新手机号与当前手机号相同 `400`（换绑）；账号停用 `USER_DISABLED`（`515`） |
| 校验顺序 | 注册：验码 → 手机号查重 → 建号；登录：验码 → 查账号 → 查状态；换绑：验旧码 → 验新码 → **拒绝同号 → 新号查重** |
| | ⚠ 换绑的「拒绝同号」必须**先于**「新号查重」：反过来的话新号 == 旧号会先被查重命中、报「手机号已注册」，刚登记的「新手机号与当前手机号相同」400 **永不可达**（spec §6.3 原写的顺序即反例，已订正） |
| 资料读口径 | **不单开 `GET /profile`**：资料读合并在 `/auth/me` —— 其出参含**完整资料**（`nickname` / `avatar` / `gender` / `birthday`）。要改资料走 `PUT /profile`（只写） |
| 资料写口径 | `PUT /profile` 是**整份替换**（四项全传，未传即写为 NULL）；资料页每次全量提交。要「只改一个字段」得先读 `/auth/me` 拿到完整资料再整体回传 |
| 昵称兜底 | 资料为空（或 customer-center 不可用）时 `nickname` **回退为手机号**，其余资料字段留空、**不阻断** `/auth/me`。⚠ **只在 `/auth/me` 一处兜底**，别在别处再写一份 |
| 换绑口径 | `POST /auth/phone` **双验证**（旧号码验证码 + 新号码验证码）；成功后**服务器登录态快照即时更新**，⚠ **不重签 token**（前端无需换 token、也不重新登录） |
| 登录态 | 签发 `type=user` 的 JWT，Redis 键 `panoramic:login:user:{userId}` |
| 身份类型绑定 | 本端只接受 `type=user` 的登录态（`panoramic.auth.user-type: user`）；跨端 token（`admin` / `store`）在 `AuthTokenFilter` 处即按未认证处理 → **HTTP 401**（见 [cross-cutting.md](./cross-cutting.md) 第 9 条） |
| 登出 | 删除 Redis 快照即服务端下线；本地 token 由前端清除 |
| 内部依赖 | **已启用** `@EnableFeignClients(basePackages = {"com.panoramic.contract.store", "com.panoramic.contract.goods", "com.panoramic.contract.customer"})`；分类树经 `GoodsCenterClient` 调 goods-center，商品分页 / 筛选聚合 / 详情经 `StoreClient` 调 store，顾客资料经 `CustomerCenterClient` 调 customer-center。编排集中在 `CatalogBffService` / `CustomerProfileBffService`，统一走 `common` 的 `BffFeignCall`（下游故障降级为「…暂不可用」，业务 4xx 原样透传）。⚠ 详情走的是 store 的 **platform 侧** `platformStoreGoodsDetail`（不带 `storeId`，与 admin BFF 同一个方法）——域侧照旧不做 C 端裁决，见 [store.md](./store.md) 第三节 |
| 静默降级的例外 | ⚠ **凡「拿不到只是增强、拿不到也照常出页面」的读，都不走 `BffFeignCall`**，而是在各自 service 里 catch + `log.warn` 后降级（如 `/auth/me` 的顾客资料读 `CustomerProfileBffService#loadProfile`、商品分页/facets 用的分类树 `CatalogBffService#categoryTreeOrEmpty`）。**此清单不在此穷举**——新增这类降级时在**该类自己的注释里**写明「为什么这条读可以静默降级」，不要把清单抄到这里（穷举数字就是下一个会烂掉的东西）。判定标准：拿不到它，页面是「少一块增强」还是「主内容没了」——后者必须走 `BffFeignCall` 抛出去 |
| C 端商品展示口径 | **固定在端 BFF，不在域**：调 store 的商品分页与 facets 时固定传 `shopStatus=2`（已审核通过店铺）+ `shelfStatus=1`（上架）+ `lockStatus=0`（未锁定）。域侧跨店通用接口**不含任何 C 端隐含约束**，漏传即把未过审店铺 / 平台锁定商品漏到前台（见 [cross-cutting.md](./cross-cutting.md) 第 17 条） |
| 详情可见性 | **与列表同一不变量**（上架 + 未锁定 + 店铺已过审），故详情能打开的商品一定能在列表里搜到、反之亦然。⚠ 与列表**判定位置不同**：列表把三个条件**当查询条件传给域**，详情拿不到查询条件（按 id 取一条），只能取回后**在 BFF 逐条重判**——两处别各写一套口径，改动时一起改 |
| 详情不可见 | 不存在 / 已下架 / 被平台锁定 / 店铺未过审 → 一律业务码 **404**「商品不存在或已下架」，**不区分原因、也不泄露商品存在性**（区分了就是给外人一个探测商品是否存在的接口）。⚠ **下游故障不是不可见**：只有业务 4xx 走 404，熔断 / 连接失败照抛 500「…暂不可用」 |
| 详情裁剪 | 域出参 `StoreGoodsSpuPlatformDetailVO` 是**管理端超集**（锁定四列 / `goodsSpuId` / `centerVersion` / `shelfStatus` / 含已下架的全部 SKU / SKU 的 `skuCode` 与 `spuId`）。C 端形状由 `CatalogBffService#toMallDetail` **逐字段手工映射**得出（不用 BeanUtils 拷贝），SKU 只留上架项——域 VO 日后加字段不会自动漏到前台（见 [cross-cutting.md](./cross-cutting.md) 第 19 条） |
| 库存展示口径 | SKU 出参带 `availableStock`（可用库存 = `stock − locked_stock`，域侧 `availableStock` 同义；本期 `locked_stock` 恒 0）。0 即售罄。⚠ **`warn_stock` 不进 C 端**；⚠ **库存不参与详情可见性**（下架 / 平台锁定 / 店铺未过审那三条不变，售罄商品照常可打开、不 404）；⚠ 该字段由 `toMallSku` **逐字段手工映射**，与 `description` 消毒同属出口处理 |
| 详情描述 | `description` 是店主自由录入的**富文本**（store / admin 两端录入框提示语「支持 HTML」、库列注释「商品详情（富文本）」），域侧原样存取、不清洗。**C 端出参是清洗过的 HTML**：`CatalogBffService#toMallDetail` 出口调 common 的 `HtmlSanitizer.sanitizeRichText`（`Safelist.relaxed`：剥 `script` / `on*` 事件属性 / `style`，`a[href]` 限 ftp/http/https/mailto、`img[src]` 限 http/https；无标签的纯文本按换行折 `<br>` 后走同一套清洗）。⚠ **白名单只此一份、各端 BFF 出口共用**（admin 端同一字段走同一件），前端 `v-html` 直接渲染、不再自行清洗（前端各自引清洗库，漏一个就是一处 XSS；见 [cross-cutting.md](./cross-cutting.md) 第 21 条） |

## 三、前端契约的**视觉与结构**基准

mall 前台的**页面契约**（长什么样、分哪几块）由前端工程自身固定，不是本文档：

- 风格与结构基准：`frontend/mall`（Vue 3 + Vite + TypeScript，端口 5175）
- 约束条文：根 `CLAUDE.md` 的「mall 前台（用户端）视觉与结构约定」

⚠ 该约定约束的是**视觉与结构，不是技术形态**：前端按 BFF 分层走，但**长什么样、分哪几块以 `frontend/mall` 为准**，
且「基准先行」——新增区块先在该工程里改好、定了，再往外铺。
⚠ 前端**账号页已接入本表 5 条账号接口**（`/login`、`/register` 两页 + 顶栏登录态 + 守卫的刷新重建，
接入点见 `frontend/mall/src/api/auth.ts`）；**搜索区与分类展示区已接本表 catalog 的分类树 / 商品分页 / facets 三个接口**
（接入点 `frontend/mall/src/api/catalog.ts`，页面见 `src/views/GoodsListView.vue`）；
**商品详情页已接 `/catalog/goods/{id}`**（页面 `src/views/GoodsDetailView.vue`，路由 `/goods/:id`，
列表卡的整卡链接进入）。
⚠ 详情页的两条渲染口径（**都由本契约定，不是前端偏好**）：① `description` **按 HTML 渲染**（`v-html`）——
它是店主录入的富文本，**出参已由本端出口清洗**（见上表「详情描述」），前端不必也不得再自己拼一遍 HTML；
② **不做「加入购物车 / 立即购买」**——后端没有购物车与下单接口，只展示商品信息。
⚠ 前端**登录门禁**的落点：路由 `meta.requiresAuth`（`src/router/index.ts`）+ 拦截器 401 出口
（`src/api/request.ts` 的 `sessionExpired`），口径见上表「鉴权分级」。
⚠ 首页「**热门商品列表**」区块**仍是静态 mock**（`frontend/mall/src/mock/`）。
