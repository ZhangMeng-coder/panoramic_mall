<!-- contract-meta
service: mall-bff
layer: page
baseUrl: /mall
scanDirs: backend/mall-bff/src/main/java/com/panoramic/mallbff/controller
typeDirs: backend/goods-center-interface/src/main/java, backend/store-interface/src/main/java, backend/common/src/main/java, backend/mall-bff/src/main/java
-->

# 商城前台 BFF（mall-bff）· 第 ① 层

> 商城**前台（C 端顾客）**的端 BFF，端口 **8085**，网关前缀 `/mall/**`（`StripPrefix=1`）。
> 它是本仓库的**第三个端 BFF**（另两个是 admin / store-bff），签发**第三套身份** `type=user`。
> 服务范围 = **顾客账号骨架**（取码 / 注册 / 登录 / 登出 / me / 换绑手机号）+ **顾客资料与收货地址**
> （资料保存 / 地址增删改查 / 设默认 / **地址状态**）+ **C 端商品浏览**（分类树 / 商品分页 / 筛选聚合 / 商品详情）
> + **购物车**（加购 / 列表 / 计数 / 改数量 / 选中 / 删除 / 清空）
> + **订单**（下单 / 列表 / 详情 / 支付 / 确认收货 / **改收货地址** / **取消订单** / **仅退款**）。
> 已接 **goods-center**（分类树）、**store**（商品分页 / 筛选聚合 / 详情 / 批量详情）与
> **customer-center**（顾客资料与收货地址）三个业务域（地址见 [customer-center.md](./customer-center.md)）、
> **trade-center**（购物车与订单，见 [trade-center.md](./trade-center.md)）；
> ⚠ 换绑手机号**不经任何域**——手机号是 `mall_user` 的列（本端独有），`customer_profile` 没有该字段；
> 首页「热门商品列表」区块仍是静态 mock。

## 一、接口形态

页面级通用规则见 [README.md](./README.md)：**必包** `RespData{code,msg,data}`。
字段定义**不在本表**，去下列类型所在的源码看（表里不抄字段，抄一份就是制造第二个会漂移的地方）。

| 类型 | 所在包 |
|---|---|
| **mall-bff 私有**（`mallbff/dto/`、`mallbff/vo/`） | 账号：`SmsCodeDTO` / `RegisterDTO` / `LoginDTO` / `ChangePhoneDTO` / `LoginResultVO` / `CurrentUserVO`；资料与地址：`ProfileSaveDTO` / `AddressSaveDTO` / `AddressVO` / `AddressStatusVO`；C 端商品：`MallGoodsPageQueryDTO` / `MallFacetQueryDTO` / `MallGoodsItemVO` / `MallFacetVO` / `MallFacetItemVO` / `MallGoodsDetailVO` / `MallGoodsSkuVO`；购物车：`MallCartItemAddDTO` / `MallCartItemUpdateDTO` / `MallCartSelectDTO` / `MallCartItemIdsDTO` / `MallCartVO` / `MallCartShopVO` / `MallCartItemVO`；订单：`MallOrderCreateDTO` / `MallOrderPageQueryDTO` / `MallOrderPayDTO` / `MallOrderAddressUpdateDTO` / `MallOrderVO` |
| `TradeCartItemVO` 等 | `backend/trade-center-interface/src/main/java/com/panoramic/contract/trade/`（购物车**域**的类型；⚠ 页面出参**不是**它——BFF 汇总成 `MallCartVO`，域类型不出网关） |
| `CategoryTreeVO` | `backend/goods-center-interface/src/main/java/com/panoramic/contract/goods/vo/` |
| `SpecConfigItem` / `SpecAttr` | `backend/store-interface/src/main/java/com/panoramic/contract/store/dto/`（详情页的规格配置与 SKU 规格属性，**数据来自 store 域**）⚠ `contract.goods.dto` 下有同形同名的孪生类，**别引错**（见 [cross-cutting.md](./cross-cutting.md) 第 3 条） |
| `PageResult` | `backend/store-interface/src/main/java/com/panoramic/contract/store/vo/` ⚠ 与 `contract.goods.vo.PageResult` 同名不同包，本模块用的是 **store** 那个（见 [cross-cutting.md](./cross-cutting.md) 第 3 条） |
| `RespData` | `backend/common/src/main/java/com/panoramic/common/vo/` |

## 二、接口清单（33 条）

| 方法 | 路径 | 权限串 | 入参 | 出参 | 声明位置 | 状态 |
|---|---|---|---|---|---|---|
| POST | /auth/sms-code | — | `SmsCodeDTO` | `Void` | AuthController.java:39 | |
| POST | /auth/register | — | `RegisterDTO` | `LoginResultVO` | AuthController.java:48 | |
| POST | /auth/login | — | `LoginDTO` | `LoginResultVO` | AuthController.java:56 | |
| POST | /auth/logout | — | — | `Void` | AuthController.java:64 | |
| GET | /auth/me | — | — | `CurrentUserVO` | AuthController.java:76 | |
| GET | /catalog/categories | — | — | `List<CategoryTreeVO>` | CatalogController.java:46 | |
| POST | /catalog/goods | — | `MallGoodsPageQueryDTO` | `PageResult<MallGoodsItemVO>` | CatalogController.java:61 | |
| POST | /catalog/facets | — | `MallFacetQueryDTO` | `MallFacetVO` | CatalogController.java:69 | |
| GET | /catalog/goods/{id} | — | `Long` | `MallGoodsDetailVO` | CatalogController.java:80 | |
| PUT | /profile | — | `ProfileSaveDTO` | `Void` | ProfileController.java:36 | |
| GET | /addresses | — | — | `List<AddressVO>` | AddressController.java:46 | |
| POST | /addresses | — | `AddressSaveDTO` | `Long` | AddressController.java:54 | |
| PUT | /addresses/{id} | — | `Long`, `AddressSaveDTO` | `Void` | AddressController.java:62 | |
| DELETE | /addresses/{id} | — | `Long` | `Void` | AddressController.java:71 | |
| POST | /addresses/{id}/default | — | `Long` | `Void` | AddressController.java:80 | |
| GET | /addresses/status | — | — | `AddressStatusVO` | AddressController.java:62 | |
| POST | /auth/phone | — | `ChangePhoneDTO` | `Void` | AuthController.java:86 | |
| GET | /cart | — | — | `MallCartVO` | CartController.java:59 | |
| GET | /cart/count | — | — | `Integer` | CartController.java:67 | |
| POST | /cart/items | — | `MallCartItemAddDTO` | `Long` | CartController.java:77 | |
| PUT | /cart/items/{id} | — | `Long`, `MallCartItemUpdateDTO` | `Void` | CartController.java:85 | |
| PUT | /cart/items/{id}/selected | — | `Long`, `MallCartSelectDTO` | `Void` | CartController.java:95 | |
| PUT | /cart/selected | — | `MallCartSelectDTO` | `Void` | CartController.java:105 | |
| POST | /cart/items/remove | — | `MallCartItemIdsDTO` | `Void` | CartController.java:114 | |
| DELETE | /cart | — | — | `Void` | CartController.java:123 | |
| POST | /orders | — | `MallOrderCreateDTO` | `List<MallOrderVO>` | OrderController.java:58 | |
| POST | /orders/page | — | `MallOrderPageQueryDTO` | `PageResult<MallOrderVO>` | OrderController.java:68 | |
| GET | /orders/{orderNo} | — | `String` | `MallOrderVO` | OrderController.java:76 | |
| POST | /orders/{orderNo}/pay | — | `String`, `MallOrderPayDTO` | `Void` | OrderController.java:84 | |
| POST | /orders/{orderNo}/receive | — | `String` | `Void` | OrderController.java:94 | |
| PUT | /orders/{orderNo}/address | — | `String`, `MallOrderAddressUpdateDTO` | `Void` | OrderController.java:100 | |
| POST | /orders/{orderNo}/cancel | — | `String` | `Void` | OrderController.java:125 | |
| POST | /orders/{orderNo}/refund | — | `String` | `Void` | OrderController.java:140 | |

> ⚠ **新增 2 条：取消订单 / 仅退款**。两者都是 `POST` 命令语义 + 出参 `Void`（页面重拉详情 / 列表），
> **入参只有路径里的 `orderNo`，没有本端 DTO**（与域侧一致——这正是「同一份形状不造第二个出口」）。
> ⚠ 「从哪个状态可做、库存怎么还」属**业务规则**，闸门在**域内**（状态机），本层不重判、原样透传 `400`。
> 详见 [trade-center.md](./trade-center.md) 第二节第 2 小节。

> 订单的域侧契约（作用域入参 / 出参类型）见 [trade-center.md](./trade-center.md) 第二节第 2 小节。

> ⚠ 订单**路径标识用 `orderNo`（业务可读单号）**，不是自增 id——理由与形状见 [trade-center.md](./trade-center.md)。

⚠ **权限串一律为空**：C 端顾客**不接 RBAC**（与店主端同理），本模块没有、也不应有任何 `@PreAuthorize`。
登录后顾客对自己的数据全权限——**这是预期状态，不是漏登记**。

### 形状与行为口径（表里放不下的）

> 账号形态、验证方式（模拟短信）、免鉴权路径与鉴权分级、登录态键、身份类型绑定、内部依赖（三域 Feign +
> `BffFeignCall`）、C 端商品展示口径、详情可见性 / 不可见 / 裁剪、详情描述消毒 —— 这些属**跨服务或模块级口径**，
> 见 [`backend/mall-bff/README.md`](../../backend/mall-bff/README.md) 与
> [cross-cutting.md](./cross-cutting.md) 第 5、9、11、17、19、20、21 条。
> 下表只留**本端接口自身的形状与行为**（表里放不下的那些）。

| 项 | 口径 |
|---|---|
| 未认证响应 | HTTP **401** + `{code:401,msg}`（`common-auth` 的 `AuthenticationEntryPoint` 写出，网关侧同形）。⚠ 对本端前端而言 401 是**可预期的日常分支**（提示登录并跳转），不是故障：别把它与「商品暂不可用」那类下游降级混在一个出口里 |
| 错误码 | 验证码错误 `400`；手机号已注册 `400`；手机号未注册 `400`；新手机号与当前手机号相同 `400`（换绑）；账号停用 `USER_DISABLED`（`515`） |
| 校验顺序 | 注册：验码 → 手机号查重 → 建号；登录：验码 → 查账号 → 查状态；换绑：验旧码 → 验新码 → **拒绝同号 → 新号查重** |
| | ⚠ 换绑的「拒绝同号」必须**先于**「新号查重」：反过来的话新号 == 旧号会先被查重命中、报「手机号已注册」，刚登记的「新手机号与当前手机号相同」400 **永不可达**（spec §6.3 原写的顺序即反例，已订正） |
| 资料读口径 | **不单开 `GET /profile`**：资料读合并在 `/auth/me` —— 其出参含**完整资料**（`nickname` / `avatar` / `gender` / `birthday`）。要改资料走 `PUT /profile`（只写） |
| 资料写口径 | `PUT /profile` 是**整份替换**（四项全传，未传即写为 NULL）；资料页每次全量提交。要「只改一个字段」得先读 `/auth/me` 拿到完整资料再整体回传 |
| 昵称兜底 | 资料为空（或 customer-center 不可用）时 `nickname` **回退为手机号**，其余资料字段留空、**不阻断** `/auth/me`。⚠ **只在 `/auth/me` 一处兜底**，别在别处再写一份。⚠ 与「新顾客还没填过资料」在出参上**不同形**：靠下一行的 `profileLoaded` 区分，**别用「昵称是否等于手机号」去猜**（真拿手机号当昵称的顾客会被判错） |
| 资料可用性 | `CurrentUserVO.profileLoaded` = **本次是否真的从 customer-center 读到资料**：`true` = 域读成功（此时资料四项为 null 即「顾客没填」）；`false` = 读失败已降级，`nickname` 是手机号兜底、`avatar` / `gender` / `birthday` 是**「拿不到」而非「空」**，四项**一律不可信**。⚠ **`false` 时前端不得渲染资料表单**——`PUT /profile` 是**整份替换**（见上「资料写口径」），拿降级值提交会把真实资料**静默清空**还回「已保存」；此时资料页只渲染降级块 + 「重试」 |
| 换绑口径 | `POST /auth/phone` **双验证**（旧号码验证码 + 新号码验证码）；成功后**服务器登录态快照即时更新**，⚠ **不重签 token**（前端无需换 token、也不重新登录） |
| 登出 | 删除 Redis 快照即服务端下线；本地 token 由前端清除 |
| 静默降级的例外 | ⚠ **凡「拿不到只是增强、拿不到也照常出页面」的读，都不走 `BffFeignCall`**，而是在各自 service 里 catch + `log.warn` 后降级（如 `/auth/me` 的顾客资料读 `CustomerProfileBffService#loadProfile`、商品分页/facets 用的分类树 `CatalogBffService#categoryTreeOrEmpty`）。**此清单不在此穷举**——新增这类降级时在**该类自己的注释里**写明「为什么这条读可以静默降级」，不要把清单抄到这里（穷举数字就是下一个会烂掉的东西）。判定标准：拿不到它，页面是「少一块增强」还是「主内容没了」——后者必须走 `BffFeignCall` 抛出去 |
| 库存展示口径 | SKU 出参带 `availableStock`（可用库存 = `stock`，域侧同义；`locked_stock` 已于 2026-09-21 废弃、不参与口径），0 即售罄。⚠ **`warn_stock` 不进 C 端**；⚠ **库存不参与详情可见性**，售罄商品照常可打开、不 404；⚠ 该字段由 `toMallSku` **逐字段手工映射**，与 `description` 消毒同属出口处理 |
| 购物车读口径 | `GET /cart` 一次编排 = trade-center 取行 + **store 批量详情一次调用**（`POST /goods/spu/batch`）+ 可见性判定 + 按店铺分组汇总。⚠ **逐行调详情是 N+1，禁止**（加购物车行的第一步就是别把行数变成请求数） |
| 购物车不可买口径 | 行的 `invalid` = **C 端商品可见性不变量**（[cross-cutting.md](./cross-cutting.md) 第 20 条）的**第三个落点**：店铺未审核 / SPU 已下架 / SPU 被锁定 / SPU 已删 → `invalid=true` 且 `purchasable=false`。⚠ **不从列表里删掉**（顾客要看得见才敢删它），但它**不计入** `totalQuantity` / `selectedQuantity` / `selectedAmount`（金额只算真能下单的行）。⚠ 页面上的「清除失效商品」**没有专用端点**：把 `invalid` 行的 id 收集起来走 `POST /cart/items/remove`——「判定归服务端（下发的 `invalid`）、删除复用批量端点」是有意选择，别为它加第九个接口 |
| `invalid` 与 `purchasable` 分工 | 两者**独立**：`invalid` = **商品本身**不可买（不可见）；`purchasable` = 商品可见但**这一行不能再加**（`invalid` 或 `quantity >= availableStock`）→ 前端禁「+」。⚠ 别把两者合成一个字段：合并后「已下架」与「已到库存上限」在页面上同形，提示语没法写对 |
| 加购不校验库存 | `POST /cart/items` **只校验「该商品对 C 端可见」**（不可见 → `400`「该商品已下架或不可购买」，不落行），**不校验库存、不锁库存**：购物车是购买意向不是占位，库存只影响 `purchasable` 的展示。⚠ **还多校一条**：`skuId` 必须属于该 `spuId` 且在售（⚠ SPU 上架 ≠ 名下每个 SKU 都在售，域内不变量只保证「至少一个在售」），否则 `400`「该规格已下架，请重新选择」——这道校验只能在 BFF 做，域不持商品信息 |
| 购物车上限 | 单行数量 999 / 单购物车 100 行，**都在域侧**。⚠ **两条路径的封顶方式不同，别按一条理解**：新增行与改数量走 DTO 校验（`@Max(999)`）→ 超限 `400` 原样透传，单车满 100 行再加 → `400`「购物车最多 100 种商品，请先清理」；**重复加购的累加路径**在 SQL 里 `LEAST(quantity + delta, 999)` 原地封顶、**不报错**（连点加购最多停在 999）。⚠ 100 行上限是**软上限**（并发两笔可双双通过检查而略微越界），与收货地址 20 条上限同口径 |
| 购物车写口径 | 改数量 / 改选中 / 删除 / 清空**一律经 `BffFeignCall.call` 直透 trade-center**（写路径不在 BFF 二次判定），失败降级文案「购物车暂不可用，请稍后重试」 |
| | ⚠ **单行与批量对「命中 0 行」的语义刻意不同，不要拉平**：`PUT /cart/items/{id}` 与 `PUT /cart/items/{id}/selected` 命中 0 行（行不存在 / 不属于本人）→ **`404`「购物车行不存在」**——「改成功」与「没这行」对调用方是两件事，回 200 就是**假成功**；`POST /cart/items/remove` 与 `DELETE /cart` 是**幂等 no-op**（批量语义下不该因某一行被并发删掉而让整批失败）。故前端**删除路径**不必处理「双击删除」的竞态，**改数量 / 改选中要按 `404` 处理**（重拉列表即可，那一行确实已经不在车里） |
| 徽标口径 | `GET /cart/count` = **购物车行数**（轻口径：域侧 `count(*)` + Redis 读穿透，**不做可见性判定**），供顶栏徽标。⚠ 它与购物车页的 `totalQuantity`（**有效行的件数之和**）**口径不同**，不是 bug：徽标数「车里有几项」，页脚算「能买几件、多少钱」 |
| 下单入参 | `MallOrderCreateDTO` = `source`（`DIRECT` / `CART`）+ `requestId`（客户端生成）+ `addressId`（**必填**）+ `items[{skuId, quantity}]` + `cartItemIds`（仅 `CART` 结算带，`DIRECT` 直购**不得**带——带了也不会清车，见下行「下单后的清车」）。⚠ **传 `addressId` 而不是地址快照**：地址由本层经 customer-center 取回（顺带校验归属）后组装成快照传给 trade-center —— **trade-center 结构上调不到 customer-center**（每个域只依赖自己的 `<域>-interface`） |
| 订单地址改口径 | `PUT /orders/{orderNo}/address` **只改这一笔订单的收货地址快照**（不动顾客地址簿、也不影响别的订单），且**仅待支付可改**——闸门在**域内**，本层不重判、原样透传 `400`「订单当前状态「已支付」不允许修改收货地址」。⚠ 入参是 `addressId`：本层用它与下单**同一个** `addressSnapshot` 取地址 + 校验归属（不属本人 → customer-center 404「地址不存在」，不区分「不存在」与「不属于本人」），再组快照传域——故「拿别人的 addressId 改我的单」自然被挡住、且不透出存在性。出参 `Void`，页面改完重拉详情 |
| 地址状态读 | `GET /addresses/status` 只回**控制流用的派生态**（`hasAddress` + `defaultAddressId`），**不回地址列表本身**：列表是给人看的数据、缓存它会引入陈旧展示；派生态陈旧有兜底（下行）。⚠ 空结果是**合法状态不是 miss**（`hasAddress=false` 照常缓存），否则新顾客每次下单都要穿透打一次下游 |
| 地址状态缓存 | 键 `{prefix}:{customerId}`（`panoramic.mall.address-status-redis-prefix`，默认 `panoramic:mall:addr-status`），TTL = `panoramic.mall.address-status-ttl-seconds`（默认 **1800**）。**四个写路径成功后主动失效**（新增 / 编辑 / 删除 / 设默认）——地址簿一变，「有没有地址 / 默认是哪条」就变了。⚠ **TTL 是「失效漏掉」的最终兜底而不是主手段**：陈旧的 `defaultAddressId` 会**直接导致下单 / 改地址失败**（域侧 404「地址不存在」），故页面必须按那条 404 回退到重选 |
| 缓存的三条硬口径 | ⚠ ① **读失败不得写成缓存**：customer-center 不可用时的「读不到」**不是「没有地址」**，写进去就把一次故障固化成整个 TTL 的错结论（同 memory `bff-feign-cb-counts-4xx` 的教训：故障期写进去的错值会长期存活）——故回填只在**域读成功之后**；② **Redis 不可用不得让主流程失败**：命中查询 / 失效 / 回填三处各自 catch + `log.warn`，「读不到缓存」一律**当 miss** 落回下游；③ **失效失败不报错**：地址写已经成功，删键只是让派生态早一点刷新，TTL 兜底 |
| 下单后的清车 | 下单**成功后**本层才清车，且**仅当 `source=CART`**（`cartItemIds` 非空时调 `POST /cart/items/remove`）——**两个条件都不能少、顺序也不能反**。⚠ **`DIRECT` 直购即便带了 `cartItemIds` 也不清**：那些行从未被下单，删掉是**静默丢顾客数据**（域内物理删除、不可逆）。⚠ 客户端须**只传本次结算的行 id**（CART 结算 3 行里的 1 行，就只传那 1 行的 id）——本层不做「`cartItemIds ⊆ items`」的交叉校验（要额外查一次购物车才做得到，代价不成比例），传多了删的是顾客自己的行、可重新加回。⚠ **清车失败只 `log.warn`、不让下单整体失败**（这次调用**没有页面出口**，故不套「购物车暂不可用」那类降级文案）：订单已建是**不可逆的主结果**，清车是**可重放的补偿**（顾客手动删、或再提交一次都行；指纹窗口内重复提交同一批商品会**复用原单**，不会重复下单；原单已结束（已收货 / 已取消 / 已退款）时除外——结束的单不参与复用，那种情况下会真下出新的一笔）。反过来「先清车再下单」会让顾客的车空了什么也没买到——两个方向的错里，能自愈的那个才是该选的那个 |
| 下单出参 | 一次提交会按 `storeId` **拆成多笔**（一单一店），故出参是 `List<MallOrderVO>`，顺序 = `storeId` 升序（确定）。页面按「一笔一单」展示与支付 |
| 重复提交 | `requestId` **必填**（客户端生成，**这是页面的义务、域内刻意不设校验**）；命中即**原样返回首次那批**——不重建、不二次扣库存、连商品都不再校验。⚠ **生成规格**：非空、**≤64 字符**（域列 `VARCHAR(64)`）、同一顾客名下唯一即可——随机 UUID 足够。⚠ **键的作用域是 `(customer_id, request_id)`**，故它只需**唯一性、不需保密性**，不是安全令牌（别人的 `requestId` 在本人的 `customer_id` 下不匹配）。⚠ **同一次提交重试应沿用同一个 `requestId`**：沿用走 L1 原样返回首批；换新的只剩 L2 指纹窗口兜底。⚠ **L2 是有限窗口、别当无时限保证**：指纹 = `sha256(customerId\|source\|storeId\|排序后的 skuId:qty)`，**不含 `requestId`、不含地址**；窗口 = `panoramic.trade.order.idempotency-window-seconds`（当前 **300 秒**）。故换新 id 重试时：**超过 300 秒、或 `source` / `storeId` / 任一 `skuId:qty` 变了，都会真下出第二单**（重试时别把 `DIRECT` 改成 `CART`）。⚠ 另有一维**不属于「窗口」而属于「那一笔是否已经结束」**：同指纹的最近一笔若已**收货 / 取消 / 退款**（`OrderStatus#isEnded`），它**不参与复用** → 同样真下出新的一笔（这是刻意的：结束的单既没重新扣库存、也付不了款，拿它顶掉新单会让顾客「下单成功」却拿到一笔死单）。两级幂等（请求级 + 指纹窗口）口径见 [trade-center.md](./trade-center.md) 与 [`backend/trade-center/README.md`](../../backend/trade-center/README.md) 第 7 节 |
| 假支付 | `MallOrderPayDTO.amount` 必须**等于订单总额**才算支付成功。⚠ **校验落在域内**（金额是领域规则，本层只透传），不一致回 `400`「支付金额与订单总额不一致（应付 X 元，实付 Y 元）」 |
| 订单状态文案 | 状态名与文案**由域下发**（`status` = 枚举名，`statusMallLabel` = 顾客可读文案）。⚠ **本层不重写文案**——两端各写一份必漂移；商户端 / 管理端读的是域 VO 上的另一个字段 `statusStoreAdminLabel`（**不在 `MallOrderVO` 上、C 端不下发**），如 `PAID` 在 C 端叫「已支付」、商户端叫「待发货」。⚠ 别写成 `mallLabel` / `storeAdminLabel`——那是枚举 `OrderStatus` **内部**的字段名，域 VO 上带 `status` 前缀 |
| 全选作用域 | `PUT /cart/selected` 是**域侧整表操作**（把该顾客**所有**行的 `selected` 置为传入值，含 `invalid` 行）；页面上的「全选」勾选态按**有效行**推导，汇总只算「有效且选中」。⚠ 不要在 BFF 侧重写成「逐行改选中」——那是 N 次请求 |

## 三、前端契约的**视觉与结构**基准

mall 前台的**页面契约**（长什么样、分哪几块、哪些不做、登录门禁落在哪个文件）由 `frontend/mall` 工程
固定（风格源头是该工程自身，见 [frontend/mall/README.md](../../frontend/mall/README.md) 与其 `src/styles/tokens.css`），**不是本文档**；本文件只登记服务端接口与形状。

