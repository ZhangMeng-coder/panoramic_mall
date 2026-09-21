# trade-center — 交易域（下沉纯域）

全景商城**交易域**（Servlet 技术栈，2026-09-21 新建），端口 **8087**。

本域持**购物车 `trade_cart_item`** 与**订单**（`trade_order` 等 5 张表，`com.panoramic.trade.order`，DDD 三层）。
⚠ 订单**已落库**（2026-09-21 阶段一）；商品 / 库存两个下游仍是内存脚手架，接真实 store 域是阶段二。
结账、评价与 Seata 接入**不在本期**，计划见仓库根 [`todo.md`](../../todo.md)。

> 接口清单与**实现进度不在这份文件里维护**——见 [`docs/contracts/trade-center.md`](../../docs/contracts/trade-center.md)：
> 那张表由 `docs/contracts/drift-check.mjs` 与代码**双向核对**，始终反映真实进度（本 README 里写死条数只会随每次实现失真）。

## 一、架构位置

**下沉纯域（第 ② 层）**：不向页面暴露公网路由，只被端 BFF 经注册中心**内部 Feign** 调用。

| 方向 | 对象 | 通道 |
|---|---|---|
| 被谁调 | mall-bff（**C 端顾客自助购物车**） | `trade-center-interface` 的 `TradeCenterClient`，带熔断降级 |
| 本域调谁 | — | **不启用 Feign 客户端，纯被调方** |

- 顾客账号 `mall_user`（手机号 / 密码 / 登录态）归 **mall-bff**；顾客资料与收货地址归 **customer-center**；
  店铺商品（SPU / SKU / 价格 / 上下架）归 **store 域**。本域**只记 `spuId` / `skuId` 的 id 引用，不持商品快照**
  （加购时刻的价格到结算时早已过期，任何快照都是错的）
- 本域只依赖 `common`（**不依赖 `common-auth`**）→ 结构上拿不到认证链：**不做鉴权、不碰 token、不查登录态**，
  防线在网络层（8087 端口只在内网可达），不在应用层
- ⚠ 本域**破例**加载了 Redis（见下节），但这不改变上面任何一条：Redis 里没有身份、没有登录态，**它不是鉴权组件**

> 📋 对外接口清单见 [`docs/contracts/trade-center.md`](../../docs/contracts/trade-center.md)。
> 本 README 只讲**这服务是什么、持什么、做什么**；接口、形状、类型位置一律不在此处重复。

## 二、实体标记

库：`panoramic_mall`（与其它服务同库，只分表所有权）。

| 表 | 归属 | 说明 |
|---|---|---|
| `trade_cart_item` | **trade-center（本域）** | 购物车行（`customer_id` + `spu_id` / `sku_id` + `quantity` + `selected`；**唯一键 `uk_customer_sku (customer_id, sku_id)`**，即同一顾客同一 SKU 至多一行） |
| `trade_order` | **trade-center（本域）** | 订单主表（一笔订单一店；`order_no` 唯一，地址四项为快照，`fingerprint` 为 L2 幂等键） |
| `trade_order_item` | **trade-center（本域）** | 订单明细（一行 = 一个 SKU；快照字段下单即冻结；`uk_order_sku (order_no, sku_id)`） |
| `trade_order_submission` | **trade-center（本域）** | 订单提交记录（**唯一键 `uk_customer_request (customer_id, request_id)` = L1 幂等键**，先占键的落点） |
| `trade_order_submission_order` | **trade-center（本域）** | 提交记录 ↔ 订单关联（一次请求落了哪几笔；纯关联表，无审计列、物理删除） |
| `trade_order_status_log` | **trade-center（本域）** | 状态轨迹（一行 = 一次状态变更，`uk_order_seq (order_no, seq)`；`create_time` 即变更时刻） |
| `mall_user` | mall-bff | 顾客账号（**不在本域**） |
| `customer_profile` / `customer_address` | customer-center | 顾客资料 / 收货地址（**不在本域**） |
| `store_goods_spu` / `store_goods_sku` | store | 店铺商品（**不在本域**；本域只存 id） |

建表脚本：`src/main/resources/db/schema.sql`（`CREATE TABLE IF NOT EXISTS`，可重复执行，纯新增）。

实体沿用 common `BaseEntity`（审计字段自动填充，代码不得显式赋值；取值格式见 `CLAUDE.md`「代码生成与分层约定」）。

### 1. 为什么走**物理删除**（本表 `is_delete` 恒 0）

全仓其它实体一律逻辑删除，本表是**刻意的例外**，理由是它与本表的唯一键**互斥**：

- 逻辑删除只把 `is_delete` 置 1，**行仍在表里、仍参与唯一键**；
- 于是「删掉某 SKU → 再加回同一 SKU」这种最普通的操作，插入会直接撞
  `uk_customer_sku (customer_id, sku_id)` → 用户看到的是「删了以后再也加不回来」。

故（用户已裁定）购物车走物理删除，`is_delete` 列**恒 0**，只为对齐 `BaseEntity` 的 `@TableLogic` 而保留。
⚠ 随之而来的纪律：**本表禁止调用 `removeById` / `remove(...)` / `IService#remove*`**（那是逻辑删除，
会把「再加购」变成 500）——删行只走 `TradeCartItemMapper` 的三个 `physicalDelete*`。
唯一键本身是「同一 SKU 至多一行」这个不变量的承担者：重复加购由它收敛成一条 UPDATE（累加数量），
并发首插也由它收敛成「一个成功、一个改走自增」。

## 三、职责与边界

### 1. 数据权限模型

- **锚点 `customerId` = `mall_user.id`**（跨域 id 引用、**无外键**）：域内所有方法**全按传入锚点过滤**，`customerId` 就是数据权限本身。
- **无 owner / platform 分侧**：本期只做 C 端自助，没有平台侧能力，故不像 store 那样由「BFF 调哪一侧」分流。
- **域内不做任何身份判断**：不判 `X-User-Type`、无 `@PreAuthorize`；调用方传的 `customerId` 是否「本人」**由 mall-bff 从登录态取**，域侧不校验——防线在 BFF。

### 2. 上限口径

| 维度 | 上限 | 超限 | 封顶位置 |
|---|---|---|---|
| 单行数量 `quantity` | ≤ 999 | 400（插入路径）/ 静默封顶（累加路径） | 插入路径由 DTO 的 `@Max(999)` 拦；累加路径由自增 SQL 的 `LEAST(quantity + delta, 999)` 在 SQL 里拦 |
| 单购物车行数 | ≤ 100 行 | 400「购物车最多 100 种商品，请先清理」 | 只在**确实要走插入**时判（累加既有 SKU 不受「车满了」影响，否则用户连同一件商品都加不了）。⚠ **软上限**：并发下两笔可双双通过检查而略微越界，同 customer-center 的 20 条地址上限口径 |

⚠ **数量上限必须两条路径各封一次**：只封插入路径的话，反复加购同一 SKU 就能把 `quantity` 顶到 INT 上限。

### 3. 为什么本域**破例**加载 Redis（全仓唯一的域服务）

两个键，**都是性能优化**，不是事实源：

| 键 | 类型 / TTL | 角色 |
|---|---|---|
| `panoramic:cart:skus:{customerId}` | Set（成员=skuId 字符串）/ 24h | 加购的**第一遍重复过滤**：命中即走原子自增，省掉一次「先查后插」。⚠ 只放 id、**绝不放数量**（放了就有两个数值源、就有分歧） |
| `panoramic:cart:count:{customerId}` | String（值=行数）/ 60s | 购物车**行数的读穿透缓存**。⚠ **不许用 `INCRBY` 之类的算式维护**（漏算一次就永久偏掉）：口径一律「**写路径 DEL、读路径穿透**」，最坏只是少一次命中，**不会算错** |

- ⚠ **这不是把鉴权下沉**：Redis 里没有登录态、没有 token、没有权限快照，本域仍然不鉴权、不查登录态。
  它是 [cross-cutting.md](../../docs/contracts/cross-cutting.md) 第 12 条「Nacos 加载矩阵」的**登记例外**
  （其余域服务都不引 `datasource-redis.yml`）。
- **MySQL 是唯一事实源**，两个键与库数据**无强一致要求**：Redis 不可用时 `CartRedisCache` 的所有方法
  **吞异常 + `log.warn` 降级**（读按未命中、写当没写过），**绝不让加购因为 Redis 失败**。
- **两处失效都能自愈**（两个方向的错判都只影响「走哪条支路」，不影响结果）：

| 失效方向 | 触发场景 | 自愈路径 |
|---|---|---|
| **误报**（Set 里有、库里没这行） | 行被并发删除 / Set 里是上次清空前的残留 | 原子自增匹配 **0 行** → **回落到插入** |
| **漏报**（Set 里没有、库里其实有） | Set 过期、Redis 曾不可用、两笔并发首插 | 插入撞**唯一键** `DuplicateKeyException` → **回落到原子自增** |

- 计数缓存另有一层**已知的短暂不一致**（设计上接受，故它只用于角标）：写路径先落库、再 DEL 键，
  中间若有并发读，它会用**提交前**的值回写该键，于是该值可能在 TTL（60 秒）内偏掉，随后自愈。

### 4. 「0 行受影响」的两种口径（刻意不一致，别整齐化）

| 支路 | 0 行的含义 | 处理 |
|---|---|---|
| 按 id 改单行（改数量 / 改选中） | 「这行不存在或不属于你」——与「改成功」是**两件事** | **抛 404**，不回假成功（并发删除时页面必须知道这次改动没落到任何行上） |
| 批量删 / 清空 / 全选 | 「这些行现在都不在车里了」——正是调用方要的结果 | **幂等**，0 行不报错（多选删除不该因某行被另一个标签页并发删掉而整批失败） |

两条都与 customer-center 的同名口径一致（`CustomerAddressServiceImpl#requireOwned` / `deleteAddress`）。

### 5. 边界（本域不做什么）

- **不做任何鉴权、不做权限判断、不校验 token**；唯一授权点是 mall-bff 从登录态取 `customerId`
- **不装配认证链**：不打 Redis 登录态、不查 token；`application.yml` 不声明 auth 白名单
- **不判商品可见性**：店铺是否已审核 / SPU 是否上架 / 是否被平台锁定**都在 mall-bff 读时重判**，
  域只按 `spuId` / `skuId` 出原始行（见 cross-cutting 第 20 条：购物车行是「C 端可见性」不变量的**第三个落点**）。
  同理，全选是**域侧整表**操作，作用面含 BFF 眼里「已失效」的行
- **不加购时校验商品存在性**：域不持商品、不调 store（加购不做跨域调用）；商品下架 / 不存在由 BFF 与页面处理
- **不做订单接口 / 订单落库 / 结账 / 评价**：**订单领域模型已建**（见下节，**无接口、不落库**，只由单测验证）
- **不做页面编排**（商品详情拼装、失效标记、汇总金额都在 mall-bff）

### 6. 信任与防线

入口只有两道：

1. `TradeUserIdentityFilter` —— 把透传的 `X-User-Id`/`X-User-Type` 直取填 `UserContext`，供审计填充；**缺头即不填充、放行**，不回 401
2. 本地 `TradeSecurityConfig` —— 唯一一条全放行链，避免 Spring Security 默认链拦截 actuator

> ⚠ 本域**不做鉴权是有意设计，不是疏漏**。安全性完全依赖 `8087` 端口只在内网可达。
> `customerId` 直接取自请求路径且域侧不判归属——「锚点即数据权限」这条口径只在
> 「路径上的 `customerId` 由 mall-bff 从登录态填」+「8087 不可从公网抵达」**同时**成立时才成立。

### 7. 订单领域（DDD 三层，已落库）

`com.panoramic.trade.order`，DDD 三层：`domain`（**零 Spring 依赖**，纯 POJO）/ `application`（步骤流水线 + 编排）/ `infrastructure`（适配器 + Spring 装配）。
订单**已真实落库**（`trade_order` 等 5 张表，见「二、实体标记」）；**Seata 仍未接入**。

⚠ **订正一句早期说法**：曾写过「落库只换适配器，`domain` / `application` 一行都不用动」。那句话**是错的**——落库期实际动了三处且都是必需的：收货地址进模型、快递单号进模型、支付金额校验进聚合（理由见 `OrderModel` 的类注释）。适配器能替换的只是**外部依赖**（仓库 / 商品 / 库存），订单**自己有什么**必须由聚合说完。

三个**端口**就是「换适配器不动业务」的接缝：

| 端口 | 当前实现 | 未来 |
|---|---|---|
| `GoodsQueryPort`（商品快照 + 可见性） | 内存脚手架（读 `resources/mock-store-data.json`：store 域的**只读快照**） | store 域 `StoreClient`（Feign，带熔断降级） |
| `StockPort`（扣减 + 按单回补） | 内存脚手架（进程内账，不回写 store 域，重启回到快照值） | store 域库存能力（`UPDATE ... WHERE stock >= ?` 的影响行数 + 同事务写流水） |
| `OrderRepository`（订单 + **提交记录**，两级幂等的落点） | `JdbcOrderRepository`（5 张表，MyBatis-Plus 基类，**无自定义 SQL**） | 无需更换 |

⚠ 商品 / 库存两处脚手架、`mock-store-data.json` 与 `infrastructure/inmemory` 整包随 store 域落地一起删除（todo 残留 9）；开关是 `panoramic.trade.order.store-adapter=mock|feign`，**阶段一的默认值是 `mock`**（mock 侧带 `matchIfMissing`，它被删除的同一改动里默认值翻到 `feign` 侧）。订单仓库另有 `panoramic.trade.order.repository=jdbc|memory` **不设默认值**（「缺失该按哪个」没有唯一答案，故二选一必须显式写、缺失即启动失败；`memory` 供无数据源的装配层单测）。

- **状态机**：四态 `PENDING_PAYMENT` / `PAID` / `SHIPPED` / `RECEIVED`。枚举常量名即 todo 说的 `name`，两侧文案分别是 `mallLabel`（C 端）与 `storeAdminLabel`（商户 / 管理端）——同名状态两端叫法不同（`PAID` 在 C 端是「已支付」、商户端是「待发货」）
- **流转顺序由配置决定**（`panoramic.trade.order.status-flow`），域内**只允许「下标 +1」**：跳级 / 回退 / 未知状态一律业务错；配置缺任一枚举常量、或含重复项 → **装配即失败**。**不做取消、不做超时关单**（取消是唯一不按线性顺序走的状态，将来要做需给 `OrderStatusFlow` 加前驱集合）
- **生成流水线**：`goods-check`（商品存在 + 店铺已审核 + SPU/SKU 已上架 + 未平台锁定，并把商品快照冻进订单项）→ `stock-check`（逐行原子扣减；SKU 粒度的出库流水由库存实现自己记，端口不暴露流水）→ `price-compute`（取单价、算行小计与总价、`seal()` 封模型）。三步都是**可插拔实现**（`OrderCreateStep` bean），**启哪些、什么顺序由 `panoramic.trade.order.steps` 决定**，配了不存在的步骤名 → 装配即失败。⚠ 步骤间**只经 `OrderModel` 本体传参**；模型必须走完 `open → 补商品快照 → 补价 → seal` 才能被置为待支付——**顺序写反会直接抛错，不会静默出一张残单**
- **一单一店**：一次提交按 `storeId` 拆成多笔订单（各店的金额 / 状态 / 扣减相互独立），按 `storeId` 升序处理
- **订单号**：`yyyyMMddHHmmss` + 4 位序列，生成后查重、冲突则重试（上限 `panoramic.trade.order.order-no-max-retry`）；`Clock` 与序列源可注入——单测靠它钉死时间与「故意撞号」
- **重复提交（两级判定）**：
  - 一级 = **先占键**：`trade_order_submission` 的**唯一键 `(customer_id, request_id)`** 就是幂等键——`OrderRepository#occupy` 先插一行，**再**执行业务（拆单 / 扣库存 / 落库）。并发重复提交由**唯一索引的行锁**串行化：后到者阻塞到先到者提交，然后拿到重复键 → 回读**先到者那一批**；先到者业务失败回滚 → 键随事务一起消失，后到者成为新的「第一个」（失败不留残键不需要任何补偿动作）
  - 一级的返回口径 = **提交关联** `trade_order_submission_order` 说的：一批里可能含复用笔（指纹命中），它们的 `requestId` 是**上一次提交**的值，按 requestId 查会少返回几笔（用户侧表现为「下单成功但少了一笔」）。⚠ 复用笔的 `requestId` 字段保持**它原本的值**（记的是「哪次提交创造了这笔单」），不被后来的提交改写；关联行则**无条件写**——本次提交的成员关系是新的
  - 一级的作用域是**顾客内**（`customerId + requestId`）：跨顾客不共享，否则 A 用过的 `requestId` 能把 A 的订单取给 B
  - ⚠ **键与订单必须同事务**：`occupy` 若自己单独提交，键就先落地——先到者随后失败时键残留，重放会回读到一个**空批次**。故事务边界在 `OrderCreateCoordinator#create`（`@Transactional`），**不在端口方法上**；已知代价是后到者最多阻塞到 `innodb_lock_wait_timeout`（默认 50s），这是「宁可慢也不重复下单」的取舍
  - 二级 = **指纹** `sha256(customerId|source|storeId|排序后的 skuId:qty)`，逐笔在**窗口内**判定（`idempotency-window-seconds`，闭区间），命中则复用该笔。⚠ 复用笔属于**上一次提交**：它**不扣库存、不回补、不重复保存**——对它回补等于把上次真实下单占用的库存还回货架（超卖）
  - ⚠ 代价（`requestId` 的定义使然，不是缺陷）：同一 `requestId` 被**换内容**复用（客户端 bug）时，返回的是首次那批，新内容不会被下单
  - ⚠ **重放返回的是订单「当前」状态**：落库实现按关联回读（天然是最新真相），内存实现存的是**活引用**（只拷列表不拷元素）——代价是调用方改了返回值就等于改了凭证，**调用方不得修改返回的订单**
- **失败回滚**：任一笔失败即整次提交回滚（库内写入随事务消失，**失败不留残单**），只剩「回补库存」要还——库存在当前实现里是**外部资源**（内存脚手架 / 将来 store 域），不随本地事务回滚，必须显式归还。⚠ **只回补本次新建的笔**：复用笔的库存在上一次就扣过了
- ⚠ **回补按单、幂等由净额算出来**（`StockPort#revertByOrder(orderNo)`）：实现按流水算出「这一单每个 SKU 还欠多少」，还完净额归 0，重复调用即无欠可还。**不得**改成按 `orderNo + skuId` 记「已回补」标记的去重——失败提交从不落库，同一秒的两次失败提交可能拿到同一个单号，那个标记会**静默吞掉第二次回补**（库存净亏、流水却显示 0）。⚠ 同样**不得**改成逐行回补：`goods-check` 失败或某行库存不足时那一行**从没扣过**，逐行回补会把库存冲多且不报错
- **读取时对账**：库里读到的数据也要自证——枚举名可解析、明细小计 = 单价×数量、轨迹 `seq` 连续且是合法的「下标 +1」路径、落库的总件数 / 总金额与按行重算的一致。任一条不符即 `IllegalStateException`（**数据被写坏**，不是 400——报成 400 等于把「库里的数据坏了」说成「你的操作不对」）
- **Seata 落点**：`OrderCreateCoordinator#create` 的方法入口（将来在那里加 `@GlobalTransactional`），真实库存写入方在 store 域。本期**不引依赖、不加注解**——没有跨服务调用时它没有事务可管
- **验证**：`mvn -pl trade-center -am clean test`。纯 JUnit：`domain` / `application` 层**不启 Spring**；只有装配层用 `@SpringJUnitConfig`。⚠ 本域**不能写 `@SpringBootTest`**——`spring.config.import` 不带 `optional:`，没有 Nacos 时上下文启动即失败，不是可绕过的选项

## 四、配置说明

- **数据源**：连接信息由 Nacos 共享配置 `datasource-mysql.yml` 提供；**Redis** 由 `datasource-redis.yml` 提供
  （`REDIS_HOST`/`REDIS_PORT`/`REDIS_PASSWORD` 可覆盖）。连接其他库请注入环境变量
  `MYSQL_HOST`/`MYSQL_PORT`/`MYSQL_DB`/`MYSQL_USERNAME`/`MYSQL_PASSWORD`（账号密码勿写入代码或提交到仓库）
- **Nacos 共享配置加载**：引入 `datasource-mysql.yml` **与** `datasource-redis.yml`，且 import **不带 `optional:`**
  ——缺任一 dataId 则启动失败。加载矩阵见 [cross-cutting.md](../../docs/contracts/cross-cutting.md) 第 12 条
- MyBatis-Plus：主键 `IdType.AUTO`（`trade_cart_item.id` 自增）；**本表 `is_delete` 恒 0**（物理删除，
  见上文「为什么走物理删除」）；驼峰映射
- 启动类扫描 `com.panoramic` 以加载 common 的全局异常处理、分页插件、字段自动填充与安全链
- 异常语义（内部）：经 `TradeDomainExceptionHandler` 还原**真实 HTTP 状态 + `{code,msg}`**，供内部 Feign
  ErrorDecoder 还原为 `ServiceException`。⚠ **兜底 `Exception` → HTTP 500 这一形状不得改**（4xx/5xx 分野是熔断契约，
  见 cross-cutting 第 13 条）
- 响应结构：**本域内部接口不包 `RespData`**（`RespData` 只用于端 BFF 的对外接口）
