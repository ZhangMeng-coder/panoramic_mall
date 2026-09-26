<!-- contract-meta
service: trade-center
layer: internal
basePath: /internal/trade
feignClient: backend/trade-center-interface/src/main/java/com/panoramic/contract/trade/api/TradeCenterClient.java
implScanDirs: backend/trade-center/src/main/java/com/panoramic/trade/controller
typeDirs: backend/trade-center-interface/src/main/java
respEnvelope: RespData
-->

# 交易域（trade-center）内部契约 · 第 ② 层

> 交易域：**购物车 `trade_cart_item` + 订单**（`trade_order` / `trade_order_item` /
> `trade_order_status_log` / `trade_order_submission` / `trade_order_submission_order`）。
> **不暴露公网路由**，只被**端 BFF** 经内部 Feign 调用（购物车 ← mall-bff；订单同理按能力被各端 BFF 调用）。
> 域内**不做任何鉴权、不做权限判断**（见 [cross-cutting.md](./cross-cutting.md) 第 6、7、14 条）。

**共 17 个接口**：**购物车 8** + **订单 9**（**按能力**，不按端分侧）。
两条线**均已实现**（状态列无 `待实现`）。

## 一、归属与形状

- **作用域 `customerId` = `mall_user.id`**：跨域 id 引用、**无外键**，与 `store_id` = 店主账号 id 同一手法。
  **值只能是调用方登录态里的 id**（端 BFF 取 `LoginUser.getId()`，**禁止从前端入参透传**）；
  域侧**只做「传了就按它筛，没传就是不限定」，不判身份、不按端分流**。
- **按能力通用，不分侧**（[cross-cutting.md](./cross-cutting.md) 第 22 条）：同一能力**不分端**，
  路径段与方法名里不出现 `customer` / `store` / `platform` 之类端别子段，也**没有**成对的
  「顾客侧方法 / 商户侧方法」。⚠ 购物车 8 条的 `customerId` **一直必填**；
  订单 9 条里**读侧（分页 / 详情）的作用域可选**（管理端本就是合法全量视角），
  **写侧（下单 / 支付 / 发货 / 收货 / 改地址 / 取消 / 仅退款）必填**——写没有「合法全量视角」，省掉作用域就是「能改任意一笔单」。
- **作用域是入参 DTO 的字段，不进路径段**（同 §22 / §23）：单参能力收裸 `customerId`，
  其余并进各自 DTO。⚠ **路径变量是资源标识**（`{id}` / `{orderNo}`），不并入 DTO。
- **域内不做任何身份判断**：不判 `X-User-Type`、不校验 token、无 `@PreAuthorize`；
  身份头只读来填 `UserContext`，且**仅用于审计留痕**。
  调用方传的作用域是否真是「本人 / 本店」，**由端 BFF 从登录态取**，域侧不校验 —— 防线在 BFF。
- **形状**：出参**包 `RespData<T>`**（无返回值用 `RespData<Void>`）：业务结果（含业务失败 `code=400/403/404`）一律 **HTTP 200 + `{code,msg,data}`**，只有兜底异常才是 **HTTP 500** —— 见 [cross-cutting.md](./cross-cutting.md) 第 2、13 条。
  ⚠ **本文件下文里所有「回 400 / 404 / 4xx」都指 `RespData.code`**（HTTP 状态是 200），
  调用方看到的仍是同一个数字、同一句文案，只是它不再出现在 HTTP 状态行上。
- **前缀**：`/internal/trade`；拼法（不是 context-path、由 Controller 类级 `@RequestMapping` 写死）
  见 [README.md](./README.md) 的「内部 Feign 的「路径」前缀怎么来的」。
- ⚠ **本域是全仓库唯一加载 `datasource-redis.yml` 的域服务**（购物车的加购去重与计数缓存），
  是 [cross-cutting.md](./cross-cutting.md) 第 12 条「Nacos 加载矩阵」的**登记例外**。
  ⚠ 它**只把 Redis 当缓存/提示**，不改变「域内不鉴权」：Redis 里没有登录态，MySQL 始终是唯一事实源；
  两个方向的错判（误报/漏报）都由写路径自愈，见 [`backend/trade-center/README.md`](../../backend/trade-center/README.md)。
- ⚠ **购物车行是「C 端商品可见性」不变量（[cross-cutting.md](./cross-cutting.md) 第 20 条）的第三个落点**：
  可见性判定（店铺已审核 + SPU 已上架 + 未锁定）**不在域内做**，域只按 `spuId` / `skuId` 出原始行；
  「这一行还算不算可买」由 mall-bff 读时判定并打 `invalid` 标记。

## 二、接口清单

### 1. 购物车（8 条）

| Feign 方法 | 方法 | 路径 | 入参 | 出参 | 契约声明(接口模块) | 域实现 | 调用方 | 状态 |
|---|---|---|---|---|---|---|---|---|
| listCartItems | GET | /cart | `Long` | `RespData<List<TradeCartItemVO>>` | `TradeCenterClient#listCartItems` | `CartController#listCartItems` | CartBffService(mall-bff) |  |
| cartItemCount | GET | /cart/count | `Long` | `RespData<Integer>` | `TradeCenterClient#cartItemCount` | `CartController#cartItemCount` | CartBffService(mall-bff) |  |
| addCartItem | POST | /cart/items | `TradeCartItemAddDTO` | `RespData<Long>` | `TradeCenterClient#addCartItem` | `CartController#addCartItem` | CartBffService(mall-bff) |  |
| updateCartItemQuantity | PUT | /cart/items/{id} | `Long`, `TradeCartItemUpdateDTO` | `RespData<Void>` | `TradeCenterClient#updateCartItemQuantity` | `CartController#updateCartItemQuantity` | CartBffService(mall-bff) |  |
| setCartItemSelected | PUT | /cart/items/{id}/selected | `Long`, `TradeCartSelectDTO` | `RespData<Void>` | `TradeCenterClient#setCartItemSelected` | `CartController#setCartItemSelected` | CartBffService(mall-bff) |  |
| setAllCartItemsSelected | PUT | /cart/selected | `TradeCartSelectDTO` | `RespData<Void>` | `TradeCenterClient#setAllCartItemsSelected` | `CartController#setAllCartItemsSelected` | CartBffService(mall-bff) |  |
| removeCartItems | POST | /cart/items/remove | `TradeCartItemIdsDTO` | `RespData<Void>` | `TradeCenterClient#removeCartItems` | `CartController#removeCartItems` | CartBffService(mall-bff) |  |
| clearCart | DELETE | /cart | `Long` | `RespData<Void>` | `TradeCenterClient#clearCart` | `CartController#clearCart` | CartBffService(mall-bff) |  |

> ⚠ 作用域 `customerId` **不进路径段**（[cross-cutting.md](./cross-cutting.md) 第 22 / 23 条）：
> 只有**一个非路径入参**的三条（列表 / 计数 / 清空）收裸 `Long`，其余五条把它放进各自 DTO 的
> **`customerId` 字段（必填）**；`{id}` 是**行标识（路径变量）**，不并入 DTO。

> 字段定义**不在本表**，去 `trade-center-interface`（包根 `com.panoramic.contract.trade`）的
> `dto` / `vo` 包里看；表里只登记**有哪些接口、形状是什么、类型在哪、谁在调**，不抄字段。

> 删除接口用 **`POST .../items/remove` + `@RequestBody`**（而非 `DELETE` 带 body，或逐个 `DELETE`）：
> 批量删除口径与跨店通用侧同形，见 [cross-cutting.md](./cross-cutting.md) 第 18 条。

### 2. 订单（9 条，**按能力**）

域内订单领域模型与接口层均已落地（领域口径见 [`backend/trade-center/README.md`](../../backend/trade-center/README.md) 第 7 节）。

| Feign 方法 | 方法 | 路径 | 入参 | 出参 | 契约声明(接口模块) | 域实现 | 调用方 | 状态 |
|---|---|---|---|---|---|---|---|---|
| createOrder | POST | /order | `TradeOrderCreateDTO` | `RespData<List<TradeOrderVO>>` | `TradeCenterClient#createOrder` | `OrderController#createOrder` | `mall-bff/OrderBffService` |  |
| pageOrders | POST | /order/page | `TradeOrderPageQueryDTO` | `RespData<TradeOrderPageVO>` | `TradeCenterClient#pageOrders` | `OrderController#pageOrders` | `mall-bff/OrderBffService`、`store-bff/StoreOrderBffService`、`admin/AdminOrderBffService` |  |
| getOrder | GET | /order/{orderNo} | `String`, `TradeOrderQueryDTO` | `RespData<TradeOrderVO>` | `TradeCenterClient#getOrder` | `OrderController#getOrder` | `mall-bff/OrderBffService`、`store-bff/StoreOrderBffService`、`admin/AdminOrderBffService` |  |
| payOrder | POST | /order/{orderNo}/pay | `String`, `TradeOrderPayDTO` | `RespData<Void>` | `TradeCenterClient#payOrder` | `OrderController#payOrder` | `mall-bff/OrderBffService` |  |
| updateOrderAddress | PUT | /order/{orderNo}/address | `String`, `TradeOrderAddressUpdateDTO` | `RespData<Void>` | `TradeCenterClient#updateOrderAddress` | `OrderController#updateOrderAddress` | `mall-bff/OrderBffService` |  |
| shipOrder | POST | /order/{orderNo}/ship | `String`, `TradeOrderShipDTO` | `RespData<Void>` | `TradeCenterClient#shipOrder` | `OrderController#shipOrder` | `store-bff/StoreOrderBffService` |  |
| receiveOrder | POST | /order/{orderNo}/receive | `String`, `TradeOrderReceiveDTO` | `RespData<Void>` | `TradeCenterClient#receiveOrder` | `OrderController#receiveOrder` | `mall-bff/OrderBffService` |  |
| cancelOrder | POST | /order/{orderNo}/cancel | `String`, `TradeOrderCancelDTO` | `RespData<Void>` | `TradeCenterClient#cancelOrder` | `OrderController#cancelOrder` | `mall-bff/OrderBffService` |  |
| refundOrder | POST | /order/{orderNo}/refund | `String`, `TradeOrderRefundDTO` | `RespData<Void>` | `TradeCenterClient#refundOrder` | `OrderController#refundOrder` | `mall-bff/OrderBffService` |  |

> ⚠ **新增 2 条动作：取消（`cancelOrder`）/ 仅退款（`refundOrder`）**
> 形状与三个既有动作同形：`POST` 命令语义、出参 `void`（页面改完重拉详情 / 列表）、
> 作用域 `customerId` **必填**（写侧没有「合法全量视角」）。
> ⚠ **二者都写状态轨迹**——它们是**真正的状态变更**，与 `updateOrderAddress` 那条「不是状态流转、
> 不写轨迹」刻意相反（见下文改地址那条）。
> ⚠ 各自「从哪个状态可做、库存怎么还」属**业务规则**，见
> [`backend/trade-center/README.md`](../../backend/trade-center/README.md) 第 7 节，**本文件不写**。

> **订单 9 条的调用方**（三端 BFF 的订单编排均已落地）：页面级的对应行为
> [mall-bff.md](./mall-bff.md)（`/orders` 8 条）、[store-bff.md](./store-bff.md)
> （`/orders/page` / `/orders/{orderNo}` / `/orders/{orderNo}/ship`）、[admin.md](./admin.md)
> （`/orders/page` / `/orders/{orderNo}`）。
> ⚠ 「调用方」列**没有任何核对手段**（`drift-check` 只读「状态」列，不读这一列），写错了不会有任何东西报错：
> 删改这三端 BFF 的订单编排时，**顺手回看本列**。⚠ 也正因如此，这一列只写**已存在**的类名——
> 早先这里写过三个当时并不存在的服务类名，等于留一个查不到的引用。

> **作用域在入参 DTO 里**（[cross-cutting.md](./cross-cutting.md) 第 22 / 23 条）：域内不做身份判断
> （读作用域 ≠ 鉴权），值由**端 BFF 从登录态取**后填进 DTO 字段。分页 / 详情的 `customerId` / `storeId`
> **可选**——传了即「我的订单 / 本店订单」、不传即全量（管理端视角）；**写侧（下单 / 支付 / 发货 / 收货 /
> 改地址 / 取消 / 仅退款）必填**，由各自 DTO 上的 `@NotNull` 守，**域内另有一道断言**（绕过 MVC 直连时 `null` 会退化成
> 「不限定」→ 取任意一笔并改它，故域应用层再判一次、回 400）。
> ⚠ `{orderNo}` 是**资源标识（路径变量）**，不并入 DTO。
> ⚠ **同一能力只有一行**：顾客 / 商户 / 管理端调的是**同一个端点**，差别只在传不传作用域。

> ⚠ **订单标识一律用 `orderNo`（业务可读单号），不用自增 id**：单号已是唯一键、由「生成 → 查重 → 重试」保证不撞；
> 用 id 会让页面契约依赖一个落库后才存在、且不稳定（拆单顺序决定）的东西。

> ⚠ **空白 `{orderNo}` → `400`「订单号不能为空」**（见详情与三个动作）：单号是资源标识，
> 空白即没有「按单号取」这一项条件 → 会退化成「取作用域内的**任意一笔**」（多行时还会 500），
> 故在域实现的入口直接 400。⚠ 这是**入参不变量**，任何调用方都必经（不只是 `forDetail` 那个工厂），
> 端 BFF 原样透传即可。

> ⚠ **三个动作（支付 / 发货 / 收货）出参是 `void`**：写接口只表达「命令已生效」，页面重拉列表或详情拿新状态。
> 别再给它们各配一份订单 VO 出参——那是同一份形状的第二个出口。改地址同理（`void`，改完重拉详情）。

> ⚠ **改地址（`updateOrderAddress`）与三个动作的三点不同**，别按「第四个动作」对齐：
> ① **用 `PUT`**——语义是「替换该订单的地址子资源」（幂等），三个动作是命令语义的 `POST`；
> ② **不是状态流转**：状态原地不变、**不写 `trade_order_status_log`**（轨迹的语义是「走过哪些状态」，
> 其 `seq` 连续性是对账依据；混进与状态无关的行会让两种语义都不可断言）。留痕靠审计字段；
> ③ **只在 `PENDING_PAYMENT` 允许**：闸门是**业务规则**（付款后顾客与商家对「寄到哪儿」的共识不该被单方面
> 改写），与状态机的**迁移判据**无关，故落在聚合内而不是 `OrderStatusFlow` 里。非待支付回 400，
> 提示语可直接展示（「订单当前状态「已支付」不允许修改收货地址」）。
> ⚠ 它改的**只是这一笔订单的地址快照**，不动顾客地址簿、也不影响别的订单——正是「存快照而非存 id」的另一面。
> 落库是**条件更新**（条件 = 库里仍为待支付）：0 行时复用同一句 400，避免「读之后被支付抢先」时
> 盲写掉一笔已支付订单的地址。

> ⚠ **三个动作不幂等，重复提交由状态机拒**：每个动作只推一格，第二次同动作就是「重复变更」
> → `400`「订单状态不能从「已支付」重复变更到「已支付」」（提示语已带两侧文案，可直接展示）。
> 端 BFF **原样透传**该 `code=400`（[cross-cutting.md](./cross-cutting.md) 第 13 条），不另译成「请勿重复操作」
> ——同一句提示只此一份。前端**不必**为双击加特殊处理，但**不要**把这条 400 渲染成「系统异常」。

## 三、类型所在包（全部在 `trade-center-interface`，两端引用同一份）

包根：`backend/trade-center-interface/src/main/java/com/panoramic/contract/trade/`

| 包 | 类型 |
|---|---|
| `dto` | TradeCartItemAddDTO, TradeCartItemIdsDTO, TradeCartItemUpdateDTO, TradeCartSelectDTO；订单：TradeOrderCreateDTO, TradeOrderAddressDTO, TradeOrderAddressUpdateDTO, TradeOrderPageQueryDTO, TradeOrderQueryDTO, TradeOrderReceiveDTO, TradeOrderPayDTO, TradeOrderShipDTO, TradeOrderCancelDTO, TradeOrderRefundDTO |
| `vo` | TradeCartItemVO；订单：TradeOrderVO, TradeOrderPageVO |

> ⚠ 订单分页出参刻意叫 `TradeOrderPageVO` 而**不再加一个 `PageResult`**：本仓库已有 `contract.goods.vo` /
> `contract.store.vo` / admin 本地三份同形同名的 `PageResult`（「别引错包」清单见 [cross-cutting.md](./cross-cutting.md) 第 3 条），
> 第四个只会把那份清单继续撑大。各端 BFF 收到后**自行映射**成自己那份 `PageResult<…>`。

> ⚠ **作用域字段只有一个来源、只有一种语义**：读侧的可选锚点在 `TradeOrderPageQueryDTO` / `TradeOrderQueryDTO`
> 上（`customerId?` / `storeId?`，不填即不限定），写侧的必填锚点在各自动作 DTO 上（`@NotNull`）。
> 已**删除**早先的 `TradeOrderPlatformPageQueryDTO`（端别子类）——端别差异是**调用方传不传**，不是类型差异
> （[cross-cutting.md](./cross-cutting.md) 第 22 条）。⚠ 写侧**不得**复用读侧那两份可选锚点 DTO：
> 省掉锚点就是「能改任意一笔单」，且**不报错、只写错**。

> ⚠ `TradeOrderCreateDTO` 里的收货地址是**快照**（`TradeOrderAddressDTO`：收件人 / 电话 / 地区 / 详址）、**必填**，
> **不是 `addressId`**：trade-center **结构上不能调 customer-center**（每个域只依赖自己的 `<域>-interface`），
> 归属校验与取地址由 **mall-bff** 做完再传快照（见 [mall-bff.md](./mall-bff.md) 与 [customer-center.md](./customer-center.md)）。
> 快照是**下单当时的地址**，此后顾客改地址 / 删地址都不影响已下的单——这正是存快照而非存 id 的理由。

## 四、业务规则去哪看

购物车行的落库口径（`(customer_id, sku_id)` 唯一键 + **物理删除**）、数量与行数上限（单行 ≤ 999、单购物车 ≤ 100 行）、
加购的「查重 → 自增」双向回退、选中状态持久化、Redis 两处失效的可自愈性、
以及**订单领域模型**的完整口径（状态机 / 配置驱动的生成流水线 / 一单一店拆单 / 两级幂等 / 回滚与保存时机）——
见 [`backend/trade-center/README.md`](../../backend/trade-center/README.md)「三、职责与边界」第 7 节。
⚠ 订单侧**领域模型与接口层均已落地**：接口的形状见上面第二节的订单表，
支付金额校验、发货单号校验、状态流转等**行为口径**归
[`backend/trade-center/README.md`](../../backend/trade-center/README.md)「三、职责与边界」第 7 节。
本文件只写接口与形状。
