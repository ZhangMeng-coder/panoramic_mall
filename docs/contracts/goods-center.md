<!-- contract-meta
service: goods-center
layer: internal
basePath: /internal/goods
feignClient: backend/goods-center-interface/src/main/java/com/panoramic/contract/goods/api/GoodsCenterClient.java
implScanDirs: backend/goods-center/src/main/java/com/panoramic/goods/controller
typeDirs: backend/goods-center-interface/src/main/java
respEnvelope: RespData
-->

# 标准商品域（goods-center）内部契约 · 第 ② 层

> 标准商品平台：分类 / 品牌 / 标准 SPU-SKU 模板。
> **不暴露公网路由**，只被 admin BFF 与 store-bff 经内部 Feign 调用。
> 域内**不做任何鉴权、不做权限判断**（见 [cross-cutting.md](./cross-cutting.md) 第 6、7、14 条）。

**共 19 个接口**。调用方见每行「调用方」列。

## 一、前缀怎么拼上的

本域前缀 = **`/internal/goods`**；拼法（不是 context-path、由 Controller 类级 `@RequestMapping`
写死）见 [README.md](./README.md) 的「内部 Feign 的「路径」前缀怎么来的」。

## 二、接口清单

| Feign 方法 | 方法 | 路径 | 入参 | 出参 | 契约声明(接口模块) | 域实现 | 调用方 | 状态 |
|---|---|---|---|---|---|---|---|---|
| pageBrands | GET | /brands/page | BrandPageQueryDTO | RespData<PageResult<BrandVO>> | `GoodsCenterClient#pageBrands` | `BrandController#page` | GoodsTemplateBffService |  |
| listBrands | GET | /brands/list | — | RespData<List<BrandVO>> | `GoodsCenterClient#listBrands` | `BrandController#list` | GoodsTemplateBffService, ShopGoodsBffService |  |
| brandDetail | GET | /brands/{id} | Long | RespData<BrandVO> | `GoodsCenterClient#brandDetail` | `BrandController#detail` | GoodsTemplateBffService |  |
| saveBrand | POST | /brands | BrandSaveDTO | RespData<Long> | `GoodsCenterClient#saveBrand` | `BrandController#saveBrand` | GoodsTemplateBffService |  |
| updateBrand | PUT | /brands/{id} | Long, BrandUpdateDTO | RespData<Void> | `GoodsCenterClient#updateBrand` | `BrandController#updateBrand` | GoodsTemplateBffService |  |
| deleteBrand | DELETE | /brands/{id} | Long | RespData<Void> | `GoodsCenterClient#deleteBrand` | `BrandController#deleteBrand` | GoodsTemplateBffService |  |
| saveCategory | POST | /categories | CategorySaveDTO | RespData<Long> | `GoodsCenterClient#saveCategory` | `CategoryController#saveCategory` | GoodsTemplateBffService |  |
| categoryTree | GET | /categories/tree | — | RespData<List<CategoryTreeVO>> | `GoodsCenterClient#categoryTree` | `CategoryController#tree` | GoodsTemplateBffService, ShopGoodsBffService, StoreGoodsBffService, CatalogBffService(mall-bff) |  |
| categoryPaths | POST | /categories/paths | List<Long> | RespData<Map<Long, String>> | `GoodsCenterClient#categoryPaths` | `CategoryController#paths` | ShopGoodsBffService, StoreGoodsBffService |  |
| updateCategory | PUT | /categories/{id} | Long, CategoryUpdateDTO | RespData<Void> | `GoodsCenterClient#updateCategory` | `CategoryController#updateCategory` | GoodsTemplateBffService |  |
| deleteCategory | DELETE | /categories/{id} | Long | RespData<Void> | `GoodsCenterClient#deleteCategory` | `CategoryController#deleteCategory` | GoodsTemplateBffService |  |
| pageSpu | GET | /spu/page | SpuPageQueryDTO | RespData<PageResult<SpuPageItemVO>> | `GoodsCenterClient#pageSpu` | `SpuController#page` | GoodsTemplateBffService |  |
| spuDetail | GET | /spu/{id} | Long | RespData<SpuDetailVO> | `GoodsCenterClient#spuDetail` | `SpuController#detail` | GoodsTemplateBffService, StoreGoodsBffService |  |
| spuDetailBySkuCode | GET | /spu/by-sku-code | String | RespData<SpuBySkuCodeVO> | `GoodsCenterClient#spuDetailBySkuCode` | `SpuController#findBySkuCode` | StoreGoodsBffService |  |
| saveSpu | POST | /spu | SpuSaveDTO | RespData<Long> | `GoodsCenterClient#saveSpu` | `SpuController#saveSpu` | GoodsTemplateBffService |  |
| updateSpu | PUT | /spu/{id} | Long, SpuUpdateDTO | RespData<Void> | `GoodsCenterClient#updateSpu` | `SpuController#updateSpu` | GoodsTemplateBffService |  |
| replaceSpuSkus | PUT | /spu/{id}/skus | Long, SpuSkuReplaceDTO | RespData<Void> | `GoodsCenterClient#replaceSpuSkus` | `SpuController#replaceSkus` | GoodsTemplateBffService |  |
| updateSpuStatus | PUT | /spu/{id}/status | Long, SpuStatusDTO | RespData<Void> | `GoodsCenterClient#updateSpuStatus` | `SpuController#updateStatus` | GoodsTemplateBffService |  |
| deleteSpu | DELETE | /spu/{id} | Long | RespData<Void> | `GoodsCenterClient#deleteSpu` | `SpuController#deleteSpu` | GoodsTemplateBffService |  |

> 「入参」列里，`Long` 单独出现通常是 `@PathVariable` 的 id。
> `pageBrands` / `pageSpu` 的查询对象是 `@SpringQueryMap`，`categoryPaths` 是 `@RequestBody List<Long>`。

## 三、形状规则

出参一律包 `RespData<T>`（无返回值用 `RespData<Void>`）：业务结果（含业务失败 `code=400/403/404`）一律 **HTTP 200 + `{code,msg,data}`**，只有兜底异常才是 **HTTP 500**。见 [cross-cutting.md](./cross-cutting.md) 第 2、6、13 条（域内不鉴权 / 熔断只认非 2xx 与连接失败）。

## 四、类型所在包（全部在 `goods-center-interface`，两端引用同一份）

包根：`backend/goods-center-interface/src/main/java/com/panoramic/contract/goods/`

| 包 | 类型 |
|---|---|
| `dto` | BrandPageQueryDTO, BrandSaveDTO, BrandUpdateDTO, CategorySaveDTO, CategoryUpdateDTO, SkuDTO, SpecAttr, SpecConfigItem, SpuPageQueryDTO, SpuSaveDTO, SpuSkuReplaceDTO, SpuStatusDTO, SpuUpdateDTO |
| `vo` | BrandVO, CategoryTreeVO, PageResult, SkuVO, SpuBySkuCodeVO, SpuDetailVO, SpuPageItemVO |

> ⚠ 本包的 `PageResult`、`SpecAttr` / `SpecConfigItem` 在 `contract.store` 下**各有一份同形同名的孪生类**，别引错
> （见 [cross-cutting.md](./cross-cutting.md) 第 3 条）。

## 五、Feign 客户端配套类

| 类 | 职责 | 位置 |
|---|---|---|
| `GoodsFeignConfiguration` | 透传身份头 `X-User-Id` / `X-User-Type`（⚠ 缺 `X-User-Type` 时**回退 `admin`**） | `goods-center-interface/.../contract/goods/api/GoodsFeignConfiguration.java:30-56` |
| ⚠ **刻意没有 `ErrorDecoder`** | 业务失败一律 HTTP 200 + `code`，没有非 2xx 的错误体可解码；挂了它等于把业务错误重新变成熔断失败信号（cross-cutting 第 2 / 13 条） | — |
| `DomainResp` | 调用方解包：`code=200` 取 `data`，否则抛 `ServiceException(code,msg)` | `common/.../feign/DomainResp.java` |
| `BffFeignCall` | 端 BFF 统一「解包 + 剥 cause 链 + 降级文案」包装（域间调用**不用**，cross-cutting 第 24 条） | `common/.../feign/BffFeignCall.java` |

## 六、业务规则去哪看

本页只登记**契约**。分类树结构、SPU/SKU 的约束、`spec_config` 语义等**业务规则**见
[`backend/goods-center/README.md`](../../backend/goods-center/README.md)（服务说明）。
