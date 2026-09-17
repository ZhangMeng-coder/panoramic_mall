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
> **不暴露公网路由**，只被 store-bff（owner 侧）与 admin BFF / mall-bff（platform 侧）经内部 Feign 调用。
> 域内**不做任何鉴权、不做权限判断**（见 [cross-cutting.md](./cross-cutting.md) 第 6、7、14 条）。

**共 18 个接口**（owner 10 + platform 8）。

## 一、前缀怎么拼上的（⚠ 容易踩）

`@FeignClient(path = "/internal/store")` **不是** context-path —— store 的 `application.yml` 里没有
context-path（只有 `server.port: 8083`）。前缀是 Controller 类级 `@RequestMapping` 里写死的字面量：

| Controller | 类级 `@RequestMapping` | 行号 |
|---|---|---|
| `ShopController` | `/internal/store/shops` | :31 |
| `GoodsController` | `/internal/store/goods` | :40 |

下表「路径」列是**去掉 `/internal/store` 前缀后**的部分。改前缀要**同时**改 Feign 客户端与两个 Controller。

## 二、接口清单

| Feign 方法 | 方法 | 路径 | 入参 | 出参 | 契约声明(common) | 域实现 | 调用方 | 状态 |
|---|---|---|---|---|---|---|---|---|
| mineShop | GET | /shops/mine | Long | ShopVO | StoreClient.java:57 | ShopController.java:41 | StoreShopBffService(store-bff), StoreGoodsBffService(store-bff) |  |
| saveShop | POST | /shops/{storeId}/save | Long, ShopSaveDTO | void | StoreClient.java:63 | ShopController.java:49 | StoreShopBffService(store-bff) |  |
| submitShop | POST | /shops/{storeId}/submit | Long, ShopSaveDTO | void | StoreClient.java:69 | ShopController.java:57 | StoreShopBffService(store-bff) |  |
| pageShops | GET | /shops/page | ShopPageQueryDTO | PageResult<ShopVO> | StoreClient.java:77 | ShopController.java:65 | StoreShopBffService(admin) |  |
| shopDetail | GET | /shops/{id} | Long | ShopVO | StoreClient.java:83 | ShopController.java:73 | StoreShopBffService(admin) |  |
| auditShop | POST | /shops/{id}/audit | Long, ShopAuditDTO | void | StoreClient.java:89 | ShopController.java:81 | StoreShopBffService(admin) |  |
| pageStoreGoods | GET | /goods/spu/page | Long, StoreGoodsSpuPageQueryDTO | PageResult<StoreGoodsSpuPageItemVO> | StoreClient.java:101 | GoodsController.java:50 | StoreGoodsBffService(store-bff) |  |
| storeGoodsDetail | GET | /goods/spu/{id} | Long, Long | StoreGoodsSpuDetailVO | StoreClient.java:108 | GoodsController.java:59 | StoreGoodsBffService(store-bff) |  |
| saveStoreGoods | POST | /goods/spu | Long, StoreGoodsSpuSaveDTO | Long | StoreClient.java:114 | GoodsController.java:68 | StoreGoodsBffService(store-bff) |  |
| updateStoreGoods | PUT | /goods/spu/{id} | Long, Long, StoreGoodsSpuUpdateDTO | void | StoreClient.java:120 | GoodsController.java:77 | StoreGoodsBffService(store-bff) |  |
| deleteStoreGoods | DELETE | /goods/spu/{id} | Long, Long | void | StoreClient.java:127 | GoodsController.java:87 | StoreGoodsBffService(store-bff) |  |
| replaceStoreGoodsSkus | PUT | /goods/spu/{id}/skus | Long, Long, StoreGoodsSkuReplaceDTO | void | StoreClient.java:133 | GoodsController.java:96 | StoreGoodsBffService(store-bff) |  |
| updateStoreGoodsSkuShelf | PUT | /goods/spu/{spuId}/skus/{skuId}/shelf | Long, Long, Long, StoreGoodsSkuShelfDTO | void | StoreClient.java:140 | GoodsController.java:106 | StoreGoodsBffService(store-bff) |  |
| pageStoreGoodsCrossShop | POST | /goods/cross-shop/spu/page | StoreGoodsSpuCrossShopPageQueryDTO | PageResult<StoreGoodsSpuCrossShopPageItemVO> | StoreClient.java:155 | GoodsController.java:120 | ShopGoodsBffService(admin), CatalogBffService(mall-bff) |  |
| platformStoreGoodsDetail | GET | /goods/platform/spu/{id} | Long | StoreGoodsSpuPlatformDetailVO | StoreClient.java:161 | GoodsController.java:129 | ShopGoodsBffService(admin) |  |
| lockStoreGoods | POST | /goods/platform/spu/{id}/lock | Long, StoreGoodsLockDTO | void | StoreClient.java:167 | GoodsController.java:138 | ShopGoodsBffService(admin) |  |
| unlockStoreGoods | POST | /goods/platform/spu/{id}/unlock | Long | void | StoreClient.java:173 | GoodsController.java:146 | ShopGoodsBffService(admin) |  |
| listShopOptions | GET | /shops/options | — | List<ShopOptionVO> | StoreClient.java:180 | ShopController.java:90 | ShopGoodsBffService(admin) |  |

> 「入参」列里**多个 `Long` 同时出现**时，第一个是 **`storeId`**（owner 侧数据权限锚点），后面的是 `id` / `spuId` / `skuId`。
> 例：`storeGoodsDetail` 的 `Long, Long` = `storeId, id`；`updateStoreGoodsSkuShelf` 的 `Long, Long, Long, DTO` = `storeId, spuId, skuId, dto`。

## 三、owner / platform 两侧（**分流由"调哪一侧"决定，不由域内判断**）

| 侧 | 条数 | 方法 | 特征 |
|---|:--:|---|---|
| **owner** | 10 | mineShop, saveShop, submitShop, pageStoreGoods, storeGoodsDetail, saveStoreGoods, updateStoreGoods, deleteStoreGoods, replaceStoreGoodsSkus, updateStoreGoodsSkuShelf | **带 `storeId`**，只作用于「id == store_id 的店」；调用方是 store-bff |
| **platform** | 8 | pageShops, shopDetail, auditShop, pageStoreGoodsCrossShop, platformStoreGoodsDetail, lockStoreGoods, unlockStoreGoods, listShopOptions | 分页 `pageStoreGoodsCrossShop` 已**跨店通用**（无锚点，调用方自设限定条件）：admin BFF 与 mall-bff 共用，差别只在传入条件（C 端固定 `shopStatus=2` + `shelfStatus=1` + `lockStatus=0`）；其余**不带 `storeId`**，全量，调用方是 admin BFF，权限由 admin 的 `@PreAuthorize` 把关 |

> 域内**没有** `assertOwner` / `requirePlatformAdmin` 之类的断言（已随去鉴权一并删除）。
> 谁在什么权限下能调哪一侧，**完全是端 BFF 的职责**。

## 四、形状规则

- ✅ **不包 `RespData`**；✅ **无 `@PreAuthorize`**；错误走 `{code,msg}` + 真实 HTTP 状态。
- ⚠ **分页走 `POST + @RequestBody`**（`pageStoreGoodsCrossShop`），而非 owner 侧的 `GET + @SpringQueryMap` ——
  因为要传 `List<Long> categoryIds` / `brandIds`，规避 Feign `@SpringQueryMap` 对集合字段序列化口径不确定的风险。
- ⚠ 缺失行**不抛异常**：`mine` / 详情类接口查不到时的行为见模块 README 的边界说明。

## 五、类型所在包（全部在 `common`，两端引用同一份）

包根：`backend/common/src/main/java/com/panoramic/common/store/`

| 包 | 类型 |
|---|---|
| `dto` | ShopAuditDTO, ShopPageQueryDTO, ShopSaveDTO, StoreGoodsLockDTO, StoreGoodsSkuDTO, StoreGoodsSkuReplaceDTO, StoreGoodsSkuShelfDTO, StoreGoodsSpuCrossShopPageQueryDTO, StoreGoodsSpuPageQueryDTO, StoreGoodsSpuSaveDTO, StoreGoodsSpuUpdateDTO |
| `vo` | PageResult, ShopOptionVO, ShopVO, StoreGoodsSkuVO, StoreGoodsSpuCrossShopPageItemVO, StoreGoodsSpuDetailVO, StoreGoodsSpuPageItemVO, StoreGoodsSpuPlatformDetailVO |

> 「子类扩字段」先例：`StoreGoodsSpuPlatformDetailVO extends StoreGoodsSpuDetailVO`（platform 侧追加 `storeName` / `categoryPath`），
> 避免为平台侧污染 owner 侧 VO。

## 六、Feign 客户端配套类

| 类 | 职责 | 位置 |
|---|---|---|
| `StoreFeignConfiguration` | 透传身份头（⚠ **不做** `X-User-Type` 缺省回退，与 `GoodsFeignConfiguration` 行为不对称） | `common/.../store/api/StoreFeignConfiguration.java:32-59` |

## 七、业务规则去哪看

锁定语义（级联下架 / 锁定期整行只读 / 解锁不自动恢复上架）、上下架推导规则（`refreshShelfStatus` 唯一写者）、
store_id 数据权限（D4/D5/D6）等**业务规则**见 [`backend/store/README.md`](../../backend/store/README.md)。
分类全路径**不在域内解析**（域只存快照），由端 BFF 读时调 goods-center `/categories/paths` 补全 —— 见 [cross-cutting.md](./cross-cutting.md) 与 `store-bff.md`。
