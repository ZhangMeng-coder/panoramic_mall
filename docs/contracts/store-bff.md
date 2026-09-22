<!-- contract-meta
service: store-bff
layer: page
baseUrl: /store
scanDirs: backend/store-bff/src/main/java/com/panoramic/storebff/controller
typeDirs: backend/goods-center-interface/src/main/java, backend/store-interface/src/main/java, backend/trade-center-interface/src/main/java, backend/common/src/main/java, backend/store-bff/src/main/java
-->

# 店铺端 BFF（store-bff）对外契约 · 第 ① 层

> 店主端页面接口（8084），经网关 `/store/**` 对外（`StripPrefix=1` 后落到本服务的 `/auth/**`、`/shops/**`、`/goods/**`）。
> 签发 `type=store` 的登录令牌，经内部 Feign 编排 store 域、goods-center 与 trade-center（订单，**待实现**）。

**共 23 个接口**（已实现 20 + **待实现 3**：订单列表 / 详情 / 发货）。

## 一、接口清单

| 方法 | 路径 | 权限串 | 入参 | 出参 | 声明位置 | 状态 |
|---|---|---|---|---|---|---|
| POST | /auth/register | — | RegisterDTO | RespData<LoginResultVO> | AuthController.java:32 |  |
| POST | /auth/login | — | LoginDTO | RespData<LoginResultVO> | AuthController.java:40 |  |
| POST | /auth/logout | — | — | RespData<Void> | AuthController.java:48 |  |
| GET | /auth/me | — | — | RespData<CurrentUserVO> | AuthController.java:60 |  |
| GET | /shops/mine | — | — | RespData<ShopVO> | ShopController.java:31 |  |
| POST | /shops/save | — | ShopSaveDTO | RespData<Void> | ShopController.java:39 |  |
| POST | /shops/submit | — | ShopSaveDTO | RespData<Void> | ShopController.java:48 |  |
| GET | /goods/spu/page | — | StoreGoodsSpuPageQueryDTO | RespData<PageResult<StoreGoodsSpuPageItemVO>> | GoodsController.java:55 |  |
| GET | /goods/spu/{id} | — | Long | RespData<StoreGoodsSpuDetailBffVO> | GoodsController.java:63 |  |
| POST | /goods/spu | — | StoreGoodsSpuSaveDTO | RespData<Long> | GoodsController.java:71 |  |
| PUT | /goods/spu/{id} | — | Long, StoreGoodsSpuUpdateDTO | RespData<Void> | GoodsController.java:79 |  |
| DELETE | /goods/spu/{id} | — | Long | RespData<Void> | GoodsController.java:89 |  |
| PUT | /goods/spu/{id}/skus | — | Long, StoreGoodsSkuReplaceDTO | RespData<Void> | GoodsController.java:98 |  |
| PUT | /goods/spu/{spuId}/skus/{skuId}/shelf | — | Long, Long, StoreGoodsSkuShelfDTO | RespData<Void> | GoodsController.java:108 |  |
| GET | /goods/categories/tree | — | — | RespData<List<CategoryTreeVO>> | GoodsController.java:119 |  |
| GET | /goods/brands | — | — | RespData<List<BrandVO>> | GoodsController.java:127 |  |
| GET | /goods/center/spu-by-sku-code | — | String | RespData<SpuBySkuCodeVO> | GoodsController.java:137 |  |
| GET | /goods/stock/page | — | StoreGoodsStockPageQueryDTO | RespData<PageResult<StoreGoodsStockPageItemVO>> | GoodsController.java:146 |  |
| PUT | /goods/stock/{skuId} | — | Long, StoreGoodsStockUpdateDTO | RespData<Void> | GoodsController.java:154 |  |
| PUT | /goods/stock/batch | — | StoreGoodsStockBatchUpdateDTO | RespData<Void> | GoodsController.java:164 |  |
| GET | /orders/page | — | StoreOrderPageQueryDTO | RespData<PageResult<TradeOrderVO>> | — | 待实现 |
| GET | /orders/{orderNo} | — | String | RespData<TradeOrderVO> | — | 待实现 |
| POST | /orders/{orderNo}/ship | — | String, StoreOrderShipDTO | RespData<Void> | — | 待实现 |

> ⚠ **订单 3 条标 `待实现`**（契约先行）。列表**全状态可见**、含「待支付」（商户需要看到谁下了单没付钱）。
> ⚠ **页面入参 DTO 与域侧入参 DTO 是两回事**（同 [mall-bff.md](./mall-bff.md) 的 `MallOrder*`、[admin.md](./admin.md) 的
> `OrderPageQueryDTO` 做法）：上面两行是**本层自有**的页面 DTO（`StoreOrderPageQueryDTO` / `StoreOrderShipDTO`），
> **不持作用域字段**——`storeId` 由本层从登录态取（`type=store` 的 `loginUser.getId()`）后写进**域侧**入参 DTO
> （`TradeOrderPageQueryDTO.storeId` / `TradeOrderShipDTO.storeId`，后者在域侧是 `@NotNull`）。
> 照页面行实现却以为要传 `storeId`、或照域侧类型当页面入参，都会得到「同一类型两层含义不同」的错觉；
> 口径见 [cross-cutting.md](./cross-cutting.md) 第 22 条，域侧方法见 [trade-center.md](./trade-center.md) 第二节。
> 出参 `TradeOrderVO` 是**域契约类型**（`trade-center-interface`），本层直接下发、不另造一套；
> 状态文案取其中的 `storeAdminLabel`（C 端取的是 `mallLabel`，同一个枚举两个字段）。
> ⚠ 路径标识用 **`orderNo`**，不是自增 id。

> 「路径」列不带网关前缀 `/store`。例：`/goods/spu/page` 对外完整路径是 `/store/goods/spu/page`。

## 二、形状规则

- ✅ **必包 `RespData`**（唯一例外见下节 403 门禁，也是 `RespData` 形状的）。
- ⚠ **全 23 个接口都没有 `@PreAuthorize`** —— 店主端**不接 RBAC**，登录态是唯一门槛，故「权限串」列整列为 `—` 属预期；
  本层**只接受 `type=store` 的登录态**（`panoramic.auth.user-type: store`），跨端 token 在 `AuthTokenFilter` 处即按未认证处理（**HTTP 401**），
  这是店主端的**唯一身份防线**。见 [cross-cutting.md](./cross-cutting.md) 第 9 条。

## 三、本层独有的业务门禁（不在域内）

调域**之前**判定店铺「已审核通过」（含 `/goods/stock/**`），未过审回 **`code=403`**（域内不查店铺状态）；
另有分类全路径读时补全、中台版本比对（「更新提示 + 同步覆盖」）、锁定商品整行只读三条编排口径。
四条的规则本体见 [`backend/store-bff/README.md`](../../backend/store-bff/README.md) 与
[`backend/store/README.md`](../../backend/store/README.md)。

## 四、类型所在

| 来源 | 类型 |
|---|---|
| 两个接口模块（`store-interface` 的 `com.panoramic.contract.store.vo`；`goods-center-interface` 的 `.goods.vo`） | ShopVO, ShopSaveDTO, StoreGoodsSpuPageQueryDTO, StoreGoodsSpuPageItemVO, StoreGoodsSpuSaveDTO, StoreGoodsSpuUpdateDTO, StoreGoodsSkuReplaceDTO, StoreGoodsSkuShelfDTO, StoreGoodsStockPageQueryDTO, StoreGoodsStockUpdateDTO, StoreGoodsStockBatchUpdateDTO, StoreGoodsStockPageItemVO, PageResult, CategoryTreeVO, BrandVO, SpuBySkuCodeVO |
| `trade-center-interface`（`com.panoramic.contract.trade`） | 订单（**待实现**）：出参 TradeOrderVO；**域侧入参**（由本层组装后传给域，不是页面入参）TradeOrderPageQueryDTO, TradeOrderShipDTO |
| **store-bff 私有**（不在任何接口模块，仅本服务用） | `storebff/vo/LoginResultVO`, `storebff/vo/CurrentUserVO`, `storebff/vo/StoreGoodsSpuDetailBffVO`, `storebff/dto/LoginDTO`, `storebff/dto/RegisterDTO`；订单页面入参（**待实现**）：`storebff/dto/StoreOrderPageQueryDTO`, `storebff/dto/StoreOrderShipDTO` |

⚠ `StoreGoodsSpuDetailBffVO` 是 **BFF 独有**的详情出参（在 owner 侧 `StoreGoodsSpuDetailVO` 基础上扩展），
与 platform 侧的 `StoreGoodsSpuPlatformDetailVO` 是同款「子类扩字段」做法，**两者不可互换**。

## 五、下游依赖（本层调谁）

| 目标 | 通道 | 内容 |
|---|---|---|
| store 域(8083) | Feign `StoreClient`（owner 侧方法） | 店铺 mine/save/submit；在售商品 CRUD 与 SKU 上下架 |
| goods-center(8081) | Feign `GoodsCenterClient` | 分类树、分类全路径、品牌列表、SPU 详情、按 SKU 编码反查 SPU |
| trade-center(8087) | Feign `TradeCenterClient`（**待实现**） | 商户侧订单分页 / 详情 / 发货（`storeId` 锚点由本层从登录态取） |

全部经 `common` 的 `BffFeignCall` 包装（剥 cause 链 + 降级文案）。降级口径见 [cross-cutting.md](./cross-cutting.md) 第 13 条。

## 六、业务规则去哪看

店主端页面行为、店铺资料字段、商品编辑约束等见 [`backend/store-bff/README.md`](../../backend/store-bff/README.md)。
