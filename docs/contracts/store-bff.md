<!-- contract-meta
service: store-bff
layer: page
baseUrl: /store
scanDirs: backend/store-bff/src/main/java/com/panoramic/storebff/controller
typeDirs: backend/goods-center-interface/src/main/java, backend/store-interface/src/main/java, backend/trade-center-interface/src/main/java, backend/common/src/main/java, backend/store-bff/src/main/java
-->

# 店铺端 BFF（store-bff）对外契约 · 第 ① 层

> 店主端页面接口（8084），经网关 `/store/**` 对外（`StripPrefix=1` 后落到本服务的 `/auth/**`、`/shops/**`、
> `/goods/**`、`/orders/**`、`/evaluations/**`、`/stats/**`）。
> 签发 `type=store` 的登录令牌，经内部 Feign 编排 store 域、goods-center、trade-center（订单）
> 与 customer-center（评价人的昵称 / 头像）。

**共 26 个接口**。

## 一、接口清单

| 方法 | 路径 | 权限串 | 入参 | 出参 | 声明位置 | 状态 |
|---|---|---|---|---|---|---|
| POST | /auth/register | — | RegisterDTO | RespData<LoginResultVO> | `AuthController#register` |  |
| POST | /auth/login | — | LoginDTO | RespData<LoginResultVO> | `AuthController#login` |  |
| POST | /auth/logout | — | — | RespData<Void> | `AuthController#logout` |  |
| GET | /auth/me | — | — | RespData<CurrentUserVO> | `AuthController#me` |  |
| GET | /stats/overview | — | StoreStatsQueryDTO | RespData<StoreStatsVO> | `StatsController#overview` |  |
| GET | /shops/mine | — | — | RespData<ShopVO> | `ShopController#mine` |  |
| POST | /shops/save | — | ShopSaveDTO | RespData<Void> | `ShopController#save` |  |
| POST | /shops/submit | — | ShopSaveDTO | RespData<Void> | `ShopController#submit` |  |
| GET | /goods/spu/page | — | StoreGoodsSpuPageQueryDTO | RespData<PageResult<StoreGoodsSpuPageItemVO>> | `GoodsController#page` |  |
| GET | /goods/spu/{id} | — | Long | RespData<StoreGoodsSpuDetailBffVO> | `GoodsController#detail` |  |
| POST | /goods/spu | — | StoreGoodsSpuSaveDTO | RespData<Long> | `GoodsController#save` |  |
| PUT | /goods/spu/{id} | — | Long, StoreGoodsSpuUpdateDTO | RespData<Void> | `GoodsController#update` |  |
| DELETE | /goods/spu/{id} | — | Long | RespData<Void> | `GoodsController#delete` |  |
| PUT | /goods/spu/{id}/skus | — | Long, StoreGoodsSkuReplaceDTO | RespData<Void> | `GoodsController#replaceSkus` |  |
| PUT | /goods/spu/{spuId}/skus/{skuId}/shelf | — | Long, Long, StoreGoodsSkuShelfDTO | RespData<Void> | `GoodsController#updateSkuShelf` |  |
| GET | /goods/categories/tree | — | — | RespData<List<CategoryTreeVO>> | `GoodsController#categoryTree` |  |
| GET | /goods/brands | — | — | RespData<List<BrandVO>> | `GoodsController#listBrands` |  |
| GET | /goods/center/spu-by-sku-code | — | String | RespData<SpuBySkuCodeVO> | `GoodsController#centerSpuBySkuCode` |  |
| GET | /goods/stock/page | — | StoreGoodsStockPageQueryDTO | RespData<PageResult<StoreGoodsStockPageItemVO>> | `GoodsController#pageStock` |  |
| PUT | /goods/stock/{skuId} | — | Long, StoreGoodsStockUpdateDTO | RespData<Void> | `GoodsController#updateSkuStock` |  |
| PUT | /goods/stock/batch | — | StoreGoodsStockBatchUpdateDTO | RespData<Void> | `GoodsController#batchUpdateSkuStock` |  |
| GET | /orders/page | — | StoreOrderPageQueryDTO | RespData<PageResult<TradeOrderVO>> | `OrderController#page` |  |
| GET | /orders/{orderNo} | — | String | RespData<TradeOrderVO> | `OrderController#detail` |  |
| POST | /orders/{orderNo}/ship | — | String, StoreOrderShipDTO | RespData<Void> | `OrderController#ship` |  |
| GET | /evaluations/page | — | StoreEvaluationPageQueryDTO | RespData<PageResult<StoreEvaluationItemVO>> | `EvaluationController#page` |  |
| POST | /evaluations/{id}/reply | — | Long, StoreEvaluationReplyDTO | RespData<Void> | `EvaluationController#reply` |  |

> ⚠ **首页数据看板 `GET /stats/overview`**（2026-09-27 落契约）—— **一个接口承载八项**，与
> [admin.md](./admin.md) 的平台看板同形（那里也是「一个接口 + 前 3 快照 / 后 5 窗口」）：
> - **入参只有时间窗口**（`StoreStatsQueryDTO`：`window` 枚举 + 自定义时的 `start` / `end`），
>   **没有 `storeId`** —— 作用域由本层从登录态取（`type=store` 的 `loginUser.getId()`）后写进**域侧**入参 DTO，
>   页面**不提供也不采用**它传的值（[cross-cutting.md](./cross-cutting.md) 第 22 条）。
> - **窗口解析（本月 / 上季 / 去年 / 自定义 + ≤180 天粒度）在 `common` 里，本端只调**：同一份实现
>   admin 看板也在用（[cross-cutting.md](./cross-cutting.md) 第 25 条）。⚠ **别在本层再写一份月 / 季 / 年算术**。
> - **前 3 项是当前累计快照、不受窗口影响**（上架 / 下架商品数、库存异常 SKU 数），其余 5 项按窗口。
> - ⚠ **本接口不套审核门禁**（与同层的 `/goods/**` 相反）：店主端首页 `/home` **不在**「开店后业务入口」
>   那组路由里，未开店 / 未过审同样打得开首页，故接口必须**容忍空店**——各计数回 `0`、折线回**一串 0**
>   （零填充的完整桶序列，**不是**空数组，见第 25 条），**不是** 404、**不是** 403、**不是**降级 500。
> - **无 `@PreAuthorize`**（同本层其余 25 条，店主端不接 RBAC）。
> - 各指标口径（谁的窗口 / 粒度 / 库存异常怎么判 / 评价分布按不按窗口）属**业务规则**，见
>   [`backend/store-bff/README.md`](../../backend/store-bff/README.md) 与各域 README；本文件只登记接口与形状。

> ⚠ **评价 2 条**（2026-09-24 落契约）——**独立菜单页「评价」**（与「订单管理」平级），不是订单详情里的子区块。
> - **本店评价分页** `GET /evaluations/page`：**可按星级筛选**（页面按「全部 / 5 星 / …」单选，
>   域侧入参是**星级集合** `scores`，非空即 `IN` 过滤、空 = 不筛）+ **可按商品筛选**（`spuId`；
>   商品列表**复用既有在售商品分页**，不为它新造一套）。⚠ 与 C 端的刻意不对称：**C 端要分布不要筛选、
>   商户端要筛选不要分布**——**分布是展示、筛选是操作**，商户端按星级看差评才是刚需。
>   ⚠ 每行显示**商品名**：由 store 域按 `spuId` **批量**反查填充；商品已软删 → 域侧下发 `null`、
>   **本层填兜底文案「商品已删除」**（不报错、不整页失败）。
> - **回复评价** `POST /evaluations/{id}/reply`：入参只有 `replyContent`；`storeId` 由本层从登录态取
>   （`type=store` 的 `loginUser.getId()`）后写进**域侧**入参 DTO。**一条评价至多一个回复**，
>   由域侧条件更新 + 影响行数承担——本层**不重判**，域侧 `400`「该评价已回复」原样透传。
>   ⚠ 回复**不可改、不可删**（需求未提，属边界）；回复**不影响评分**、不写状态轨迹。
> - ⚠ 评价人昵称 / 头像经 **customer-center** 批量取（见第五节）；拿不到资料统一下发占位「用户」、
>   头像为空下发 `null`。**读取侧不拼手机号后 4 位**（那需要手机号，商户端拿不到）——
>   该规则只在 mall-bff 的写入侧实现一处。
> - ⚠ 评价文字与回复文字按**纯文本插值**渲染，本层**不接 `HtmlSanitizer`**、前端**不许 `v-html`**。

> ⚠ **订单列表全状态可见**，含「待支付」（商户需要看到谁下了单没付钱）。
> ⚠ **页面入参 DTO 与域侧入参 DTO 是两回事**（同 [mall-bff.md](./mall-bff.md) 的 `MallOrder*`、[admin.md](./admin.md) 的
> `OrderPageQueryDTO` 做法）：上面两行是**本层自有**的页面 DTO（`StoreOrderPageQueryDTO` / `StoreOrderShipDTO`），
> **不持作用域字段**——`storeId` 由本层从登录态取（`type=store` 的 `loginUser.getId()`）后写进**域侧**入参 DTO
> （`TradeOrderPageQueryDTO.storeId` / `TradeOrderShipDTO.storeId`，后者在域侧是 `@NotNull`）。
> 照页面行实现却以为要传 `storeId`、或照域侧类型当页面入参，都会得到「同一类型两层含义不同」的错觉；
> 口径见 [cross-cutting.md](./cross-cutting.md) 第 22 条，域侧方法见 [trade-center.md](./trade-center.md) 第二节。
> 出参 `TradeOrderVO` 是**域契约类型**（`trade-center-interface`），本层直接下发、不另造一套；
> 状态文案取其中的 `statusStoreAdminLabel`（C 端取的是 `statusMallLabel`，同一个枚举两个字段）。
> ⚠ 别写成 `storeAdminLabel` / `mallLabel`——那是枚举 `OrderStatus` **内部**的字段名，域 VO 上的同义字段带 `status` 前缀；
> 照枚举名取会拿到 `undefined`、状态列直接空白（且**不会报错**，是静默的）。
> ⚠ 路径标识用 **`orderNo`**，不是自增 id。

> ⚠ **与上一条相对的另一半：本层**多数**页面入参**直接复用域 DTO**（`ShopSaveDTO` / `StoreGoodsSpuSaveDTO` /
> `StoreGoodsSpuUpdateDTO` / `StoreGoodsSkuReplaceDTO` / `StoreGoodsSkuShelfDTO` / `StoreGoodsStockUpdateDTO` /
> `StoreGoodsStockBatchUpdateDTO` / `StoreGoodsSpuPageQueryDTO` / `StoreGoodsStockPageQueryDTO`）——
> 这些 DTO 带作用域字段 `storeId`，但**页面不提供、也不采用页面传的值**：
> `StoreShopBffService` / `StoreGoodsBffService` 从登录态取（`type=store` 的 `loginUser.getId()`）后
> **无条件覆盖**（不是 `if (dto.getStoreId() != null)` 才填），再原样透传给域。
> 域侧该字段是 `@NotNull(groups = StoreScopeGroup.class)`（缺了即 HTTP 400），
> 页面入口只跑默认组校验、故页面请求不因缺 `storeId` 被拒 —— 见 [cross-cutting.md](./cross-cutting.md) 第 22 条。

> 「路径」列不带网关前缀 `/store`。例：`/goods/spu/page` 对外完整路径是 `/store/goods/spu/page`。

## 二、形状规则

- ✅ **必包 `RespData`**（唯一例外见下节 403 门禁，也是 `RespData` 形状的）。
- ⚠ **全 26 个接口都没有 `@PreAuthorize`** —— 店主端**不接 RBAC**，登录态是唯一门槛，故「权限串」列整列为 `—` 属预期；
  本层**只接受 `type=store` 的登录态**（`panoramic.auth.user-type: store`），跨端 token 在 `AuthTokenFilter` 处即按未认证处理（**HTTP 401**），
  这是店主端的**唯一身份防线**。见 [cross-cutting.md](./cross-cutting.md) 第 9 条。

## 三、本层独有的业务门禁（不在域内）

调域**之前**判定店铺「已审核通过」（含 `/goods/stock/**`），未过审回 **`code=403`**（域内不查店铺状态）；
另有分类全路径读时补全、中台版本比对（「更新提示 + 同步覆盖」）、锁定商品整行只读三条编排口径。
⚠ **这条门禁只落在 `/goods/**` 编排**（`StoreGoodsBffService` 内的 `requireApprovedShop`）：`/shops/**`、`/orders/**`、
`/evaluations/**` 与新的 `/stats/overview` 都**不套**——它们的页面里 `/home`、`/shop-info` 本就不在「开店后业务入口」
那组路由中，未开店 / 未过审必须打得开。**按页面可达性判**，不是按接口归类判。
四条的规则本体见 [`backend/store-bff/README.md`](../../backend/store-bff/README.md) 与
[`backend/store/README.md`](../../backend/store/README.md)。

## 四、类型所在

| 来源 | 类型 |
|---|---|
| 两个接口模块（`store-interface` 的 `com.panoramic.contract.store.*`；`goods-center-interface` 的 `.goods.vo`） | ShopVO, ShopSaveDTO, StoreGoodsSpuPageQueryDTO, StoreGoodsSpuPageItemVO, StoreGoodsSpuSaveDTO, StoreGoodsSpuUpdateDTO, StoreGoodsSkuReplaceDTO, StoreGoodsSkuShelfDTO, StoreGoodsStockPageQueryDTO, StoreGoodsStockUpdateDTO, StoreGoodsStockBatchUpdateDTO, StoreGoodsStockPageItemVO, StoreGoodsSpuDetailQueryDTO, StoreGoodsSpuPlatformDetailVO, PageResult, CategoryTreeVO, BrandVO, SpuBySkuCodeVO |
| `trade-center-interface`（`com.panoramic.contract.trade`） | 订单：出参 TradeOrderVO, TradeOrderStatsVO；**域侧入参**（由本层组装后传给域，不是页面入参）TradeOrderPageQueryDTO, TradeOrderQueryDTO, TradeOrderShipDTO, TradeOrderStatsQueryDTO |
| **store-bff 私有**（不在任何接口模块，仅本服务用） | `storebff/vo/LoginResultVO`, `storebff/vo/CurrentUserVO`, `storebff/vo/StoreGoodsSpuDetailBffVO`, `storebff/dto/LoginDTO`, `storebff/dto/RegisterDTO`；订单页面入参：`storebff/dto/StoreOrderPageQueryDTO`, `storebff/dto/StoreOrderShipDTO`；评价：`storebff/dto/StoreEvaluationPageQueryDTO`, `storebff/dto/StoreEvaluationReplyDTO`, `storebff/vo/StoreEvaluationItemVO`；首页看板：`storebff/dto/StoreStatsQueryDTO`, `storebff/vo/StoreStatsVO` |
| `common`（`com.panoramic.common.stats`，**两个端 BFF 共用**、域不得引用） | StatsWindow（页面窗口枚举）, StatsGrain（折线粒度）, StatsPointVO（折线点 `date` / `count`）, StatsDateRange, StatsWindows（窗口 → 显式起止 + 自定义校验 + 粒度判定）, StatsSeriesMerger（日点 → 桶点，含零填充） |
| `customer-center-interface`（`com.panoramic.contract.customer`） | `CustomerProfileBatchQueryDTO`（评价人头像 / 昵称批量取的**域侧入参**，由本层组装后传给域，不是页面入参） |

⚠ 评价出参 `StoreEvaluationItemVO` 是**本层私有**（不继承域类型）：域出参带 `customerId`（内部 id，不下发），
本层把它**替换**成 `nickname` / `avatar`，并补商品名兜底文案、只留商户端要看的字段——
与 `StoreGoodsSpuDetailBffVO` 同款「逐字段手工映射、**不用 `BeanUtils.copyProperties`**」。

⚠ `StoreGoodsSpuDetailBffVO` 是 **BFF 独有**的详情出参（**不继承任何域类型**）：域详情出参
`StoreGoodsSpuPlatformDetailVO` 是**管理端超集**（含 `lockUser` / `storeName` / …），
本层**逐字段手工映射**到自己的 VO（**不用 `BeanUtils.copyProperties`**），
`lockUser`（锁定人，仅管理端展示）与 `storeName` **刻意不在商户端出参里**——
换域出参类型时，**不因下游多了字段而扩大下发面**（见 [cross-cutting.md](./cross-cutting.md) 第 17 条）。

## 五、下游依赖（本层调谁）

| 目标 | 通道 | 内容 |
|---|---|---|
| store 域(8083) | Feign `StoreClient`（店主的店铺 / 商品能力，作用域写入入参 DTO） | 店铺详情（`getShop`）、店铺保存 / 提交；在售商品 CRUD、SKU 替换与上下架、库存；商品分页；**首页看板**：本店商品与库存规模（`getGoodsStats`）、本店评价星级分布（`evaluationStat`，带窗口） |
| goods-center(8081) | Feign `GoodsCenterClient` | 分类树、分类全路径、品牌列表、SPU 详情、按 SKU 编码反查 SPU |
| trade-center(8087) | Feign `TradeCenterClient` | 商户侧订单分页 / 详情 / 发货（`storeId` 锚点由本层从登录态取）；**首页看板**：本店订单统计（`getOrderStats`，同一路径传 `storeId`） |
| customer-center(8086) | Feign `CustomerCenterClient` | 评价人**昵称 / 头像**（`listProfilesByIds` 按 `customerId` 集合**一次**取回，**禁止逐条 `getProfile`**）——2026-09-24 因评价页新增的依赖 |

全部经 `common` 的 `BffFeignCall` 包装（剥 cause 链 + 降级文案）。降级口径见 [cross-cutting.md](./cross-cutting.md) 第 13 条。

## 六、业务规则去哪看

店主端页面行为、店铺资料字段、商品编辑约束等见 [`backend/store-bff/README.md`](../../backend/store-bff/README.md)。
