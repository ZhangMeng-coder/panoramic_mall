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
> **不暴露公网路由**，只被 store-bff、admin BFF 与 mall-bff 经内部 Feign 调用。
> 域内**不做任何鉴权、不做权限判断**（见 [cross-cutting.md](./cross-cutting.md) 第 6、7、14 条）。

**共 24 个接口**（有作用域维度 11 + 无作用域维度 10 + 交易协作 3）。
⚠ 条数**不按端分侧**统计：同一能力只有一条路径，端别差异只体现在「传不传作用域」上（见第三节）。

## 一、前缀怎么拼上的

本域前缀 = **`/internal/store`**；拼法（不是 context-path、由 Controller 类级 `@RequestMapping`
写死）见 [README.md](./README.md) 的「内部 Feign 的「路径」前缀怎么来的」。

## 二、接口清单

| Feign 方法 | 方法 | 路径 | 入参 | 出参 | 契约声明(接口模块) | 域实现 | 调用方 | 状态 |
|---|---|---|---|---|---|---|---|---|
| getShop | GET | /shops/{id} | Long | ShopVO | StoreClient.java:70 | ShopController.java:70 | StoreShopBffService(store-bff), StoreGoodsBffService(store-bff), StoreShopBffService(admin), CatalogBffService(mall-bff) |  |
| saveShop | POST | /shops/save | ShopSaveDTO | void | StoreClient.java:76 | ShopController.java:43 | StoreShopBffService(store-bff) |  |
| submitShop | POST | /shops/submit | ShopSaveDTO | void | StoreClient.java:82 | ShopController.java:51 | StoreShopBffService(store-bff) |  |
| pageShops | GET | /shops/page | ShopPageQueryDTO | PageResult<ShopVO> | StoreClient.java:88 | ShopController.java:59 | StoreShopBffService(admin) |  |
| auditShop | POST | /shops/{id}/audit | Long, ShopAuditDTO | void | StoreClient.java:94 | ShopController.java:78 | StoreShopBffService(admin) |  |
| listShopOptions | GET | /shops/options | — | List<ShopOptionVO> | StoreClient.java:101 | ShopController.java:87 | ShopGoodsBffService(admin) |  |
| pageStoreGoods | GET | /goods/spu/page | StoreGoodsSpuPageQueryDTO | PageResult<StoreGoodsSpuPageItemVO> | StoreClient.java:111 | GoodsController.java:65 | StoreGoodsBffService(store-bff) |  |
| storeGoodsDetail | GET | /goods/spu/{id} | Long, StoreGoodsSpuDetailQueryDTO | StoreGoodsSpuPlatformDetailVO | StoreClient.java:122 | GoodsController.java:77 | StoreGoodsBffService(store-bff), ShopGoodsBffService(admin), CatalogBffService(mall-bff) |  |
| saveStoreGoods | POST | /goods/spu | StoreGoodsSpuSaveDTO | Long | StoreClient.java:129 | GoodsController.java:86 | StoreGoodsBffService(store-bff) |  |
| updateStoreGoods | PUT | /goods/spu/{id} | Long, StoreGoodsSpuUpdateDTO | void | StoreClient.java:135 | GoodsController.java:94 | StoreGoodsBffService(store-bff) |  |
| deleteStoreGoods | DELETE | /goods/spu/{id} | Long, Long | void | StoreClient.java:145 | GoodsController.java:106 | StoreGoodsBffService(store-bff) |  |
| replaceStoreGoodsSkus | PUT | /goods/spu/{id}/skus | Long, StoreGoodsSkuReplaceDTO | void | StoreClient.java:151 | GoodsController.java:115 | StoreGoodsBffService(store-bff) |  |
| updateStoreGoodsSkuShelf | PUT | /goods/spu/{spuId}/skus/{skuId}/shelf | Long, Long, StoreGoodsSkuShelfDTO | void | StoreClient.java:157 | GoodsController.java:124 | StoreGoodsBffService(store-bff) |  |
| pageSkuStock | GET | /goods/stock/page | StoreGoodsStockPageQueryDTO | PageResult<StoreGoodsStockPageItemVO> | StoreClient.java:167 | GoodsController.java:137 | StoreGoodsBffService(store-bff) |  |
| updateSkuStock | PUT | /goods/stock/{skuId} | Long, StoreGoodsStockUpdateDTO | void | StoreClient.java:174 | GoodsController.java:146 | StoreGoodsBffService(store-bff) |  |
| batchUpdateSkuStock | PUT | /goods/stock/batch | StoreGoodsStockBatchUpdateDTO | void | StoreClient.java:181 | GoodsController.java:155 | StoreGoodsBffService(store-bff) |  |
| pageStoreGoodsCrossShop | POST | /goods/cross-shop/spu/page | StoreGoodsSpuCrossShopPageQueryDTO | PageResult<StoreGoodsSpuCrossShopPageItemVO> | StoreClient.java:198 | GoodsController.java:170 | ShopGoodsBffService(admin), CatalogBffService(mall-bff) |  |
| crossShopFacets | POST | /goods/facets | StoreGoodsSpuFacetQueryDTO | StoreGoodsSpuFacetVO | StoreClient.java:206 | GoodsController.java:180 | CatalogBffService(mall-bff) |  |
| batchSpuDetail | POST | /goods/spu/batch | StoreGoodsSpuBatchQueryDTO | List<StoreGoodsSpuPlatformDetailVO> | StoreClient.java:221 | GoodsController.java:196 | CatalogBffService(mall-bff) |  |
| lockStoreGoods | POST | /goods/spu/{id}/lock | Long, StoreGoodsLockDTO | void | StoreClient.java:227 | GoodsController.java:206 | ShopGoodsBffService(admin) |  |
| unlockStoreGoods | POST | /goods/spu/{id}/unlock | Long | void | StoreClient.java:233 | GoodsController.java:214 | ShopGoodsBffService(admin) |  |
| tradeSkuSnapshotBatch | POST | /goods/trade/sku/batch | StoreGoodsSkuBatchQueryDTO | List<StoreGoodsSkuSnapshotVO> | — | — | GoodsQueryAdapter(trade-center) | 待实现 |
| deductStock | POST | /goods/trade/stock/deduct | StoreStockDeductDTO | boolean | — | — | StockAdapter(trade-center) | 待实现 |
| revertStockByOrder | POST | /goods/trade/stock/revert-by-order/{orderNo} | String | void | — | — | StockAdapter(trade-center) | 待实现 |

> ⚠ **「入参」列不再有位置约定**（cross-cutting 第 23 条）：除路径变量外最多只有一个 DTO，
> 作用域是**该 DTO 的字段**，不是位置裸参——`storeGoodsDetail` 的 `Long, StoreGoodsSpuDetailQueryDTO`
> 是「路径变量 id + 作用域查询 DTO」，`updateStoreGoods` 的 `Long, StoreGoodsSpuUpdateDTO` 同理。
> **唯一例外**是 `deleteStoreGoods`（路径变量外恰好一个 `storeId`，`Long, Long`）——
> 代价是它的删除语句自带「id + store_id」双条件，见第四节。
> 「契约声明」/「域实现」两列指向该端点的**注解行**（如 `@GetMapping("/x")`）。它们只是定位指针，
> 检查器**不核对行号**（只核对路径 / 方法 / 权限串），故文件里增删几行就会整体偏移——
> 2026-09-22 按当时的真实行号统一校准过一次（T19），改动这两个文件时顺手带一下即可。

## 三、作用域与调用方（按「有 / 无作用域维度」分组，**不分端**）

| 组 | 条数 | 方法 | 作用域 |
|---|:--:|---|---|
| **有作用域维度**（作用域必填，无全量视角） | 11 | saveShop, submitShop, pageStoreGoods, saveStoreGoods, updateStoreGoods, deleteStoreGoods, replaceStoreGoodsSkus, updateStoreGoodsSkuShelf, pageSkuStock, updateSkuStock, batchUpdateSkuStock | `storeId` 进 DTO，**必填**（`@NotNull(groups = StoreScopeGroup.class)`） |
| **无作用域维度**（该能力存在合法全量视角） | 10 | getShop, pageShops, auditShop, listShopOptions, storeGoodsDetail, pageStoreGoodsCrossShop, crossShopFacets, batchSpuDetail, lockStoreGoods, unlockStoreGoods | 无字段，或**可空**（`storeGoodsDetail`：传了就按它筛，没传就是不限定） |
| **交易协作**（域间调用，非端 BFF） | 3 | tradeSkuSnapshotBatch, deductStock, revertStockByOrder | 无锚点，**待实现** |

**作用域值只能来自调用方的登录态**（端 BFF 取 `LoginUser.getId()`，**禁止**从前端入参透传）：
域侧只做「传了就按 `store_id` 筛，没传就是不限定」，**不判身份、不按端分流**——
域内**没有** `assertOwner` / `requirePlatformAdmin` 之类的断言。
「谁该传什么」全在端 BFF，各端对同一能力的差别只是传不传作用域：

| 能力 | store-bff（店主） | admin BFF（平台） | mall-bff（顾客） |
|---|---|---|---|
| `getShop` | 传自己的账号 id（= 店 id），判 **null = 未开店** | 传任意店铺 id，判 null → 本层「店铺不存在」（400） | 传商品的 storeId，判 null = **店铺不可见** |
| `storeGoodsDetail` | 传自己的账号 id（只看本店） | **不传**（跨店） | **不传**（跨店） |
| 商品写 / 库存 / 分页（11 条必填） | 传自己的账号 id | —（平台走 `pageStoreGoodsCrossShop` 等跨店能力） | — |

⚠ 有作用域维度的 DTO **同时是 store-bff 的页面入参类型**（本层复用域 DTO）：页面按第 22 条**不提供**
作用域字段，由本层从登录态**无条件覆盖**（页面传了也不采用）；`storeId` 的 `@NotNull` 因此只挂在
`StoreScopeGroup` 组上（域入口 `@Validated({Default.class, StoreScopeGroup.class})`，页面入口只跑默认组），
见 [store-bff.md](./store-bff.md) 第四节与 `StoreScopeGroup` 的类注释。

**无作用域维度的能力也不分端**：`pageStoreGoodsCrossShop` / `crossShopFacets` 由 admin BFF（「店铺商品管理」，
不设限定条件 = 全量）与 mall-bff（C 端浏览，固定传 `shopStatus=2` + `shelfStatus=1` + `lockStatus=0`）共用，
**限定条件全由调用方自设**。这些接口返回的 VO 是**管理端超集**（含 `lockUser` / `storeName` / `goodsSpuId`），
**域返回了不等于可以下发**：C 端与商户端输出前必须由各自端 BFF **逐字段裁剪**——
见 [cross-cutting.md](./cross-cutting.md) 第 17、19、20 条。

**交易协作组**是第三种：调用方是**域**（trade-center）而不是端 BFF，路径子段带 `trade` 以示区分；
它不复用「无作用域维度」那组——那组的调用方是端 BFF、语义是**查询**。
⚠ 该组使 store 域首次被**域间**调用，打破了「域只依赖自己的 `<域>-interface`」的结构隔离，
是**唯一登记的跨域依赖例外**（见 [cross-cutting.md](./cross-cutting.md) 第 14 条）。

## 四、形状规则

- 形状（不包 `RespData` / 不鉴权 / `{code,msg}` + 真实 HTTP 状态）与跨店能力用 `POST + @RequestBody` 的口径、
  facets 两维互斥，见 [cross-cutting.md](./cross-cutting.md) 第 2、6、13、18 条。
- **查询不到 ≠ 故障，但两种处置按资源而异**：
  - `getShop`（店铺）**返空不抛**——「未开店」「店铺不可见」都是正常态，HTTP 200 空 body → Feign 解出 `null`，
    由调用方各自重判（见第三节表）；
  - 商品详情 `storeGoodsDetail` **取不到即报 400「商品不存在」**（传了作用域时不属本店与不存在同样报错、
    不泄露存在性）；`batchSpuDetail` 反向——查不到的 id **跳过不报**（购物车行可能引用已删商品，
    逐行报错会让整个列表取不回来）。
- ⚠ **`deleteStoreGoods` 的双条件不变量**：它是唯一保留裸参的方法（`(Long id, Long storeId)`，
  位置约定不进类型），故删除语句本身写成 `where id = ? and store_id = ?`——
  传参写反时命中 0 行、报「商品不存在」，而不是删掉别人的商品。
- 商品写操作的**作用域必填**靠域入口的 `@Validated({Default.class, StoreScopeGroup.class})` 守：
  缺 `storeId` 的请求得 **HTTP 400**，而不是 NPE 或静默写库。

## 五、类型所在包（全部在 `store-interface`，各端引用同一份）

包根：`backend/store-interface/src/main/java/com/panoramic/contract/store/`

| 包 | 类型 |
|---|---|
| `dto` | ShopAuditDTO, ShopPageQueryDTO, ShopSaveDTO, StoreGoodsLockDTO, StoreGoodsSkuDTO, StoreGoodsSkuReplaceDTO, StoreGoodsSkuShelfDTO, StoreGoodsSpuBatchQueryDTO, StoreGoodsSpuCrossShopPageQueryDTO, StoreGoodsSpuDetailQueryDTO, StoreGoodsSpuFacetQueryDTO, StoreGoodsSpuPageQueryDTO, StoreGoodsSpuSaveDTO, StoreGoodsSpuUpdateDTO, StoreGoodsStockPageQueryDTO, StoreGoodsStockUpdateDTO, StoreGoodsStockBatchUpdateDTO, StoreGoodsSkuBatchQueryDTO, StoreStockDeductDTO |
| `vo` | PageResult, ShopOptionVO, ShopVO, StoreGoodsFacetItemVO, StoreGoodsSkuVO, StoreGoodsSpuCrossShopPageItemVO, StoreGoodsSpuDetailVO, StoreGoodsSpuFacetVO, StoreGoodsSpuPageItemVO, StoreGoodsSpuPlatformDetailVO, StoreGoodsStockPageItemVO, StoreGoodsSkuSnapshotVO |

> `dto` 包里另有校验组 `StoreScopeGroup`（**不是数据形状**，只标记「作用域字段只在域入口必填」，故不在上表）。
> ⚠ `dto` 包里另有 `SpecAttr` / `SpecConfigItem`（**跨域共享形状**，不在上表清单里），`contract.goods.dto`
> 下有同形同名的孪生类，逐字段映射时**别引错**（见 [cross-cutting.md](./cross-cutting.md) 第 3 条）。

> 商品详情只有**一个**域出参类型：`StoreGoodsSpuPlatformDetailVO`（= 基础字段的 `StoreGoodsSpuDetailVO`
> + `storeName` / `categoryPath`），店主侧与管理端/C 端共用它，**靠调用方裁剪**而不是靠类型分侧。

## 六、Feign 客户端配套类

| 类 | 职责 | 位置 |
|---|---|---|
| `StoreFeignConfiguration` | 透传身份头（⚠ **不做** `X-User-Type` 缺省回退，与 `GoodsFeignConfiguration` 行为不对称） | `store-interface/.../contract/store/api/StoreFeignConfiguration.java:32-59` |

## 七、业务规则去哪看

锁定语义（级联下架 / 锁定期整行只读 / 解锁不自动恢复上架）、推导量不变量（`refreshDerived` 是推导量统一刷新入口，
内含 `refreshShelfStatus` 与 `refreshMinPrice` 两个不变量写者）、
store_id 数据作用域（D4/D5/D6）等**业务规则**见 [`backend/store/README.md`](../../backend/store/README.md)。
分类全路径**不在域内解析**（域只存快照），由端 BFF 读时调 goods-center `/categories/paths` 补全 —— 见 [cross-cutting.md](./cross-cutting.md) 与 `store-bff.md`。
