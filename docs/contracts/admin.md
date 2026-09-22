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

**共 57 个接口 / 10 个 Controller**。

## 一、接口清单

### AuthController — `/auth`（4）

| 方法 | 路径 | 权限串 | 入参 | 出参 | 声明位置 | 状态 |
|---|---|---|---|---|---|---|
| POST | /auth/login | — | LoginDTO | RespData<LoginResultVO> | AuthController.java:37 |  |
| POST | /auth/logout | — | — | RespData<Void> | AuthController.java:45 |  |
| GET | /auth/me | — | — | RespData<CurrentUserVO> | AuthController.java:57 |  |
| PUT | /auth/password | — | ChangePasswordDTO | RespData<Void> | AuthController.java:65 |  |

### RoleController — `/roles`（10）

| 方法 | 路径 | 权限串 | 入参 | 出参 | 声明位置 | 状态 |
|---|---|---|---|---|---|---|
| GET | /roles/page | system:role:list | RolePageQueryDTO | RespData<PageResult<RoleVO>> | RoleController.java:41 |  |
| GET | /roles/list | system:role:list | — | RespData<List<RoleVO>> | RoleController.java:50 |  |
| GET | /roles/{id} | system:role:list | Long | RespData<RoleVO> | RoleController.java:59 |  |
| POST | /roles | system:role:add | RoleSaveDTO | RespData<Long> | RoleController.java:68 |  |
| PUT | /roles/{id} | system:role:edit | Long, RoleUpdateDTO | RespData<Void> | RoleController.java:77 |  |
| DELETE | /roles/{id} | system:role:delete | Long | RespData<Void> | RoleController.java:88 |  |
| GET | /roles/{id}/permissions | system:role:assignPermission | Long | RespData<List<Long>> | RoleController.java:98 |  |
| PUT | /roles/{id}/permissions | system:role:assignPermission | Long, RolePermissionIdsDTO | RespData<Void> | RoleController.java:107 |  |
| GET | /roles/{id}/user-ids | system:role:assignUser | Long | RespData<List<Long>> | RoleController.java:118 |  |
| PUT | /roles/{id}/users | system:role:assignUser | Long, RoleUserIdsDTO | RespData<Void> | RoleController.java:127 |  |

### UserController — `/users`（8）

| 方法 | 路径 | 权限串 | 入参 | 出参 | 声明位置 | 状态 |
|---|---|---|---|---|---|---|
| GET | /users/page | system:user:list | UserPageQueryDTO | RespData<PageResult<UserVO>> | UserController.java:42 |  |
| GET | /users/{id} | system:user:list | Long | RespData<UserVO> | UserController.java:51 |  |
| POST | /users | system:user:add | UserSaveDTO | RespData<Long> | UserController.java:60 |  |
| PUT | /users/{id} | system:user:edit | Long, UserUpdateDTO | RespData<Void> | UserController.java:69 |  |
| DELETE | /users/{id} | system:user:delete | Long | RespData<Void> | UserController.java:80 |  |
| GET | /users/{id}/roles | system:user:assignRole | Long | RespData<List<Long>> | UserController.java:90 |  |
| PUT | /users/{id}/roles | system:user:assignRole | Long, UserRoleIdsDTO | RespData<Void> | UserController.java:99 |  |
| GET | /users/unassigned/page | system:user:list | Long, RoleUnassignedUserPageQueryDTO | RespData<PageResult<UserVO>> | UserController.java:110 |  |

### PermissionController — `/permissions`（6）

| 方法 | 路径 | 权限串 | 入参 | 出参 | 声明位置 | 状态 |
|---|---|---|---|---|---|---|
| GET | /permissions/tree | system:permission:list | — | RespData<List<PermissionTreeVO>> | PermissionController.java:38 |  |
| GET | /permissions/menus | — | — | RespData<List<PermissionTreeVO>> | PermissionController.java:47 |  |
| GET | /permissions/{id} | system:permission:list | Long | RespData<PermissionTreeVO> | PermissionController.java:55 |  |
| POST | /permissions | system:permission:add | PermissionSaveDTO | RespData<Long> | PermissionController.java:64 |  |
| PUT | /permissions/{id} | system:permission:edit | Long, PermissionUpdateDTO | RespData<Void> | PermissionController.java:73 |  |
| DELETE | /permissions/{id} | system:permission:delete | Long | RespData<Void> | PermissionController.java:84 |  |

### shop/ShopController — `/shop/shops`（3）

| 方法 | 路径 | 权限串 | 入参 | 出参 | 声明位置 | 状态 |
|---|---|---|---|---|---|---|
| GET | /shop/shops | store:shop:list | ShopPageQueryDTO | RespData<PageResult<ShopVO>> | ShopController.java:37 |  |
| GET | /shop/shops/{id} | store:shop:list | Long | RespData<ShopVO> | ShopController.java:46 |  |
| POST | /shop/shops/{id}/audit | store:shop:audit | Long, ShopAuditDTO | RespData<Void> | ShopController.java:55 |  |

### shop/ShopGoodsController — `/shop/goods`（7）

| 方法 | 路径 | 权限串 | 入参 | 出参 | 声明位置 | 状态 |
|---|---|---|---|---|---|---|
| GET | /shop/goods/page | store:goods:list | ShopGoodsPageQueryDTO | RespData<PageResult<StoreGoodsSpuCrossShopPageItemVO>> | ShopGoodsController.java:48 |  |
| GET | /shop/goods/{id} | store:goods:list | Long | RespData<StoreGoodsSpuPlatformDetailVO> | ShopGoodsController.java:57 |  |
| POST | /shop/goods/{id}/lock | store:goods:lock | Long, StoreGoodsLockDTO | RespData<Void> | ShopGoodsController.java:66 |  |
| POST | /shop/goods/{id}/unlock | store:goods:lock | Long | RespData<Void> | ShopGoodsController.java:77 |  |
| GET | /shop/goods/categories | store:goods:list | — | RespData<List<CategoryTreeVO>> | ShopGoodsController.java:87 |  |
| GET | /shop/goods/brands | store:goods:list | — | RespData<List<BrandVO>> | ShopGoodsController.java:96 |  |
| GET | /shop/goods/shops | store:goods:list | — | RespData<List<ShopOptionVO>> | ShopGoodsController.java:105 |  |

### goods/GoodsSpuController — `/goods/spu`（7）

| 方法 | 路径 | 权限串 | 入参 | 出参 | 声明位置 | 状态 |
|---|---|---|---|---|---|---|
| GET | /goods/spu/page | goods:spu:list | SpuPageQueryDTO | RespData<PageResult<SpuPageItemVO>> | GoodsSpuController.java:42 |  |
| GET | /goods/spu/{id} | goods:spu:list | Long | RespData<SpuDetailVO> | GoodsSpuController.java:51 |  |
| POST | /goods/spu | goods:spu:add | SpuSaveDTO | RespData<Long> | GoodsSpuController.java:60 |  |
| PUT | /goods/spu/{id} | goods:spu:edit | Long, SpuUpdateDTO | RespData<Void> | GoodsSpuController.java:69 |  |
| PUT | /goods/spu/{id}/skus | goods:spu:edit | Long, SpuSkuReplaceDTO | RespData<Void> | GoodsSpuController.java:80 |  |
| PUT | /goods/spu/{id}/status | goods:spu:edit | Long, SpuStatusDTO | RespData<Void> | GoodsSpuController.java:91 |  |
| DELETE | /goods/spu/{id} | goods:spu:delete | Long | RespData<Void> | GoodsSpuController.java:102 |  |

### goods/GoodsCategoryController — `/goods/categories`（4）

| 方法 | 路径 | 权限串 | 入参 | 出参 | 声明位置 | 状态 |
|---|---|---|---|---|---|---|
| POST | /goods/categories | goods:category:add | CategorySaveDTO | RespData<Long> | GoodsCategoryController.java:39 |  |
| GET | /goods/categories/tree | goods:category:list | — | RespData<List<CategoryTreeVO>> | GoodsCategoryController.java:48 |  |
| PUT | /goods/categories/{id} | goods:category:edit | Long, CategoryUpdateDTO | RespData<Void> | GoodsCategoryController.java:57 |  |
| DELETE | /goods/categories/{id} | goods:category:delete | Long | RespData<Void> | GoodsCategoryController.java:68 |  |

### goods/GoodsBrandController — `/goods/brands`（6）

| 方法 | 路径 | 权限串 | 入参 | 出参 | 声明位置 | 状态 |
|---|---|---|---|---|---|---|
| GET | /goods/brands/page | goods:brand:list | BrandPageQueryDTO | RespData<PageResult<BrandVO>> | GoodsBrandController.java:41 |  |
| GET | /goods/brands/list | goods:brand:list | — | RespData<List<BrandVO>> | GoodsBrandController.java:50 |  |
| GET | /goods/brands/{id} | goods:brand:list | Long | RespData<BrandVO> | GoodsBrandController.java:59 |  |
| POST | /goods/brands | goods:brand:add | BrandSaveDTO | RespData<Long> | GoodsBrandController.java:68 |  |
| PUT | /goods/brands/{id} | goods:brand:edit | Long, BrandUpdateDTO | RespData<Void> | GoodsBrandController.java:77 |  |
| DELETE | /goods/brands/{id} | goods:brand:delete | Long | RespData<Void> | GoodsBrandController.java:88 |  |

### order/OrderController — `/orders`（2）

| 方法 | 路径 | 权限串 | 入参 | 出参 | 声明位置 | 状态 |
|---|---|---|---|---|---|---|
| GET | /orders/page | trade:order:list | OrderPageQueryDTO | RespData<PageResult<TradeOrderVO>> | OrderController.java:45 |  |
| GET | /orders/{orderNo} | trade:order:list | String | RespData<TradeOrderVO> | OrderController.java:54 |  |

> ⚠ **管理端对订单只读**——只有这两个查询端点，**没有任何写动作**（改状态 / 改单 / 删单都不做）。
> 订单的状态流转入口只在两端：C 端 `pay` / `receive`、商户端 `ship`（见 [mall-bff.md](./mall-bff.md) 与 [store-bff.md](./store-bff.md)）。

> ⚠ 平台侧**没有锚点**（管理端是全量视角）：筛选（`storeId` / `customerId` / `orderNo` / `status`）走
> `OrderPageQueryDTO`（**admin 本地**编排查询对象，与 `ShopGoodsPageQueryDTO` 同款做法）；
> 自增 id **不出现在契约里**，路径标识用 **`orderNo`**。出参 `TradeOrderVO` 是域契约类型，直接下发。

> ⚠ **`trade:order:list` 的三方一致**：`sys_permission` 种子（目录 `5 订单管理` → 页 `51 订单列表`（`route=/order`）
> → 按钮 `511 订单查询`，按「目录 X → 页 X1 → 按钮 X11+」）与**本行**已就位——见
> [cross-cutting.md](./cross-cutting.md) 第 16 条。⚠ 前端 `v-perm` 属**前端任务**（admin 前端订单页），
> **尚未**就位，故此处**不是**「三方已齐」；且这一项检查器守不住（第 3 项只查「`v-perm` ⊆ 契约表 / 种子」
> 一向，**少了 `v-perm` 不报**），补齐只能靠人工核对。

> 「路径」列不带网关前缀 `/admin`。例：`/shop/goods/page` 对外完整路径是 `/admin/shop/goods/page`。

## 二、权限串族

各权限串已在上一节逐行登记；族分组即各 Controller 小节。三方一致（`@PreAuthorize` 字面量 ↔
`sys_permission.perms` 种子 ↔ 前端 `v-perm`）由检查器第 2、3 项核对，见 [cross-cutting.md](./cross-cutting.md) 第 16 条。

## 三、**无 `@PreAuthorize`** 的接口（2 类 / 5 个端点，属预期）

| 接口 | 为何无授权 |
|---|---|
| `/auth/login`、`/auth/logout`、`/auth/me`、`/auth/password` | 登录链本身；`/auth/login` 在网关与服务两处白名单内免鉴权，其余靠"已登录"门槛 |
| `/permissions/menus` | 当前登录用户渲染**自己的**侧栏菜单，属登录后必得数据，不设权限串 |

## 四、形状规则

- ✅ **必包 `RespData`**（57/57）；✅ 授权**只在此层**（`@PreAuthorize`）——见 [cross-cutting.md](./cross-cutting.md) 第 1、2 条。
- ⚠ 本层**只编排，不持域实体**（`/goods/**` 与 `/shop/**` 全部经内部 Feign 下沉到 goods-center / store）。
- ⚠ 本层编排与出口口径：分类子树展开、分类全路径补全、锁定人渲染、店铺商品描述消毒、身份类型绑定 ——
  规则本体见 [`backend/admin/README.md`](../../backend/admin/README.md) 与
  [cross-cutting.md](./cross-cutting.md) 第 9、21 条。

## 五、类型所在

| 来源 | 类型 |
|---|---|
| **admin 本地**（`admin/dto`、`admin/vo`） | RBAC 与登录：LoginDTO, ChangePasswordDTO, LoginResultVO, CurrentUserVO, RolePageQueryDTO, RoleSaveDTO, RoleUpdateDTO, RoleVO, RolePermissionIdsDTO, RoleUserIdsDTO, RoleUnassignedUserPageQueryDTO, UserPageQueryDTO, UserSaveDTO, UserUpdateDTO, UserVO, UserRoleIdsDTO, PermissionSaveDTO, PermissionUpdateDTO, PermissionTreeVO；编排专用：ShopGoodsPageQueryDTO, OrderPageQueryDTO |
| `trade-center-interface`（`com.panoramic.contract.trade.vo`） | 订单：TradeOrderVO |
| 两个接口模块（`com.panoramic.contract.goods.*` 在 `goods-center-interface`；`.store.*` 在 `store-interface`） | 商品模板：SpuPageQueryDTO, SpuSaveDTO, SpuUpdateDTO, SpuSkuReplaceDTO, SpuStatusDTO, SpuPageItemVO, SpuDetailVO, CategorySaveDTO, CategoryUpdateDTO, CategoryTreeVO, BrandPageQueryDTO, BrandSaveDTO, BrandUpdateDTO, BrandVO；店铺类型清单见 [store.md](./store.md) 第五节 |

> `LoginResultVO` / `CurrentUserVO` 两端**各持一份**（不共享，身份空间不同）——见 [store-bff.md](./store-bff.md) 第四节。
> `ShopPageQueryDTO` / `ShopAuditDTO` / `ShopVO` 在 `contract.store`（域与 admin 共用），`ShopGoodsPageQueryDTO` 是 **admin 本地**的编排查询对象。

## 六、下游依赖（本层调谁）

| 目标 | 通道 | 内容 |
|---|---|---|
| goods-center(8081) | Feign `GoodsCenterClient` | 分类 / 品牌 / 标准 SPU-SKU 模板的 CRUD；分类树与分类全路径 |
| store(8083) | Feign `StoreClient`（跨店通用能力：**不传作用域 = 全量**） | 店铺分页 / 详情 / 审核；店铺商品跨店分页 / 详情 / 锁定 / 解锁；店铺下拉。⚠ 店铺详情走 `getShop`（域侧**查不到返空**，404 文案由本层定）；商品详情走 `storeGoodsDetail`，跨店视角**不传** `storeId` |
| trade-center(8087) | Feign `TradeCenterClient` | 平台侧订单分页 / 详情（**只读**，无写动作） |

全部经 `common` 的 `BffFeignCall` 包装。降级口径见 [cross-cutting.md](./cross-cutting.md) 第 13 条。
`ShopGoodsBffService` 与 `StoreShopBffService` 是本层两个主要编排类。

## 七、业务规则去哪看

RBAC 模型、权限种子 id 约定（目录 X → 页 X1 → 按钮 X11+）、店铺审核状态机、店铺商品锁定语义等见
[`backend/admin/README.md`](../../backend/admin/README.md)（服务说明）与 [`backend/store/README.md`](../../backend/store/README.md)。
