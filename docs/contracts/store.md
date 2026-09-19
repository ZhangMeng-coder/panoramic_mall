<!-- contract-meta
service: store
layer: internal
basePath: /internal/store
feignClient: backend/common/src/main/java/com/panoramic/common/store/api/StoreClient.java
implScanDirs: backend/store/src/main/java/com/panoramic/store/controller
typeDirs: backend/common/src/main/java
-->

# 店铺域（store）内部契约 · 第 ② 层

> 店铺域：店铺 `store_shop` + 审核状态机 + 店主在售商品 `store_goods_spu` / `store_goods_sku`。
> **不暴露公网路由**，只被 store-bff（owner 侧）、admin BFF（platform 侧）与 mall-bff（跨店通用侧）经内部 Feign 调用。
> 域内**不做任何鉴权、不做权限判断**（见 [cross-cutting.md](./cross-cutting.md) 第 6、7、14 条）。

**共 22 个接口**（owner 13 + platform 7 + 跨店通用 2）。

## 一、前缀怎么拼上的（⚠ 容易踩）

`@FeignClient(path = "/internal/store")` **不是** context-path —— store 的 `application.yml` 里没有
context-path（只有 `server.port: 8083`）。前缀是 Controller 类级 `@RequestMapping` 里写死的字面量：

| Controller | 类级 `@RequestMapping` | 行号 |
|---|---|---|
| `ShopController` | `/internal/store/shops` | :31 |
| `GoodsController` | `/internal/store/goods` | :44 |

下表「路径」列是**去掉 `/internal/store` 前缀后**的部分。改前缀要**同时**改 Feign 客户端与两个 Controller。

## 二、接口清单

| Feign 方法 | 方法 | 路径 | 入参 | 出参 | 契约声明(common) | 域实现 | 调用方 | 状态 |
|---|---|---|---|---|---|---|---|---|
| mineShop | GET | /shops/mine | Long | ShopVO | StoreClient.java:58 | ShopController.java:40 | StoreShopBffService(store-bff), StoreGoodsBffService(store-bff) |  |
| saveShop | POST | /shops/{storeId}/save | Long, ShopSaveDTO | void | StoreClient.java:64 | ShopController.java:48 | StoreShopBffService(store-bff) |  |
| submitShop | POST | /shops/{storeId}/submit | Long, ShopSaveDTO | void | StoreClient.java:70 | ShopController.java:56 | StoreShopBffService(store-bff) |  |
| pageShops | GET | /shops/page | ShopPageQueryDTO | PageResult<ShopVO> | StoreClient.java:78 | ShopController.java:64 | StoreShopBffService(admin) |  |
| shopDetail | GET | /shops/{id} | Long | ShopVO | StoreClient.java:84 | ShopController.java:72 | StoreShopBffService(admin) |  |
| auditShop | POST | /shops/{id}/audit | Long, ShopAuditDTO | void | StoreClient.java:90 | ShopController.java:80 | StoreShopBffService(admin) |  |
| pageStoreGoods | GET | /goods/spu/page | Long, StoreGoodsSpuPageQueryDTO | PageResult<StoreGoodsSpuPageItemVO> | StoreClient.java:102 | GoodsController.java:53 | StoreGoodsBffService(store-bff) |  |
| storeGoodsDetail | GET | /goods/spu/{id} | Long, Long | StoreGoodsSpuDetailVO | StoreClient.java:109 | GoodsController.java:62 | StoreGoodsBffService(store-bff) |  |
| saveStoreGoods | POST | /goods/spu | Long, StoreGoodsSpuSaveDTO | Long | StoreClient.java:115 | GoodsController.java:71 | StoreGoodsBffService(store-bff) |  |
| updateStoreGoods | PUT | /goods/spu/{id} | Long, Long, StoreGoodsSpuUpdateDTO | void | StoreClient.java:121 | GoodsController.java:80 | StoreGoodsBffService(store-bff) |  |
| deleteStoreGoods | DELETE | /goods/spu/{id} | Long, Long | void | StoreClient.java:128 | GoodsController.java:90 | StoreGoodsBffService(store-bff) |  |
| replaceStoreGoodsSkus | PUT | /goods/spu/{id}/skus | Long, Long, StoreGoodsSkuReplaceDTO | void | StoreClient.java:134 | GoodsController.java:99 | StoreGoodsBffService(store-bff) |  |
| updateStoreGoodsSkuShelf | PUT | /goods/spu/{spuId}/skus/{skuId}/shelf | Long, Long, Long, StoreGoodsSkuShelfDTO | void | StoreClient.java:141 | GoodsController.java:109 | StoreGoodsBffService(store-bff) |  |
| pageStoreGoodsCrossShop | POST | /goods/cross-shop/spu/page | StoreGoodsSpuCrossShopPageQueryDTO | PageResult<StoreGoodsSpuCrossShopPageItemVO> | StoreClient.java:160 | GoodsController.java:126 | ShopGoodsBffService(admin), CatalogBffService(mall-bff) |  |
| platformStoreGoodsDetail | GET | /goods/platform/spu/{id} | Long | StoreGoodsSpuPlatformDetailVO | StoreClient.java:174 | GoodsController.java:144 | ShopGoodsBffService(admin), CatalogBffService(mall-bff) |  |
| lockStoreGoods | POST | /goods/platform/spu/{id}/lock | Long, StoreGoodsLockDTO | void | StoreClient.java:180 | GoodsController.java:153 | ShopGoodsBffService(admin) |  |
| unlockStoreGoods | POST | /goods/platform/spu/{id}/unlock | Long | void | StoreClient.java:186 | GoodsController.java:161 | ShopGoodsBffService(admin) |  |
| listShopOptions | GET | /shops/options | — | List<ShopOptionVO> | StoreClient.java:193 | ShopController.java:89 | ShopGoodsBffService(admin) |  |
| crossShopFacets | POST | /goods/facets | StoreGoodsSpuFacetQueryDTO | StoreGoodsSpuFacetVO | StoreClient.java:168 | GoodsController.java:136 | CatalogBffService(mall-bff) |  |
| pageSkuStock | GET | /goods/stock/page | Long, StoreGoodsStockPageQueryDTO | PageResult<StoreGoodsStockPageItemVO> | — | — | StoreGoodsBffService(store-bff) | 待实现 |
| updateSkuStock | PUT | /goods/stock/{skuId} | Long, Long, StoreGoodsStockUpdateDTO | void | — | — | StoreGoodsBffService(store-bff) | 待实现 |
| batchUpdateSkuStock | PUT | /goods/stock/batch | Long, StoreGoodsStockBatchUpdateDTO | void | — | — | StoreGoodsBffService(store-bff) | 待实现 |

> 「入参」列里**多个 `Long` 同时出现**时，第一个是 **`storeId`**（owner 侧数据权限锚点），后面的是 `id` / `spuId` / `skuId`。
> 例：`storeGoodsDetail` 的 `Long, Long` = `storeId, id`；`updateStoreGoodsSkuShelf` 的 `Long, Long, Long, DTO` = `storeId, spuId, skuId, dto`。

## 三、owner / platform / 跨店通用 三侧（**分流由"调哪一侧"决定，不由域内判断**）

| 侧 | 条数 | 方法 | 特征 |
|---|:--:|---|---|
| **owner** | 13 | mineShop, saveShop, submitShop, pageStoreGoods, storeGoodsDetail, saveStoreGoods, updateStoreGoods, deleteStoreGoods, replaceStoreGoodsSkus, updateStoreGoodsSkuShelf, pageSkuStock, updateSkuStock, batchUpdateSkuStock | **带 `storeId`**，只作用于「id == store_id 的店」；调用方是 store-bff |
| **platform** | 7 | pageShops, shopDetail, auditShop, platformStoreGoodsDetail, lockStoreGoods, unlockStoreGoods, listShopOptions | **不带 `storeId`**，全量；调用方主要是 admin BFF（权限由 admin 的 `@PreAuthorize` 把关），其中 `platformStoreGoodsDetail` **另被 mall-bff 的 `CatalogBffService` 消费**（C 端商品详情，见下条） |
| **跨店通用**（无锚点） | 2 | pageStoreGoodsCrossShop, crossShopFacets | **不带 `storeId`、也不带任何端别约束**：域只按传入条件过滤，**限定条件全由调用方自设**。`pageStoreGoodsCrossShop` 由 admin BFF 与 mall-bff 共用、`crossShopFacets` 目前只有 mall-bff 消费，差别只在传入条件——C 端固定传 `shopStatus=2` + `shelfStatus=1` + `lockStatus=0`（只出已审核通过店铺的在售未锁定商品），管理端不传这三个约束、走全量 |

> 域内**没有** `assertOwner` / `requirePlatformAdmin` 之类的断言（已随去鉴权一并删除）。
> 谁在什么权限下能调哪一侧，**完全是端 BFF 的职责**。
> ⚠ 跨店通用侧返回的 VO 是**管理端超集**（含 `lockUser` / `lockReason` / `lockTime` 等），
> **C 端输出前必须由端 BFF 裁剪**（见 [cross-cutting.md](./cross-cutting.md) 第 19 条）。
>
> ⚠ **platform 侧 `platformStoreGoodsDetail` 的第二个消费者是 mall-bff**（C 端商品详情，`CatalogBffService#detail`）：
> 它拿到的是**管理端超集**（`lockStatus` / `lockReason` / `lockUser` / `lockTime` / `goodsSpuId` /
> `centerVersion` / 含已下架的全部 SKU），**读 ≠ 判断**——域侧照旧不做任何 C 端裁决，
> 由 mall-bff 自己按与列表**同一不变量**重判可见性（`shelfStatus=1` + `lockStatus=0` + 店铺 `status=2`，
> 任一不满足回 C 端 404「商品不存在或已下架」），并**逐字段手工映射裁剪**成 C 端形状
> （不用 BeanUtils 拷贝：域 VO 日后加字段不会自动漏到前台）。契约见 [mall-bff.md](./mall-bff.md)。
> ⚠ **下游故障 ≠ 不可见**：`BffFeignCall` 把熔断 / 连接失败降级成 `ServiceException(500, …)`，
> mall-bff 只把**业务 4xx**（400/403/404）转成「不存在或已下架」，其余照抛——
> 否则一次下游抖动会被伪装成「这商品下架了」。

## 四、形状规则

- ✅ **不包 `RespData`**；✅ **无 `@PreAuthorize`**；错误走 `{code,msg}` + 真实 HTTP 状态。
- ⚠ **跨店分页与筛选聚合走 `POST + @RequestBody`**（`pageStoreGoodsCrossShop` / `crossShopFacets`），而非 owner 侧的 `GET + @SpringQueryMap` ——
  两者的入参都含集合（`List<Long> categoryIds` / `brandIds`），走 query 会在客户端被序列化成 `xxx[]=1` 形状；
  `POST + body` 规避 Feign `@SpringQueryMap` 对集合字段序列化口径不确定的风险。
- ⚠ **facets 两个维度互斥地排除自身**：`facets` 返回分类与品牌两个维度，分类维度不受**已选分类**影响、
  品牌维度不受**已选品牌**影响（`facetBy` 只在另一维度施加筛选），故选中某项后同维度选项不会消失。
- ⚠ 缺失行**不抛异常**：`mine` / 详情类接口查不到时的行为见模块 README 的边界说明。

## 五、类型所在包（全部在 `common`，两端引用同一份）

包根：`backend/common/src/main/java/com/panoramic/common/store/`

| 包 | 类型 |
|---|---|
| `dto` | ShopAuditDTO, ShopPageQueryDTO, ShopSaveDTO, StoreGoodsLockDTO, StoreGoodsSkuDTO, StoreGoodsSkuReplaceDTO, StoreGoodsSkuShelfDTO, StoreGoodsSpuCrossShopPageQueryDTO, StoreGoodsSpuFacetQueryDTO, StoreGoodsSpuPageQueryDTO, StoreGoodsSpuSaveDTO, StoreGoodsSpuUpdateDTO, StoreGoodsStockPageQueryDTO, StoreGoodsStockUpdateDTO, StoreGoodsStockBatchUpdateDTO |
| `vo` | PageResult, ShopOptionVO, ShopVO, StoreGoodsFacetItemVO, StoreGoodsSkuVO, StoreGoodsSpuCrossShopPageItemVO, StoreGoodsSpuDetailVO, StoreGoodsSpuFacetVO, StoreGoodsSpuPageItemVO, StoreGoodsSpuPlatformDetailVO, StoreGoodsStockPageItemVO |

> 「子类扩字段」先例：`StoreGoodsSpuPlatformDetailVO extends StoreGoodsSpuDetailVO`（platform 侧追加 `storeName` / `categoryPath`），
> 避免为平台侧污染 owner 侧 VO。

## 六、Feign 客户端配套类

| 类 | 职责 | 位置 |
|---|---|---|
| `StoreFeignConfiguration` | 透传身份头（⚠ **不做** `X-User-Type` 缺省回退，与 `GoodsFeignConfiguration` 行为不对称） | `common/.../store/api/StoreFeignConfiguration.java:32-59` |

## 七、业务规则去哪看

锁定语义（级联下架 / 锁定期整行只读 / 解锁不自动恢复上架）、推导量不变量（`refreshDerived` 是推导量统一刷新入口，
内含 `refreshShelfStatus` 与 `refreshMinPrice` 两个不变量写者）、
store_id 数据权限（D4/D5/D6）等**业务规则**见 [`backend/store/README.md`](../../backend/store/README.md)。
分类全路径**不在域内解析**（域只存快照），由端 BFF 读时调 goods-center `/categories/paths` 补全 —— 见 [cross-cutting.md](./cross-cutting.md) 与 `store-bff.md`。
