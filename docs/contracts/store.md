<!-- contract-meta
service: store
layer: internal
basePath: /internal/store
feignClient: backend/store-interface/src/main/java/com/panoramic/contract/store/api/StoreClient.java
implScanDirs: backend/store/src/main/java/com/panoramic/store/controller
typeDirs: backend/store-interface/src/main/java
respEnvelope: RespData
-->

# 店铺域（store）内部契约 · 第 ② 层

> 店铺域：店铺 `store_shop` + 审核状态机 + 店主在售商品 `store_goods_spu` / `store_goods_sku`。
> **不暴露公网路由**，只被 store-bff、admin BFF 与 mall-bff 经内部 Feign 调用。
> 域内**不做任何鉴权、不做权限判断**（见 [cross-cutting.md](./cross-cutting.md) 第 6、7、14 条）。

**共 29 个接口**（有作用域维度 13 + 无作用域维度 13 + 交易协作 3）。
⚠ 条数**不按端分侧**统计：同一能力只有一条路径，端别差异只体现在「传不传作用域」上（见第三节）。

## 一、前缀怎么拼上的

本域前缀 = **`/internal/store`**；拼法（不是 context-path、由 Controller 类级 `@RequestMapping`
写死）见 [README.md](./README.md) 的「内部 Feign 的「路径」前缀怎么来的」。

## 二、接口清单

| Feign 方法 | 方法 | 路径 | 入参 | 出参 | 契约声明(接口模块) | 域实现 | 调用方 | 状态 |
|---|---|---|---|---|---|---|---|---|
| getShop | GET | /shops/{id} | Long | `RespData<ShopVO>` | `StoreClient#getShop` | `ShopController#getShop` | StoreShopBffService(store-bff), StoreGoodsBffService(store-bff), StoreShopBffService(admin), CatalogBffService(mall-bff) |  |
| saveShop | POST | /shops/save | ShopSaveDTO | `RespData<Void>` | `StoreClient#saveShop` | `ShopController#save` | StoreShopBffService(store-bff) |  |
| submitShop | POST | /shops/submit | ShopSaveDTO | `RespData<Void>` | `StoreClient#submitShop` | `ShopController#submit` | StoreShopBffService(store-bff) |  |
| pageShops | GET | /shops/page | ShopPageQueryDTO | `RespData<PageResult<ShopVO>>` | `StoreClient#pageShops` | `ShopController#page` | StoreShopBffService(admin) |  |
| auditShop | POST | /shops/{id}/audit | Long, ShopAuditDTO | `RespData<Void>` | `StoreClient#auditShop` | `ShopController#audit` | StoreShopBffService(admin) |  |
| listShopOptions | GET | /shops/options | — | `RespData<List<ShopOptionVO>>` | `StoreClient#listShopOptions` | `ShopController#options` | ShopGoodsBffService(admin) |  |
| pageStoreGoods | GET | /goods/spu/page | StoreGoodsSpuPageQueryDTO | `RespData<PageResult<StoreGoodsSpuPageItemVO>>` | `StoreClient#pageStoreGoods` | `GoodsController#page` | StoreGoodsBffService(store-bff) |  |
| storeGoodsDetail | GET | /goods/spu/{id} | Long, StoreGoodsSpuDetailQueryDTO | `RespData<StoreGoodsSpuPlatformDetailVO>` | `StoreClient#storeGoodsDetail` | `GoodsController#detail` | StoreGoodsBffService(store-bff), ShopGoodsBffService(admin), CatalogBffService(mall-bff) |  |
| saveStoreGoods | POST | /goods/spu | StoreGoodsSpuSaveDTO | `RespData<Long>` | `StoreClient#saveStoreGoods` | `GoodsController#save` | StoreGoodsBffService(store-bff) |  |
| updateStoreGoods | PUT | /goods/spu/{id} | Long, StoreGoodsSpuUpdateDTO | `RespData<Void>` | `StoreClient#updateStoreGoods` | `GoodsController#update` | StoreGoodsBffService(store-bff) |  |
| deleteStoreGoods | DELETE | /goods/spu/{id} | Long, Long | `RespData<Void>` | `StoreClient#deleteStoreGoods` | `GoodsController#delete` | StoreGoodsBffService(store-bff) |  |
| replaceStoreGoodsSkus | PUT | /goods/spu/{id}/skus | Long, StoreGoodsSkuReplaceDTO | `RespData<Void>` | `StoreClient#replaceStoreGoodsSkus` | `GoodsController#replaceSkus` | StoreGoodsBffService(store-bff) |  |
| updateStoreGoodsSkuShelf | PUT | /goods/spu/{spuId}/skus/{skuId}/shelf | Long, Long, StoreGoodsSkuShelfDTO | `RespData<Void>` | `StoreClient#updateStoreGoodsSkuShelf` | `GoodsController#updateSkuShelf` | StoreGoodsBffService(store-bff) |  |
| pageSkuStock | GET | /goods/stock/page | StoreGoodsStockPageQueryDTO | `RespData<PageResult<StoreGoodsStockPageItemVO>>` | `StoreClient#pageSkuStock` | `GoodsController#pageSkuStock` | StoreGoodsBffService(store-bff) |  |
| updateSkuStock | PUT | /goods/stock/{skuId} | Long, StoreGoodsStockUpdateDTO | `RespData<Void>` | `StoreClient#updateSkuStock` | `GoodsController#updateSkuStock` | StoreGoodsBffService(store-bff) |  |
| batchUpdateSkuStock | PUT | /goods/stock/batch | StoreGoodsStockBatchUpdateDTO | `RespData<Void>` | `StoreClient#batchUpdateSkuStock` | `GoodsController#batchUpdateSkuStock` | StoreGoodsBffService(store-bff) |  |
| pageStoreGoodsCrossShop | POST | /goods/cross-shop/spu/page | StoreGoodsSpuCrossShopPageQueryDTO | `RespData<PageResult<StoreGoodsSpuCrossShopPageItemVO>>` | `StoreClient#pageStoreGoodsCrossShop` | `GoodsController#crossShopPage` | ShopGoodsBffService(admin), CatalogBffService(mall-bff) |  |
| crossShopFacets | POST | /goods/facets | StoreGoodsSpuFacetQueryDTO | `RespData<StoreGoodsSpuFacetVO>` | `StoreClient#crossShopFacets` | `GoodsController#facets` | CatalogBffService(mall-bff) |  |
| batchSpuDetail | POST | /goods/spu/batch | StoreGoodsSpuBatchQueryDTO | `RespData<List<StoreGoodsSpuPlatformDetailVO>>` | `StoreClient#batchSpuDetail` | `GoodsController#batchSpuDetail` | CatalogBffService(mall-bff) |  |
| lockStoreGoods | POST | /goods/spu/{id}/lock | Long, StoreGoodsLockDTO | `RespData<Void>` | `StoreClient#lockStoreGoods` | `GoodsController#lock` | ShopGoodsBffService(admin) |  |
| unlockStoreGoods | POST | /goods/spu/{id}/unlock | Long | `RespData<Void>` | `StoreClient#unlockStoreGoods` | `GoodsController#unlock` | ShopGoodsBffService(admin) |  |
| tradeSkuSnapshotBatch | POST | /goods/trade/sku/batch | StoreGoodsSkuBatchQueryDTO | `RespData<List<StoreGoodsSkuSnapshotVO>>` | `StoreClient#tradeSkuSnapshotBatch` | `GoodsTradeController#tradeSkuSnapshotBatch` | GoodsQueryAdapter(trade-center) |  |
| deductStock | POST | /goods/trade/stock/deduct | StoreStockDeductDTO | `RespData<Boolean>` | `StoreClient#deductStock` | `GoodsTradeController#deductStock` | StockAdapter(trade-center) |  |
| revertStockByOrder | POST | /goods/trade/stock/revert-by-order/{orderNo} | String | `RespData<Void>` | `StoreClient#revertStockByOrder` | `GoodsTradeController#revertStockByOrder` | StockAdapter(trade-center) |  |
| submitEvaluation | POST | /goods/evaluation | `StoreGoodsEvaluationSubmitDTO` | `RespData<Void>` | `StoreClient#submitEvaluation` | `EvaluationController#submitEvaluation` | EvaluationBffService(mall-bff) |  |
| pageEvaluations | POST | /goods/evaluation/page | `StoreGoodsEvaluationPageQueryDTO` | `RespData<PageResult<StoreGoodsEvaluationPageItemVO>>` | `StoreClient#pageEvaluations` | `EvaluationController#pageEvaluations` | EvaluationBffService(mall-bff), StoreEvaluationBffService(store-bff) |  |
| evaluationStat | GET | /goods/evaluation/stat | `StoreGoodsEvaluationStatQueryDTO` | `RespData<StoreGoodsEvaluationStatVO>` | `StoreClient#evaluationStat` | `EvaluationController#evaluationStat` | EvaluationBffService(mall-bff) |  |
| listEvaluatedSpuIds | GET | /goods/evaluation/order/{orderNo}/spu-ids | `String`, `StoreGoodsEvaluationOrderQueryDTO` | `RespData<List<Long>>` | `StoreClient#listEvaluatedSpuIds` | `EvaluationController#listEvaluatedSpuIds` | EvaluationBffService(mall-bff) |  |
| replyEvaluation | POST | /goods/evaluation/{id}/reply | `Long`, `StoreGoodsEvaluationReplyDTO` | `RespData<Void>` | `StoreClient#replyEvaluation` | `EvaluationController#replyEvaluation` | StoreEvaluationBffService(store-bff) |  |

> ⚠ **评价组 5 条**（商品评价 `store_goods_evaluation`，2026-09-24 落契约）—— 评价是商品的**二级资源**，
> 路径挂在 `/goods/evaluation/**`，与 `/goods/stock/**` 同层（两者同属「独立表、独立能力，只是以商品为维度」）。
> 形状与作用域口径：
> - **写评价** `submitEvaluation`：入参带 **`customerId`（评价人，必填）**，由 mall-bff 从登录态取；
>   **`store_id` 不由调用方传**——域内按 `spuId` 反查（不采信外部值，故「与 spu 不一致」这种输入状态不存在）。
>   ⚠ **订单门禁（只有已完成订单能评）不在域内**：域不持订单，判状态就要新增 `store → trade` 的域间边
>   （违反 [cross-cutting.md](./cross-cutting.md) 第 24 条「唯一跨域边」）。门禁落在 **mall-bff 的编排**里，
>   见 [mall-bff.md](./mall-bff.md)；域侧只守自己的不变量（**同单同 SPU 至多一条**，由唯一键承担）。
> - **评价分页** `pageEvaluations` / **星级统计** `evaluationStat`：**跨店通用**（同 `pageStoreGoodsCrossShop`）——
>   `spuId` / `storeId` / 星级集合（`scores`）全是**可选筛选**，传了就筛、不传就是不限定，域内不判端。
>   分页用 `POST + @RequestBody`（`scores` 是集合，走 body 规避 `@SpringQueryMap` 的集合序列化口径，与跨店分页同因）；
>   统计无集合字段，用 `GET + @SpringQueryMap`（与 `pageShops` 同形）。
> - **按订单查已评价 SPU** `listEvaluatedSpuIds`：路径变量 `orderNo` 是**资源标识**、不并入 DTO（第 23 条），
>   作用域 `customerId` 可空。出参只回 `spuId` 集合（调用方拿去判「这一行能不能点评价」），不回评价内容。
> - **回复评价** `replyEvaluation`：`storeId` **必填**（写侧没有「合法全量视角」）；「一条评价至多一个回复」
>   由**条件更新（`reply_content is null`）+ 影响行数**承担，不是先读后写。
> ⚠ 评价**不新增任何域间调用边**（store 域不调 trade 取订单）：第 24 条与第 12 条 Nacos 矩阵**均不变**。

> ⚠ **「入参」列不再有位置约定**（cross-cutting 第 23 条）：除路径变量外最多只有一个 DTO，
> 作用域是**该 DTO 的字段**，不是位置裸参——`storeGoodsDetail` 的 `Long, StoreGoodsSpuDetailQueryDTO`
> 是「路径变量 id + 作用域查询 DTO」，`updateStoreGoods` 的 `Long, StoreGoodsSpuUpdateDTO` 同理。
> **唯一例外**是 `deleteStoreGoods`（路径变量外恰好一个 `storeId`，`Long, Long`）——
> 代价是它的删除语句自带「id + store_id」双条件，见第四节。
> 「契约声明」/「域实现」两列指向该端点的**注解行**（如 `@GetMapping("/x")`）。它们只是定位指针，
> 检查器**不核对行号**（只核对路径 / 方法 / 权限串），故文件里增删几行就会整体偏移——
> 2026-09-22 按当时的真实行号统一校准过一次（T19；同日 T3 落交易协作三条时又整体带过一次——那三条的
> 声明加在 `StoreClient` 尾部，但顶部多出的三条 import 把此前所有指针整体推后了 3 行），
> 改动这两个文件时顺手带一下即可。

## 三、作用域与调用方（按「有 / 无作用域维度」分组，**不分端**）

| 组 | 条数 | 方法 | 作用域 |
|---|:--:|---|---|
| **有作用域维度**（作用域必填，无全量视角） | 13 | saveShop, submitShop, pageStoreGoods, saveStoreGoods, updateStoreGoods, deleteStoreGoods, replaceStoreGoodsSkus, updateStoreGoodsSkuShelf, pageSkuStock, updateSkuStock, batchUpdateSkuStock, submitEvaluation, replyEvaluation | 锚点（店主的 `storeId` / 评价人的 `customerId`）进 DTO，**必填**。⚠ 校验组分两档，见下 |
| **无作用域维度**（该能力存在合法全量视角） | 13 | getShop, pageShops, auditShop, listShopOptions, storeGoodsDetail, pageStoreGoodsCrossShop, crossShopFacets, batchSpuDetail, lockStoreGoods, unlockStoreGoods, pageEvaluations, evaluationStat, listEvaluatedSpuIds | 无字段，或**可空**（`storeGoodsDetail` 与评价三条：传了就按它筛，没传就是不限定） |
| **交易协作**（域间调用，非端 BFF） | 3 | tradeSkuSnapshotBatch, deductStock, revertStockByOrder | 无锚点（按资源 id 操作：skuId / orderNo）；调用方是**域**不是端，见 [cross-cutting.md](./cross-cutting.md) 第 24 条（`trade-center` → `store` 是唯一的跨域调用边） |

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

⚠ **必填校验分两档，别一律照 `StoreScopeGroup` 抄**：上一条的机制只为「域 DTO 同时是页面入参类型」而存在。
评价两条写（`submitEvaluation` / `replyEvaluation`）的 DTO **不是任何端的页面入参类型**（两端评价页面的入参
都是各自 BFF 私有的页面 DTO），没有「页面不该提供作用域」这件事需要分组表达，故它们的必填锚点直接挂
**默认组 `@NotNull`**（`@Validated` 也不带 `StoreScopeGroup`）。两档的差别是**入参是不是页面类型**，
不是「哪个字段更重要」——新加能力时先问这一句再决定挂哪档。

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

- 形状：出参**包 `RespData<T>`**（无返回值用 `RespData<Void>`）：业务结果（含业务失败 `code=400/403/404`）一律
  **HTTP 200 + `{code,msg,data}`**，只有兜底异常才是 **HTTP 500**（不鉴权、跨店能力用 `POST + @RequestBody`、
  facets 两维互斥），见 [cross-cutting.md](./cross-cutting.md) 第 2、6、13、18 条。
  ⚠ **本节下文里所有「报 400 / 取不到即 400」都指 `RespData.code`**（HTTP 状态是 200）。
- **查询不到 ≠ 故障，但两种处置按资源而异**：
  - `getShop`（店铺）**返空不抛**——「未开店」「店铺不可见」都是正常态，`code=200` + `data=null` →
    调用方解包得 `null`，由调用方各自重判（见第三节表）；
  - 商品详情 `storeGoodsDetail` **取不到即报 `code=400`「商品不存在」**（传了作用域时不属本店与不存在同样报错、
    不泄露存在性）；`batchSpuDetail` 反向——查不到的 id **跳过不报**（购物车行可能引用已删商品，
    逐行报错会让整个列表取不回来）。
- ⚠ **`deleteStoreGoods` 的双条件不变量**：它是唯一保留裸参的方法（`(Long id, Long storeId)`，
  位置约定不进类型），故删除语句本身写成 `where id = ? and store_id = ?`——
  传参写反时命中 0 行、报「商品不存在」，而不是删掉别人的商品。
- 商品写操作的**作用域必填**靠域入口的 `@Validated({Default.class, StoreScopeGroup.class})` 守：
  缺 `storeId` 的请求得 **`code=400`**，而不是 NPE 或静默写库。

## 五、类型所在包（全部在 `store-interface`，各端引用同一份）

包根：`backend/store-interface/src/main/java/com/panoramic/contract/store/`

| 包 | 类型 |
|---|---|
| `dto` | ShopAuditDTO, ShopPageQueryDTO, ShopSaveDTO, StoreGoodsLockDTO, StoreGoodsSkuDTO, StoreGoodsSkuReplaceDTO, StoreGoodsSkuShelfDTO, StoreGoodsSpuBatchQueryDTO, StoreGoodsSpuCrossShopPageQueryDTO, StoreGoodsSpuDetailQueryDTO, StoreGoodsSpuFacetQueryDTO, StoreGoodsSpuPageQueryDTO, StoreGoodsSpuSaveDTO, StoreGoodsSpuUpdateDTO, StoreGoodsStockPageQueryDTO, StoreGoodsStockUpdateDTO, StoreGoodsStockBatchUpdateDTO, StoreGoodsSkuBatchQueryDTO, StoreStockDeductDTO；评价：StoreGoodsEvaluationSubmitDTO, StoreGoodsEvaluationPageQueryDTO, StoreGoodsEvaluationStatQueryDTO, StoreGoodsEvaluationOrderQueryDTO, StoreGoodsEvaluationReplyDTO |
| `vo` | PageResult, ShopOptionVO, ShopVO, StoreGoodsFacetItemVO, StoreGoodsSkuVO, StoreGoodsSpuCrossShopPageItemVO, StoreGoodsSpuDetailVO, StoreGoodsSpuFacetVO, StoreGoodsSpuPageItemVO, StoreGoodsSpuPlatformDetailVO, StoreGoodsStockPageItemVO, StoreGoodsSkuSnapshotVO；评价：StoreGoodsEvaluationSkuVO, StoreGoodsEvaluationPageItemVO, StoreGoodsEvaluationStatVO, StoreGoodsEvaluationScoreCountVO |

> `dto` 包里另有校验组 `StoreScopeGroup`（**不是数据形状**，只标记「作用域字段只在域入口必填」，故不在上表）。
> ⚠ `dto` 包里另有 `SpecAttr` / `SpecConfigItem`（**跨域共享形状**，不在上表清单里），`contract.goods.dto`
> 下有同形同名的孪生类，逐字段映射时**别引错**（见 [cross-cutting.md](./cross-cutting.md) 第 3 条）。

> 商品详情只有**一个**域出参类型：`StoreGoodsSpuPlatformDetailVO`（= 基础字段的 `StoreGoodsSpuDetailVO`
> + `storeName` / `categoryPath`），店主侧与管理端/C 端共用它，**靠调用方裁剪**而不是靠类型分侧。

> ⚠ **平均评分（`score`）加在既有 VO 上，不新造类型**：商品评分 `BigDecimal score`（`DECIMAL(2,1)`，
> 可空 = 无评价）落在 `StoreGoodsSpuDetailVO`（继承它的 `StoreGoodsSpuPlatformDetailVO` 一并带上）
> 与 `StoreGoodsSpuCrossShopPageItemVO`；店铺评分落 `ShopVO`。三点口径：
> ① **不需要端 BFF 分侧裁剪**——对外展示名就叫「评分」，C 端与商户端口径一致（与 `lock*` / `goodsSpuId`
> 那类「域返回了不等于可以下发」的字段**不同**，别按那一类处理）；
> ② **可空要一路保持可空**：`NULL` = 该商品 / 该店还没有评价，前端**不渲染**（不是显示 `0`）——
> 所以列可空、回写走 `lambdaUpdate().set(...)`（`updateById` 会跳过 null 列，写不回 NULL）；
> ③ ⚠ **同名不同义**：评价行自己的 `score` 是**本次评分**（1–5 整数），这三个 VO 上的 `score` 是**平均评分**。
> 口径与算法见 [backend/store/README.md](../../backend/store/README.md)。

## 六、Feign 客户端配套类

| 类 | 职责 | 位置 |
|---|---|---|
| `StoreFeignConfiguration` | 透传身份头（⚠ **不做** `X-User-Type` 缺省回退，与 `GoodsFeignConfiguration` 行为不对称） | `store-interface/.../contract/store/api/StoreFeignConfiguration.java:32-59` |

## 七、业务规则去哪看

锁定语义（级联下架 / 锁定期整行只读 / 解锁不自动恢复上架）、推导量不变量（`refreshDerived` 是推导量统一刷新入口，
内含 `refreshShelfStatus` 与 `refreshMinPrice` 两个不变量写者）、
store_id 数据作用域（D4/D5/D6）等**业务规则**见 [`backend/store/README.md`](../../backend/store/README.md)。
分类全路径**不在域内解析**（域只存快照），由端 BFF 读时调 goods-center `/categories/paths` 补全 —— 见 [cross-cutting.md](./cross-cutting.md) 与 `store-bff.md`。
