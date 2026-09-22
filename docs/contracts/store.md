<!-- contract-meta
service: store
layer: internal
basePath: /internal/store
feignClient: backend/store-interface/src/main/java/com/panoramic/contract/store/api/StoreClient.java
implScanDirs: backend/store/src/main/java/com/panoramic/store/controller
typeDirs: backend/store-interface/src/main/java
-->

# 店铺域（store）内部契约 · 第 ② 层

> 店铺域：店铺 `store_shop` + 审核状态机 + 店主在售商品 `store_goods_spu` / `store_goods_sku`。
> **不暴露公网路由**，只被 store-bff（owner 侧）、admin BFF（platform 侧）与 mall-bff（跨店通用侧）经内部 Feign 调用。
> 域内**不做任何鉴权、不做权限判断**（见 [cross-cutting.md](./cross-cutting.md) 第 6、7、14 条）。

**共 26 个接口**（owner 13 + platform 8 + 跨店通用 2 + 交易协作 3）。

## 一、前缀怎么拼上的

本域前缀 = **`/internal/store`**；拼法（不是 context-path、由 Controller 类级 `@RequestMapping`
写死）见 [README.md](./README.md) 的「内部 Feign 的「路径」前缀怎么来的」。

## 二、接口清单

| Feign 方法 | 方法 | 路径 | 入参 | 出参 | 契约声明(接口模块) | 域实现 | 调用方 | 状态 |
|---|---|---|---|---|---|---|---|---|
| mineShop | GET | /shops/mine | Long | ShopVO | StoreClient.java:64 | ShopController.java:40 | StoreShopBffService(store-bff), StoreGoodsBffService(store-bff) |  |
| saveShop | POST | /shops/{storeId}/save | Long, ShopSaveDTO | void | StoreClient.java:70 | ShopController.java:48 | StoreShopBffService(store-bff) |  |
| submitShop | POST | /shops/{storeId}/submit | Long, ShopSaveDTO | void | StoreClient.java:76 | ShopController.java:56 | StoreShopBffService(store-bff) |  |
| pageShops | GET | /shops/page | ShopPageQueryDTO | PageResult<ShopVO> | StoreClient.java:84 | ShopController.java:64 | StoreShopBffService(admin) |  |
| shopDetail | GET | /shops/{id} | Long | ShopVO | StoreClient.java:90 | ShopController.java:72 | StoreShopBffService(admin) |  |
| auditShop | POST | /shops/{id}/audit | Long, ShopAuditDTO | void | StoreClient.java:96 | ShopController.java:80 | StoreShopBffService(admin) |  |
| pageStoreGoods | GET | /goods/spu/page | Long, StoreGoodsSpuPageQueryDTO | PageResult<StoreGoodsSpuPageItemVO> | StoreClient.java:108 | GoodsController.java:60 | StoreGoodsBffService(store-bff) |  |
| storeGoodsDetail | GET | /goods/spu/{id} | Long, Long | StoreGoodsSpuDetailVO | StoreClient.java:115 | GoodsController.java:69 | StoreGoodsBffService(store-bff) |  |
| saveStoreGoods | POST | /goods/spu | Long, StoreGoodsSpuSaveDTO | Long | StoreClient.java:121 | GoodsController.java:78 | StoreGoodsBffService(store-bff) |  |
| updateStoreGoods | PUT | /goods/spu/{id} | Long, Long, StoreGoodsSpuUpdateDTO | void | StoreClient.java:127 | GoodsController.java:87 | StoreGoodsBffService(store-bff) |  |
| deleteStoreGoods | DELETE | /goods/spu/{id} | Long, Long | void | StoreClient.java:134 | GoodsController.java:97 | StoreGoodsBffService(store-bff) |  |
| replaceStoreGoodsSkus | PUT | /goods/spu/{id}/skus | Long, Long, StoreGoodsSkuReplaceDTO | void | StoreClient.java:140 | GoodsController.java:106 | StoreGoodsBffService(store-bff) |  |
| updateStoreGoodsSkuShelf | PUT | /goods/spu/{spuId}/skus/{skuId}/shelf | Long, Long, Long, StoreGoodsSkuShelfDTO | void | StoreClient.java:147 | GoodsController.java:116 | StoreGoodsBffService(store-bff) |  |
| pageSkuStock | GET | /goods/stock/page | Long, StoreGoodsStockPageQueryDTO | PageResult<StoreGoodsStockPageItemVO> | StoreClient.java:157 | GoodsController.java:130 | StoreGoodsBffService(store-bff) |  |
| updateSkuStock | PUT | /goods/stock/{skuId} | Long, Long, StoreGoodsStockUpdateDTO | void | StoreClient.java:165 | GoodsController.java:139 | StoreGoodsBffService(store-bff) |  |
| batchUpdateSkuStock | PUT | /goods/stock/batch | Long, StoreGoodsStockBatchUpdateDTO | void | StoreClient.java:173 | GoodsController.java:148 | StoreGoodsBffService(store-bff) |  |
| pageStoreGoodsCrossShop | POST | /goods/cross-shop/spu/page | StoreGoodsSpuCrossShopPageQueryDTO | PageResult<StoreGoodsSpuCrossShopPageItemVO> | StoreClient.java:191 | GoodsController.java:163 | ShopGoodsBffService(admin), CatalogBffService(mall-bff) |  |
| platformStoreGoodsDetail | GET | /goods/platform/spu/{id} | Long | StoreGoodsSpuPlatformDetailVO | StoreClient.java:205 | GoodsController.java:181 | ShopGoodsBffService(admin), CatalogBffService(mall-bff) |  |
| platformSpuBatch | POST | /goods/platform/spu/batch | StoreGoodsSpuBatchQueryDTO | List<StoreGoodsSpuPlatformDetailVO> | StoreClient.java:220 | GoodsController.java:197 | CatalogBffService(mall-bff) |  |
| lockStoreGoods | POST | /goods/platform/spu/{id}/lock | Long, StoreGoodsLockDTO | void | StoreClient.java:226 | GoodsController.java:207 | ShopGoodsBffService(admin) |  |
| unlockStoreGoods | POST | /goods/platform/spu/{id}/unlock | Long | void | StoreClient.java:232 | GoodsController.java:215 | ShopGoodsBffService(admin) |  |
| listShopOptions | GET | /shops/options | — | List<ShopOptionVO> | StoreClient.java:239 | ShopController.java:89 | ShopGoodsBffService(admin) |  |
| crossShopFacets | POST | /goods/facets | StoreGoodsSpuFacetQueryDTO | StoreGoodsSpuFacetVO | StoreClient.java:199 | GoodsController.java:173 | CatalogBffService(mall-bff) |  |
| tradeSkuSnapshotBatch | POST | /goods/trade/sku/batch | StoreGoodsSkuBatchQueryDTO | List<StoreGoodsSkuSnapshotVO> | — | — | GoodsQueryAdapter(trade-center) | 待实现 |
| deductStock | POST | /goods/trade/stock/deduct | StoreStockDeductDTO | boolean | — | — | StockAdapter(trade-center) | 待实现 |
| revertStockByOrder | POST | /goods/trade/stock/revert-by-order/{orderNo} | String | void | — | — | StockAdapter(trade-center) | 待实现 |

> 「入参」列里**多个 `Long` 同时出现**时，第一个是 **`storeId`**（owner 侧数据权限锚点），后面的是 `id` / `spuId` / `skuId`。
> 例：`storeGoodsDetail` 的 `Long, Long` = `storeId, id`；`updateStoreGoodsSkuShelf` 的 `Long, Long, Long, DTO` = `storeId, spuId, skuId, dto`。
> 「契约声明」/「域实现」两列指向该端点的**注解行**（如 `@GetMapping("/x")`）。它们只是定位指针，
> 检查器**不核对行号**（只核对路径 / 方法 / 权限串），故文件里增删几行就会整体偏移——
> 2026-09-21 按当时的真实行号统一校准过一次，改动这两个文件时顺手带一下即可。

## 三、owner / platform / 跨店通用 / 交易协作 四侧（**分流由「调哪一侧」决定，不由域内判断**）

| 侧 | 条数 | 方法 |
|---|:--:|---|
| **owner** | 13 | mineShop, saveShop, submitShop, pageStoreGoods, storeGoodsDetail, saveStoreGoods, updateStoreGoods, deleteStoreGoods, replaceStoreGoodsSkus, updateStoreGoodsSkuShelf, pageSkuStock, updateSkuStock, batchUpdateSkuStock |
| **platform** | 8 | pageShops, shopDetail, auditShop, platformStoreGoodsDetail, platformSpuBatch, lockStoreGoods, unlockStoreGoods, listShopOptions |
| **跨店通用**（无锚点） | 2 | pageStoreGoodsCrossShop, crossShopFacets |
| **交易协作**（无锚点，调用方 = trade-center） | 3 | tradeSkuSnapshotBatch, deductStock, revertStockByOrder |

owner 侧**带 `storeId`**（只作用于「id == store_id 的店」，调用方 store-bff）；platform 侧**不带**（全量，
调用方 admin BFF，其中 `platformStoreGoodsDetail` / `platformSpuBatch` 另被 mall-bff 消费——
后者是购物车列表的**批量详情**，一次调用取回多个 SPU，避免逐行 N+1）；跨店通用侧**连端别约束也不带**——
域只按传入条件过滤，**限定条件全由调用方自设**。域内**没有** `assertOwner` / `requirePlatformAdmin`
之类的断言，**谁在什么权限下能调哪一侧完全是端 BFF 的职责**。跨店侧返回的 VO 是**管理端超集**，
C 端输出前必须由端 BFF 裁剪 —— 见 [cross-cutting.md](./cross-cutting.md) 第 17、19、20 条。

**交易协作侧**是第四侧：调用方是**域**（trade-center）而不是端 BFF，路径子段天然带 `trade` 以示区分。
它不复用「跨店通用」——那条的语义是**查询**、调用方是端 BFF；也不塞进 `platform`（那条写死在管理端）。
⚠ 该侧的存在使 store 域首次被**域间**调用，打破了「域只依赖自己的 `<域>-interface`」的结构隔离，
是**唯一登记的跨域依赖例外**（见 [cross-cutting.md](./cross-cutting.md) 第 14 条）。

## 四、形状规则

- 形状（不包 `RespData` / 不鉴权 / `{code,msg}` + 真实 HTTP 状态）与跨店通用侧的 `POST + @RequestBody` 口径、
  facets 两维互斥，见 [cross-cutting.md](./cross-cutting.md) 第 2、6、13、18 条。
- ⚠ 缺失行**不抛异常**：`mine` / 详情类接口查不到时的行为见 [`backend/store/README.md`](../../backend/store/README.md) 的边界说明。

## 五、类型所在包（全部在 `store-interface`，两端引用同一份）

包根：`backend/store-interface/src/main/java/com/panoramic/contract/store/`

| 包 | 类型 |
|---|---|
| `dto` | ShopAuditDTO, ShopPageQueryDTO, ShopSaveDTO, StoreGoodsLockDTO, StoreGoodsSkuDTO, StoreGoodsSkuReplaceDTO, StoreGoodsSkuShelfDTO, StoreGoodsSpuBatchQueryDTO, StoreGoodsSpuCrossShopPageQueryDTO, StoreGoodsSpuFacetQueryDTO, StoreGoodsSpuPageQueryDTO, StoreGoodsSpuSaveDTO, StoreGoodsSpuUpdateDTO, StoreGoodsStockPageQueryDTO, StoreGoodsStockUpdateDTO, StoreGoodsStockBatchUpdateDTO, StoreGoodsSkuBatchQueryDTO, StoreStockDeductDTO |
| `vo` | PageResult, ShopOptionVO, ShopVO, StoreGoodsFacetItemVO, StoreGoodsSkuVO, StoreGoodsSpuCrossShopPageItemVO, StoreGoodsSpuDetailVO, StoreGoodsSpuFacetVO, StoreGoodsSpuPageItemVO, StoreGoodsSpuPlatformDetailVO, StoreGoodsStockPageItemVO, StoreGoodsSkuSnapshotVO |

> ⚠ `dto` 包里另有 `SpecAttr` / `SpecConfigItem`（**跨域共享形状**，不在上表清单里），`contract.goods.dto`
> 下有同形同名的孪生类，逐字段映射时**别引错**（见 [cross-cutting.md](./cross-cutting.md) 第 3 条）。

> 「子类扩字段」先例：`StoreGoodsSpuPlatformDetailVO extends StoreGoodsSpuDetailVO`（platform 侧追加 `storeName` / `categoryPath`），
> 避免为平台侧污染 owner 侧 VO。

## 六、Feign 客户端配套类

| 类 | 职责 | 位置 |
|---|---|---|
| `StoreFeignConfiguration` | 透传身份头（⚠ **不做** `X-User-Type` 缺省回退，与 `GoodsFeignConfiguration` 行为不对称） | `store-interface/.../contract/store/api/StoreFeignConfiguration.java:32-59` |

## 七、业务规则去哪看

锁定语义（级联下架 / 锁定期整行只读 / 解锁不自动恢复上架）、推导量不变量（`refreshDerived` 是推导量统一刷新入口，
内含 `refreshShelfStatus` 与 `refreshMinPrice` 两个不变量写者）、
store_id 数据权限（D4/D5/D6）等**业务规则**见 [`backend/store/README.md`](../../backend/store/README.md)。
分类全路径**不在域内解析**（域只存快照），由端 BFF 读时调 goods-center `/categories/paths` 补全 —— 见 [cross-cutting.md](./cross-cutting.md) 与 `store-bff.md`。
