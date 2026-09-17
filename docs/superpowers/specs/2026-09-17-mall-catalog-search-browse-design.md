# mall 前台 · 商品搜索与分类浏览（设计）

> 日期：2026-09-17
> 范围：mall 前台首页「万能搜索」与「全分类展示」两区块接真实数据，新增搜索页与分类商品页。
> 热门商品区**本次不动**（仍为 `src/mock/goods.ts`）。

## 一、目标

1. 首页搜索框真正可用：回车 / 点热搜词 → 跳搜索结果页。
2. 首页分类宫格接真实分类库（含图标），点击 → 跳分类商品页。
3. 新增两个页面：`/search`（搜索结果）、`/category/:categoryId`（分类商品），共用一套列表骨架。
4. 列表页带**筛选器**（分类 / 品牌），内容随查询条件动态计算，只展示有结果的可选项。
5. 支持分页（每页 49）与价格排序。

## 二、非目标

- **不做**商品详情页、购物车、下单（trade 域尚不存在）。
- **不改**首页热门商品区（`GoodsGrid` + `src/mock/goods.ts` 原样保留）。
- **不动** store 域 owner 侧分页的签名。owner 侧的 `storeId` 是**签名强制**的数据权限锚点，改成可选参数等于把「只看自己店」降级为「看全量」——属于「合并过于复杂且危险」，不做。
- **不引入**搜索引擎 / 中间件（ES 等）。`LIKE` 足够当前量级。
- **不做**分类树缓存（每次请求实调 goods-center，树很小）。
- 热门搜索词**保持静态 mock**（本次忽略）。

## 三、已定决策摘要

| 项 | 决定 |
|---|---|
| 商品数据源 | `store_goods_spu`，固定口径：上架 + 未锁定 + 所属店铺已审核通过 |
| 页面形态 | 新增 `/search` 与 `/category/:categoryId`，共用列表骨架 |
| 列表骨架 | 单列流式：搜索框 → 分类筛选行 → 品牌筛选行 → 排序条 → 网格 → 分页 |
| 栅格 | **固定 1280 容器 + 固定 7 列**（不写媒体查询，维持「只做宽屏」约定） |
| 单页条数 | **49**（= 7 列 × 7 行） |
| 商品卡 | 主图 + 名称（2 行）+ 「¥xx.xx 起」+ 店铺名；**去掉原价 / 销量 / 角标**（库里无来源） |
| 无图回退 | 主图空 / 加载失败 → CSS 渐变占位 + 商品名首字（色相按 id 取模） |
| 分类层级 | 首页宫格展示**顶级分类**；分类页展示该分类**子树**下全部在售商品 |
| 分类筛选器 | 取**顶级**分类，**可多选** |
| 品牌筛选器 | **可多选** |
| 分类页筛选器 | 也带，分类行展示**当前分类的子分类**（只列有货的）+「全部」 |
| 分类图标 | `goods_category` 加 `icon` 列存图片 URL，admin 录入；放宽「不引外链图床」 |
| 价格排序 | `store_goods_spu` 加 `min_price` 冗余列，与 `shelf_status` 同处联动维护 |
| 域接口策略 | **能通用就通用，BFF 设限定条件**；只有聚合这类新能力才新增接口 |
| 测试数据 | 批量上架 `store_id=5` 的商品 + 新灌一家店 |

## 四、链路与分层

```
mall 前台（/search、/category/:categoryId）
   │  网关 /mall/**（StripPrefix=1，白名单放行）
mall-bff（新增 catalog 编排层）
   ├─ Feign → goods-center    GET  /internal/goods/categories/tree
   └─ Feign → store           POST /internal/store/goods/cross-shop/spu/page   （通用化改造）
                              POST /internal/store/goods/facets               （新增）
```

- **公开浏览**：搜索页 / 分类页游客可看。`/mall/catalog/**` 必须**两处**登记免鉴权：
  - 网关 `backend/gateway/src/main/resources/application.yml` 的 `panoramic.auth.whitelist-paths`（带 `/mall` 前缀）
  - mall-bff 的 `panoramic.auth.whitelist-paths`（不带前缀）
  - ⚠ 漏一处 → 要么页面 401，要么接口裸露到公网。
- **mall-bff 首次启用 Feign**：`@EnableFeignClients(basePackages = {"com.panoramic.common.store", "com.panoramic.common.goods"})`。熔断共享配置 `nacos-config/feign-circuitbreaker.yml` 已在 import 列表内，且已配 `ignore-exceptions: [ServiceException]`，**不改**。
- 域调用的降级统一走 `common` 的 `BffFeignCall.call(下游名, 降级文案, action)`；文案如「商品暂不可用，请稍后重试」。400/403/404 原样透传。

## 五、域侧改造（store）

### 5.1 跨店分页通用化（改造既有接口，**不新增**）

`platformPageStoreGoods` 这条本质是「跨店、无数据权限锚点」的通用分页，admin 与 mall-bff 共用。改名去 `Platform`：

| 旧 | 新 |
|---|---|
| `StoreGoodsSpuPlatformPageQueryDTO` | `StoreGoodsSpuCrossShopPageQueryDTO` |
| `StoreGoodsSpuPlatformPageItemVO` | `StoreGoodsSpuCrossShopPageItemVO` |
| Feign `platformPageStoreGoods` | `pageStoreGoodsCrossShop` |
| 域 service `platformPage` | `crossShopPage` |
| 域路径 `/goods/platform/spu/page` | `/goods/cross-shop/spu/page` |

**入参新增（全部可选，不传 = 原行为，admin 零破坏）**

| 字段 | 说明 |
|---|---|
| `shopStatus`（Integer） | 按**所属店铺**审核状态过滤。C 端固定传 `2`（已审核通过）；admin 不传 = 不过滤 |
| `sort`（String） | `default`（= id 倒序，原行为）/ `priceAsc` / `priceDesc`，按 `min_price` 排 |
| `brandIds`（List&lt;Long&gt;） | **由 `brandId` 单值改为多值**。admin 前端品牌下拉同步从单选改多选（顺带增强） |

> `categoryIds` / `keyword` / `storeId` / `shelfStatus` / `lockStatus` 维持现状。

**出参新增**：`minPrice`（在售 SKU 最低价）。

**`shopStatus` 的落地方式**：经 `StoreShopService` 新增的 `idListByStatus(status)` 取店铺 id 集合 → `IN` 过滤。**不 join、不直接持 Mapper**，守住「跨实体只走 owner service」。当前库仅 2 家店，`IN` 规模可忽略。

**排序的 NULL 处理**：`priceAsc/priceDesc` 直接用 `orderByAsc/Desc(min_price)`，不额外处理 NULL 位置。理由——C 端固定 `shelf_status=1`，而上架 SPU 必有上架 SKU（不变量），故 `min_price` 必然非 null；admin 侧存在下架商品时排序位置不做保证。

### 5.2 新增筛选聚合接口（facets）

域内此前没有 `GROUP BY` 聚合能力，不属于「加几个筛选条件」，属新增。

`POST /internal/store/goods/facets`

- 入参 `StoreGoodsSpuFacetQueryDTO`：

| 字段 | 说明 |
|---|---|
| `keyword` | 关键字（模糊匹配商品名） |
| `scopeCategoryIds` | **范围锚点**（已展开的子树 id）。分类页传路由分类的子树；搜索页为空 |
| `filterCategoryIds` | **已选分类筛选**（已展开的子树 id） |
| `filterBrandIds` | 已选品牌筛选 |
| `shopStatus` / `shelfStatus` / `lockStatus` | 与分页同口径的可选过滤 |

- 出参 `StoreGoodsSpuFacetVO { List<FacetItemVO> categories; List<FacetItemVO> brands; }`，`FacetItemVO { Long id; String name; Integer count; }`

**⚠ 核心口径——每个维度算 faceting 时排除自己那一维**：

| 维度 | 计算条件 |
|---|---|
| `categories` | `keyword` + `scopeCategoryIds` + `shopStatus` + `shelfStatus` + `lockStatus` + **`filterBrandIds`** |
| `brands` | `keyword` + `scopeCategoryIds` + `shopStatus` + `shelfStatus` + `lockStatus` + **`filterCategoryIds`** |

若「分类维度」把 `filterCategoryIds` 也算进去，用户点掉一个分类后其余分类会全部消失——这是本设计最容易做错的地方。

**实现**：两条 `GROUP BY`，走 MP Wrapper（**不写 XML SQL**）：
```
.select(getCategoryId, getCategoryName, "COUNT(*) AS cnt").groupBy(getCategoryId, getCategoryName)
```
`name` 取库中**快照**（同一 id 快照名一致，故并入 GROUP BY 不影响结果）。返回 `listMaps` 后映射为 `FacetItemVO`。

### 5.3 `min_price` 推导列

`store_goods_spu` 加 `min_price DECIMAL(10,2) NULL`，语义：**在售（上架且未删）SKU 的最低价**；无上架 SKU 时为 `null`。

- **写入口收敛**：新增私有 `refreshMinPrice(spu)`，与既有 `refreshShelfStatus(spu)` 并列；新增私有 `refreshDerived(spu)` 统一调用二者，**所有既有调用点改调 `refreshDerived`**。这样 `refreshShelfStatus` 仍是不变量「SPU上架 ⟺ ≥1 SKU 上架」的唯一写者，`refreshMinPrice` 是新不变量「`min_price` = 上架 SKU 最低价」的唯一写者。
- 覆盖面：上架/下架 SKU、整单替换 SKU、锁定（级联下架）、解锁——全部经过 `refreshDerived`。
- ⚠ **必须避开的坑**：`refreshShelfStatus` 现有「状态未变则跳过 update」的早退。`refreshMinPrice` 必须**独立比较** `min_price` 是否变化，不能因 `shelf_status` 没变就跳过写入。
- SKU 侧能力：`StoreGoodsSkuService` 新增 `minPriceBySpuId(spuId)`。
- 存量回填 SQL 一次性执行。

### 5.4 分类图标列

`goods_category` 加 `icon VARCHAR(255) NULL`（图片 URL）。

- `CategorySaveDTO` / `CategoryUpdateDTO` / `CategoryTreeVO` 加 `icon`。
- admin 分类管理表单加 icon 文本输入（录 http(s) 地址，不做上传）。
- 种子：给 9 个顶级分类回填。

## 六、mall-bff（新增 catalog 编排层）

### 6.1 页面级接口（3 条，全部 `RespData` 包裹，**无 `@PreAuthorize`**——C 端不接 RBAC）

| 方法 | 路径 | 入参 | 出参 |
|---|---|---|---|
| GET | `/catalog/categories` | — | `List<CategoryNodeVO>`（全量树，含 `icon`） |
| POST | `/catalog/goods` | `MallGoodsPageQueryDTO` | `PageResult<MallGoodsItemVO>` |
| POST | `/catalog/facets` | `MallFacetQueryDTO` | `MallFacetVO` |

- 分页与 facets 用 **POST + `@RequestBody`**：入参含集合（`categoryIds`/`brandIds`）。⚠ **页面级用 POST 做查询在本仓库此前无先例**（admin / store 的分页都是 GET），此处破例的理由是集合入参——axios 默认把数组序列化成 `categoryIds[]=1`，而 Spring 的 `@RequestParam List<Long>` 收不了这个形状，改 `paramsSerializer` 又会牵动请求层。契约表按 POST 登记。
- `/catalog/categories` 返回**全量树**，前端本地缓存后同时用于：首页宫格、分类页标题、分类 chips 名字解析。故不需要单独的「分类详情」接口。

**`MallGoodsPageQueryDTO`**：`keyword`、`categoryId`（分类页路由锚点，可空）、`categoryIds`（已选分类多选）、`brandIds`（已选品牌多选）、`sort`、`pageNum`、`pageSize`。

**`MallGoodsItemVO`（BFF 自己的形状，不由域 VO 决定）**：
`id` / `name` / `mainImage` / `minPrice` / `storeId` / `storeName` / `categoryId` / `categoryName` / `brandId` / `brandName`

**`MallFacetVO`**：`{ categories: MallFacetItemVO[], brands: MallFacetItemVO[] }`，`MallFacetItemVO { id, name, count }`

### 6.2 编排逻辑（`CatalogBffService`）

1. **分类子树展开**：前端只传单个 / 单个选中的分类 id，BFF 用分类树递归收集「该节点 + 全部后代」→ 传 `categoryIds` 给域（域只做 `IN`）。与 admin 现有做法一致。
2. **C 端固定口径**：调域分页 / facets 时**固定传** `shopStatus=2`、`shelfStatus=1`、`lockStatus=0`。
   ⚠ 这三个条件是 C 端正确性的全部依赖，域侧不再有隐含约束——**必须写进契约**（见第八节）。
3. **字段裁剪**：域返回的跨店 VO 含 `lockReason` / `lockUser` / `goodsSpuId` / `categoryPath` / `skuCount` / `updateTime`，其中 `lockUser` 标注「仅管理端展示」。BFF **重新组装**成 `MallGoodsItemVO` 输出，不透传。
4. **分类 facet 的两种投影**（域侧一律按 `category_id` 分组返回，差异全在 BFF）：

   | 页面 | `scopeCategoryIds` | BFF 对 categories facet 的处理 |
   |---|---|---|
   | 搜索页 | 空 | **上溯到顶级祖先并合并 count** → 输出顶级分类 chips |
   | 分类页 | 路由分类的子树 | **不做上溯**，输出 scope 内的分类（即当前分类的子分类）chips |

   名称优先取分类树里的权威名，树里查不到时回退域返回的快照名。
5. **分类页的筛选入参**：`scopeCategoryIds` = 路由分类的子树；已选中的子分类作为 `filterCategoryIds` 传入（未选则范围就是整个子树）。
6. 所有域调用经 `BffFeignCall` 包装。

## 七、mall 前端

### 7.1 新增文件

| 文件 | 职责 |
|---|---|
| `src/api/catalog.ts` | `fetchCategories()` / `fetchGoodsPage(body)` / `fetchFacets(body)` |
| `src/types/catalog.ts` | `CategoryNode` / `GoodsListItem` / `FacetItem` / 分页与查询参数 |
| `src/views/GoodsListView.vue` | 搜索页与分类页**共用**的列表页 |
| `src/components/FilterRow.vue` | 筛选行（多选 chips，带商品数） |
| `src/components/Pager.vue` | 分页条（EP 不注册，自己写） |

### 7.2 改动文件

| 文件 | 改动 |
|---|---|
| `src/components/SearchBar.vue` | 回车 / 点热搜词 → 跳 `/search?keyword=`；热搜词本身仍为静态 mock |
| `src/components/CategoryGrid.vue` | 调真实分类树，渲染 `icon` 图片，无图回退渐变；点击 → `/category/:id` |
| `src/components/GoodsCard.vue` | 改为紧凑版（7 列约 170px/格）：图片 + 名称 2 行 + 起价 + 店铺名；保留无图渐变回退 |
| `src/router/index.ts` | 新增 `/search`、`/category/:categoryId` |
| `src/mock/categories.ts` | **删除**（不再被引用） |
| `src/mock/goods.ts` | **保留**（热门商品区不动） |

### 7.3 列表页形态

自上而下：

1. 搜索框（分类页显示分类名 + 「全部分类」入口）
2. **分类筛选行**（多选 chips，带 count；搜索页 = 顶级分类 / 分类页 = 当前分类的子分类）
3. **品牌筛选行**（多选 chips，带 count）
4. 排序条：`共 N 件` + 综合 / 价格 ↑ / 价格 ↓
5. **7 列网格**
6. 分页（每页 49）

**筛选状态进 URL query**（`/search?keyword=&categoryIds=1,2&brandIds=3&sort=&page=1`），保证可分享、刷新不丢。

**空结果**：友好提示 + 返回首页入口。

**首页宫格列数**：`min(顶级分类数, 10)` 动态计算，保住「铺满一行」的视觉基准又不写死 10。

## 八、契约与文档同步（**同一改动内**）

| 文件 | 改什么 |
|---|---|
| `docs/contracts/store.md` | 分页路径改名与新增入参；新增 `/goods/facets`；「共 18 个接口（owner 10 + platform 8）」需重述；`platform 侧` 相关表述改为「跨店通用」 |
| `docs/contracts/mall-bff.md` | 新增 3 条页面接口；免鉴权路径加 `/catalog/**`；「内部依赖：一期没有」改为已接 goods-center 与 store |
| `docs/contracts/gateway.md` | 免鉴权表补 `/mall/catalog/**`（网关侧 + 服务本地侧两处） |
| `docs/contracts/cross-cutting.md` | 新增：① mall-bff 的 C 端商品口径（`shopStatus=2` + `shelfStatus=1` + `lockStatus=0` 由 BFF 强制传参，域侧无隐含约束）② facet「排除自身维度」口径 ③ 跨店分页通用化后的调用方 |
| `docs/contracts/admin.md` | 「店铺商品管理」的入参 `ShopGoodsPageQueryDTO.brandId` 改为 `brandIds`（多值），页面契约该行的入参类型与描述同步 |
| 根 `CLAUDE.md` | ① store 域分页的通用化与 owner 侧不动的原因 ② `min_price` 不变量 ③ mall 前端图片约定放宽为「**数据驱动**的图片（分类图标等）可由后端 URL 提供；前端源码内不写死外链、不引外链字体」 |
| `backend/store/README.md` | `min_price` 不变量、facets 口径、跨店分页调用方 |
| `backend/mall-bff` 说明 | Feign 已启用、catalog 编排职责 |

## 九、数据改动

⚠ 属「大改动」→ **开工前先对整个库做备份**（经 `mysql-connect` 导出，记下产物位置与时间点）。

1. 备份当前库
2. DDL：`store_goods_spu.min_price`、`goods_category.icon`
3. `min_price` 存量回填
4. 9 个顶级分类的 `icon` 种子
5. **批量上架** `store_id=5` 的 SPU：SKU 置上架后，用**同一条 SQL 表达式重算** SPU 的 `shelf_status` 与 `min_price`，保证两个不变量都成立
6. **新灌一家店**（`store_shop` id=7 + 若干商品，复制现有数据结构换店），让「跨店混排 + 店铺名」真的看得出作用
7. 确保上架商品带 `brand_id`，品牌筛选器才有内容可展示

## 十、验证（止步于编译通过）

- 后端：`mvn -pl <模块> -am compile`
- 前端：`cd frontend/mall && npm run build`（`vue-tsc --noEmit && vite build`，类型不过即失败）
- `node docs/contracts/drift-check.mjs` 退出码 0
- 文本级核对：免鉴权路径**两处**都登记；`brandId` 残留无遗漏
- 数据复核（SQL）：不变量 `shelf_status=1 ⟺ 存在上架 SKU`、`min_price = MIN(上架 SKU price)`
- ❌ 不启动服务、不跑 dev/preview、不打接口

## 十一、风险

| 风险 | 应对 |
|---|---|
| BFF 漏传三个固定条件 → 下架商品漏到公网 | 集中在 `CatalogBffService` 一处传参；写入 cross-cutting 契约 |
| facet 忘记「排除自身维度」→ 选完筛选器自我消失 | 两条查询分别在域内构造，口径写进 javadoc 与契约 |
| `min_price` 早退漏写（沿用 shelf_status 的跳过判断） | 独立比较、独立 update |
| 改名波及 admin 前后端 | 纯机械改动，编译 + 类型检查兜底 |
