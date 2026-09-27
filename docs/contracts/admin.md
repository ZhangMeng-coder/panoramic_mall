<!-- contract-meta
service: admin
layer: page
baseUrl: /admin
scanDirs: backend/admin/src/main/java/com/panoramic/admin/controller
typeDirs: backend/goods-center-interface/src/main/java, backend/store-interface/src/main/java, backend/trade-center-interface/src/main/java, backend/common/src/main/java, backend/admin/src/main/java
-->

# 平台管理端 BFF（admin）对外契约 · 第 ① 层

> 平台管理后台接口（8082），经网关 `/admin/**` 对外（`StripPrefix=1` 后落到本服务的
> `/auth/**`、`/roles/**`、`/users/**`、`/permissions/**`、`/shop/**`、`/goods/**`、`/orders/**`）。
> 签发 `type=admin` 的登录令牌；是本仓库**接口最多的服务**，也是 `@PreAuthorize` 授权的**唯一位置**。

**共 58 个接口 / 11 个 Controller**。

## 一、接口清单

### AuthController — `/auth`（4）

| 方法 | 路径 | 权限串 | 入参 | 出参 | 声明位置 | 状态 |
|---|---|---|---|---|---|---|
| POST | /auth/login | — | LoginDTO | RespData<LoginResultVO> | `AuthController#login` |  |
| POST | /auth/logout | — | — | RespData<Void> | `AuthController#logout` |  |
| GET | /auth/me | — | — | RespData<CurrentUserVO> | `AuthController#me` |  |
| PUT | /auth/password | — | ChangePasswordDTO | RespData<Void> | `AuthController#changePassword` |  |

### RoleController — `/roles`（10）

| 方法 | 路径 | 权限串 | 入参 | 出参 | 声明位置 | 状态 |
|---|---|---|---|---|---|---|
| GET | /roles/page | system:role:list | RolePageQueryDTO | RespData<PageResult<RoleVO>> | `RoleController#page` |  |
| GET | /roles/list | system:role:list | — | RespData<List<RoleVO>> | `RoleController#list` |  |
| GET | /roles/{id} | system:role:list | Long | RespData<RoleVO> | `RoleController#detail` |  |
| POST | /roles | system:role:add | RoleSaveDTO | RespData<Long> | `RoleController#save` |  |
| PUT | /roles/{id} | system:role:edit | Long, RoleUpdateDTO | RespData<Void> | `RoleController#update` |  |
| DELETE | /roles/{id} | system:role:delete | Long | RespData<Void> | `RoleController#delete` |  |
| GET | /roles/{id}/permissions | system:role:assignPermission | Long | RespData<List<Long>> | `RoleController#permissionIds` |  |
| PUT | /roles/{id}/permissions | system:role:assignPermission | Long, RolePermissionIdsDTO | RespData<Void> | `RoleController#assignPermissions` |  |
| GET | /roles/{id}/user-ids | system:role:assignUser | Long | RespData<List<Long>> | `RoleController#userRoleIds` |  |
| PUT | /roles/{id}/users | system:role:assignUser | Long, RoleUserIdsDTO | RespData<Void> | `RoleController#assignUsers` |  |

### UserController — `/users`（8）

| 方法 | 路径 | 权限串 | 入参 | 出参 | 声明位置 | 状态 |
|---|---|---|---|---|---|---|
| GET | /users/page | system:user:list | UserPageQueryDTO | RespData<PageResult<UserVO>> | `UserController#page` |  |
| GET | /users/{id} | system:user:list | Long | RespData<UserVO> | `UserController#detail` |  |
| POST | /users | system:user:add | UserSaveDTO | RespData<Long> | `UserController#save` |  |
| PUT | /users/{id} | system:user:edit | Long, UserUpdateDTO | RespData<Void> | `UserController#update` |  |
| DELETE | /users/{id} | system:user:delete | Long | RespData<Void> | `UserController#delete` |  |
| GET | /users/{id}/roles | system:user:assignRole | Long | RespData<List<Long>> | `UserController#roleIds` |  |
| PUT | /users/{id}/roles | system:user:assignRole | Long, UserRoleIdsDTO | RespData<Void> | `UserController#assignRoles` |  |
| GET | /users/unassigned/page | system:user:list | Long, RoleUnassignedUserPageQueryDTO | RespData<PageResult<UserVO>> | `UserController#unassignedUsersPage` |  |

### PermissionController — `/permissions`（6）

| 方法 | 路径 | 权限串 | 入参 | 出参 | 声明位置 | 状态 |
|---|---|---|---|---|---|---|
| GET | /permissions/tree | system:permission:list | — | RespData<List<PermissionTreeVO>> | `PermissionController#tree` |  |
| GET | /permissions/menus | — | — | RespData<List<PermissionTreeVO>> | `PermissionController#menus` |  |
| GET | /permissions/{id} | system:permission:list | Long | RespData<PermissionTreeVO> | `PermissionController#detail` |  |
| POST | /permissions | system:permission:add | PermissionSaveDTO | RespData<Long> | `PermissionController#save` |  |
| PUT | /permissions/{id} | system:permission:edit | Long, PermissionUpdateDTO | RespData<Void> | `PermissionController#update` |  |
| DELETE | /permissions/{id} | system:permission:delete | Long | RespData<Void> | `PermissionController#delete` |  |

### shop/ShopController — `/shop/shops`（3）

| 方法 | 路径 | 权限串 | 入参 | 出参 | 声明位置 | 状态 |
|---|---|---|---|---|---|---|
| GET | /shop/shops | store:shop:list | ShopPageQueryDTO | RespData<PageResult<ShopVO>> | `ShopController#page` |  |
| GET | /shop/shops/{id} | store:shop:list | Long | RespData<ShopVO> | `ShopController#detail` |  |
| POST | /shop/shops/{id}/audit | store:shop:audit | Long, ShopAuditDTO | RespData<Void> | `ShopController#audit` |  |

### shop/ShopGoodsController — `/shop/goods`（7）

| 方法 | 路径 | 权限串 | 入参 | 出参 | 声明位置 | 状态 |
|---|---|---|---|---|---|---|
| GET | /shop/goods/page | store:goods:list | ShopGoodsPageQueryDTO | RespData<PageResult<StoreGoodsSpuCrossShopPageItemVO>> | `ShopGoodsController#page` |  |
| GET | /shop/goods/{id} | store:goods:list | Long | RespData<StoreGoodsSpuPlatformDetailVO> | `ShopGoodsController#detail` |  |
| POST | /shop/goods/{id}/lock | store:goods:lock | Long, StoreGoodsLockDTO | RespData<Void> | `ShopGoodsController#lock` |  |
| POST | /shop/goods/{id}/unlock | store:goods:lock | Long | RespData<Void> | `ShopGoodsController#unlock` |  |
| GET | /shop/goods/categories | store:goods:list | — | RespData<List<CategoryTreeVO>> | `ShopGoodsController#categoryTree` |  |
| GET | /shop/goods/brands | store:goods:list | — | RespData<List<BrandVO>> | `ShopGoodsController#brands` |  |
| GET | /shop/goods/shops | store:goods:list | — | RespData<List<ShopOptionVO>> | `ShopGoodsController#shopOptions` |  |

### goods/GoodsSpuController — `/goods/spu`（7）

| 方法 | 路径 | 权限串 | 入参 | 出参 | 声明位置 | 状态 |
|---|---|---|---|---|---|---|
| GET | /goods/spu/page | goods:spu:list | SpuPageQueryDTO | RespData<PageResult<SpuPageItemVO>> | `GoodsSpuController#page` |  |
| GET | /goods/spu/{id} | goods:spu:list | Long | RespData<SpuDetailVO> | `GoodsSpuController#detail` |  |
| POST | /goods/spu | goods:spu:add | SpuSaveDTO | RespData<Long> | `GoodsSpuController#save` |  |
| PUT | /goods/spu/{id} | goods:spu:edit | Long, SpuUpdateDTO | RespData<Void> | `GoodsSpuController#update` |  |
| PUT | /goods/spu/{id}/skus | goods:spu:edit | Long, SpuSkuReplaceDTO | RespData<Void> | `GoodsSpuController#replaceSkus` |  |
| PUT | /goods/spu/{id}/status | goods:spu:edit | Long, SpuStatusDTO | RespData<Void> | `GoodsSpuController#updateStatus` |  |
| DELETE | /goods/spu/{id} | goods:spu:delete | Long | RespData<Void> | `GoodsSpuController#delete` |  |

### goods/GoodsCategoryController — `/goods/categories`（4）

| 方法 | 路径 | 权限串 | 入参 | 出参 | 声明位置 | 状态 |
|---|---|---|---|---|---|---|
| POST | /goods/categories | goods:category:add | CategorySaveDTO | RespData<Long> | `GoodsCategoryController#save` |  |
| GET | /goods/categories/tree | goods:category:list | — | RespData<List<CategoryTreeVO>> | `GoodsCategoryController#tree` |  |
| PUT | /goods/categories/{id} | goods:category:edit | Long, CategoryUpdateDTO | RespData<Void> | `GoodsCategoryController#update` |  |
| DELETE | /goods/categories/{id} | goods:category:delete | Long | RespData<Void> | `GoodsCategoryController#delete` |  |

### goods/GoodsBrandController — `/goods/brands`（6）

| 方法 | 路径 | 权限串 | 入参 | 出参 | 声明位置 | 状态 |
|---|---|---|---|---|---|---|
| GET | /goods/brands/page | goods:brand:list | BrandPageQueryDTO | RespData<PageResult<BrandVO>> | `GoodsBrandController#page` |  |
| GET | /goods/brands/list | goods:brand:list | — | RespData<List<BrandVO>> | `GoodsBrandController#list` |  |
| GET | /goods/brands/{id} | goods:brand:list | Long | RespData<BrandVO> | `GoodsBrandController#detail` |  |
| POST | /goods/brands | goods:brand:add | BrandSaveDTO | RespData<Long> | `GoodsBrandController#save` |  |
| PUT | /goods/brands/{id} | goods:brand:edit | Long, BrandUpdateDTO | RespData<Void> | `GoodsBrandController#update` |  |
| DELETE | /goods/brands/{id} | goods:brand:delete | Long | RespData<Void> | `GoodsBrandController#delete` |  |

### order/OrderController — `/orders`（2）

| 方法 | 路径 | 权限串 | 入参 | 出参 | 声明位置 | 状态 |
|---|---|---|---|---|---|---|
| GET | /orders/page | trade:order:list | OrderPageQueryDTO | RespData<PageResult<TradeOrderVO>> | `OrderController#page` |  |
| GET | /orders/{orderNo} | trade:order:list | String | RespData<TradeOrderVO> | `OrderController#detail` |  |

### stats/StatsController — `/stats`（1）

| 方法 | 路径 | 权限串 | 入参 | 出参 | 声明位置 | 状态 |
|---|---|---|---|---|---|---|
| GET | /stats/overview | — | AdminStatsQueryDTO | RespData<AdminStatsVO> | `StatsController#overview` |  |

> ⚠ **平台首页数据看板 `GET /stats/overview`**（2026-09-27 落契约）—— 8 个指标挤在**一个**接口里，是**有意**的：
> 首页一次加载要么全有要么全无，拆成 4 个接口只会带来 4 次往返、4 份 loading 态。
> - **无 `@PreAuthorize`**，与 `/permissions/menus` 同类（登录后必得）——理由见第三节。
> - ⚠ **这是本层第一次调 customer-center**（此前只调 goods-center / store / trade-center），
>   也是 admin **首次读取顾客侧数据**：需在 `AdminApplication` 的 `@EnableFeignClients` 加扫
>   `com.panoramic.contract.customer` 包、并在 `pom.xml` 加 `customer-center-interface` 依赖。
> - ⚠ **时间窗口与分桶粒度在 `common` 解析，本层只是发起方**（`AdminStatsQueryDTO.window` → 显式 `start` / `end`）：
>   三个域**只收显式时间、一律按天出点**；「本月 / 上季 / 今年」的日历算术与「按天 → 按月」的归并
>   2026-09-27 起落在 `com.panoramic.common.stats`（`StatsWindows` / `StatsSeriesMerger`），
>   与店主端看板**共用同一份**。⚠ 别把窗口枚举沉到域里——那会让月/季/年算术在每个域各存一份、各自漂移。
> - ⚠ **指标的时间基准分两类**：`userCount` / `shopCount` / `goodsCount` 是**当前累计快照**（不受窗口影响）；
>   其余 5 个按窗口算。⚠ 前一类的口径是**近似**——`userCount` 数的是顾客资料行，**不等于**注册用户数
>   （偏差两个方向都有，见 [customer-center.md](./customer-center.md)）；对外文案不得写成「注册用户数」。
> - **成交比例由本层算**：域只回分子分母两个计数（域不产出「已经除过的数」），除零处置也在本层。
> - 前端 `v-perm` 与本行**都不挂权限串**（本接口不进 `sys_permission` 种子），故第 16 条的三方一致**不适用**。

> ⚠ **管理端对订单只读**——只有这两个查询端点，**没有任何写动作**（改状态 / 改单 / 删单都不做）。
> 订单的状态流转入口只在两端：C 端 `pay` / `receive` / `cancel` / `refund`、商户端 `ship`（见 [mall-bff.md](./mall-bff.md) 与 [store-bff.md](./store-bff.md)）。

> ⚠ 平台侧**没有锚点**（管理端是全量视角）：筛选（`storeId` / `customerId` / `orderNo` / `status`）走
> `OrderPageQueryDTO`（**admin 本地**编排查询对象，与 `ShopGoodsPageQueryDTO` 同款做法）；
> 自增 id **不出现在契约里**，路径标识用 **`orderNo`**。出参 `TradeOrderVO` 是域契约类型，直接下发。

> ⚠ **`trade:order:list` 的三方一致**：`sys_permission` 种子（目录 `5 订单管理` → 页 `51 订单列表`（`route=/order`）
> → 按钮 `511 订单查询`，按「目录 X → 页 X1 → 按钮 X11+」）、**本行**与**前端 `v-perm`** 三方均已就位
> （前端那侧由 `87cbaa3` 补齐）——见 [cross-cutting.md](./cross-cutting.md) 第 16 条。
> ⚠ 但这一项**检查器守不住**（第 3 项只查「`v-perm` ⊆ 契约表 / 种子」一向，**少了 `v-perm` 不报**），
> 后续改动仍只能靠人工核对。

> 「路径」列不带网关前缀 `/admin`。例：`/shop/goods/page` 对外完整路径是 `/admin/shop/goods/page`。

## 二、权限串族

各权限串已在上一节逐行登记；族分组即各 Controller 小节。三方一致（`@PreAuthorize` 字面量 ↔
`sys_permission.perms` 种子 ↔ 前端 `v-perm`）由检查器第 2、3 项核对，见 [cross-cutting.md](./cross-cutting.md) 第 16 条。

## 三、**无 `@PreAuthorize`** 的接口（3 类 / 6 个端点，属预期）

| 接口 | 为何无授权 |
|---|---|
| `/auth/login`、`/auth/logout`、`/auth/me`、`/auth/password` | 登录链本身；`/auth/login` 在网关与服务两处白名单内免鉴权，其余靠"已登录"门槛 |
| `/permissions/menus` | 当前登录用户渲染**自己的**侧栏菜单，属登录后必得数据，不设权限串 |
| `/stats/overview` | 平台首页数据看板。⚠ **主页刻意不入权限表**（`admin/src/main/resources/db/schema.sql:103`：主页 `/home` 不再入权限表，由前端写死置顶菜单），故其数据接口也必须是「登录后必得」——给本接口挂权限串，会让**没有该权限的管理员落到一个取不到数的首页**。⚠ 若将来要限制看板可见范围，两边要**同时**改（主页入权限表 + 本接口挂权限串），只改一边就是坏的主页 |

## 四、形状规则

- ✅ **必包 `RespData`**（58/58）；✅ 授权**只在此层**（`@PreAuthorize`）——见 [cross-cutting.md](./cross-cutting.md) 第 1、2 条。
- ⚠ 本层**只编排，不持域实体**（`/goods/**` 与 `/shop/**` 全部经内部 Feign 下沉到 goods-center / store）。
- ⚠ 本层编排与出口口径：分类子树展开、分类全路径补全、锁定人渲染、店铺商品描述消毒、身份类型绑定 ——
  规则本体见 [`backend/admin/README.md`](../../backend/admin/README.md) 与
  [cross-cutting.md](./cross-cutting.md) 第 9、21 条。

## 五、类型所在

| 来源 | 类型 |
|---|---|
| **admin 本地**（`admin/dto`、`admin/vo`） | RBAC 与登录：LoginDTO, ChangePasswordDTO, LoginResultVO, CurrentUserVO, RolePageQueryDTO, RoleSaveDTO, RoleUpdateDTO, RoleVO, RolePermissionIdsDTO, RoleUserIdsDTO, RoleUnassignedUserPageQueryDTO, UserPageQueryDTO, UserSaveDTO, UserUpdateDTO, UserVO, UserRoleIdsDTO, PermissionSaveDTO, PermissionUpdateDTO, PermissionTreeVO；编排专用：ShopGoodsPageQueryDTO, OrderPageQueryDTO；首页看板：AdminStatsQueryDTO, AdminStatsVO |
| `common`（`com.panoramic.common.stats`，**两个端 BFF 共用**、域不得引用） | 首页看板用的窗口与折线类型：StatsWindow, StatsGrain, StatsPointVO, StatsDateRange, StatsWindows, StatsSeriesMerger（**同一份**，职责说明见 [store-bff.md](./store-bff.md) 第四节） |
| `trade-center-interface`（`com.panoramic.contract.trade.vo`） | 订单：TradeOrderVO |
| 两个接口模块（`com.panoramic.contract.goods.*` 在 `goods-center-interface`；`.store.*` 在 `store-interface`） | 商品模板：SpuPageQueryDTO, SpuSaveDTO, SpuUpdateDTO, SpuSkuReplaceDTO, SpuStatusDTO, SpuPageItemVO, SpuDetailVO, CategorySaveDTO, CategoryUpdateDTO, CategoryTreeVO, BrandPageQueryDTO, BrandSaveDTO, BrandUpdateDTO, BrandVO；店铺类型清单见 [store.md](./store.md) 第五节 |

> `LoginResultVO` / `CurrentUserVO` 两端**各持一份**（不共享，身份空间不同）——见 [store-bff.md](./store-bff.md) 第四节。
> `ShopPageQueryDTO` / `ShopAuditDTO` / `ShopVO` 在 `contract.store`（域与 admin 共用），`ShopGoodsPageQueryDTO` 是 **admin 本地**的编排查询对象。

## 六、下游依赖（本层调谁）

| 目标 | 通道 | 内容 |
|---|---|---|
| goods-center(8081) | Feign `GoodsCenterClient` | 分类 / 品牌 / 标准 SPU-SKU 模板的 CRUD；分类树与分类全路径 |
| store(8083) | Feign `StoreClient`（跨店通用能力：**不传作用域 = 全量**） | 店铺分页 / 详情 / 审核；店铺商品跨店分页 / 详情 / 锁定 / 解锁；店铺下拉。⚠ 店铺详情走 `getShop`（域侧**查不到返空**，404 文案由本层定）；商品详情走 `storeGoodsDetail`，跨店视角**不传** `storeId` |
| trade-center(8087) | Feign `TradeCenterClient` | 平台侧订单分页 / 详情（**只读**，无写动作）；首页看板的订单统计（`getOrderStats`，2026-09-27 起） |
| customer-center(8086) | Feign `CustomerCenterClient` | 首页看板的顾客资料计数与新增顾客折线（`getCustomerStats`）。⚠ **本层 2026-09-27 起才开始调它**（此前只调前三个域），需同步加扫 `com.panoramic.contract.customer` 包与 `customer-center-interface` 依赖 |

全部经 `common` 的 `BffFeignCall` 包装。降级口径见 [cross-cutting.md](./cross-cutting.md) 第 13 条。
`ShopGoodsBffService` 与 `StoreShopBffService` 是本层两个主要编排类。

## 七、业务规则去哪看

RBAC 模型、权限种子 id 约定（目录 X → 页 X1 → 按钮 X11+）、店铺审核状态机、店铺商品锁定语义等见
[`backend/admin/README.md`](../../backend/admin/README.md)（服务说明）与 [`backend/store/README.md`](../../backend/store/README.md)。
