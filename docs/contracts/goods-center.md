<!-- contract-meta
service: goods-center
layer: internal
basePath: /internal/goods
feignClient: backend/goods-center-interface/src/main/java/com/panoramic/contract/goods/api/GoodsCenterClient.java
implScanDirs: backend/goods-center/src/main/java/com/panoramic/goods/controller
typeDirs: backend/goods-center-interface/src/main/java
-->

# 标准商品域（goods-center）内部契约 · 第 ② 层

> 标准商品平台：分类 / 品牌 / 标准 SPU-SKU 模板。
> **不暴露公网路由**，只被 admin BFF 与 store-bff 经内部 Feign 调用。
> 域内**不做任何鉴权、不做权限判断**（见 [cross-cutting.md](./cross-cutting.md) 第 6、7、14 条）。

**共 19 个接口**。调用方见每行「调用方」列。

## 一、前缀怎么拼上的（⚠ 容易踩）

`@FeignClient(path = "/internal/goods")` 这段前缀**不是** `server.servlet.context-path` ——
goods-center 的 `application.yml` 里**没有** context-path（只有 `server.port: 8081`）。

前缀是在 **Controller 类级 `@RequestMapping` 里写死的字面量**：

| Controller | 类级 `@RequestMapping` | 行号 |
|---|---|---|
| `BrandController` | `/internal/goods/brands` | :31 |
| `CategoryController` | `/internal/goods/categories` | :30 |
| `SpuController` | `/internal/goods/spu` | :34 |

即 `@FeignClient(path)` + Feign 方法路径 与 类级映射 + 方法级映射 **逐段相等**。
所以下表「路径」列写的是**去掉 `/internal/goods` 前缀后的部分**（与 Feign 注解一致），
改前缀时要**同时**改 Feign 客户端的 `path` 与三个 Controller 的类级映射。

## 二、接口清单

| Feign 方法 | 方法 | 路径 | 入参 | 出参 | 契约声明(接口模块) | 域实现 | 调用方 | 状态 |
|---|---|---|---|---|---|---|---|---|
| pageBrands | GET | /brands/page | BrandPageQueryDTO | PageResult<BrandVO> | GoodsCenterClient.java:48 | BrandController.java:40 | GoodsTemplateBffService |  |
| listBrands | GET | /brands/list | — | List<BrandVO> | GoodsCenterClient.java:51 | BrandController.java:48 | GoodsTemplateBffService, ShopGoodsBffService |  |
| brandDetail | GET | /brands/{id} | Long | BrandVO | GoodsCenterClient.java:54 | BrandController.java:56 | GoodsTemplateBffService |  |
| saveBrand | POST | /brands | BrandSaveDTO | Long | GoodsCenterClient.java:57 | BrandController.java:64 | GoodsTemplateBffService |  |
| updateBrand | PUT | /brands/{id} | Long, BrandUpdateDTO | void | GoodsCenterClient.java:60 | BrandController.java:72 | GoodsTemplateBffService |  |
| deleteBrand | DELETE | /brands/{id} | Long | void | GoodsCenterClient.java:63 | BrandController.java:81 | GoodsTemplateBffService |  |
| saveCategory | POST | /categories | CategorySaveDTO | Long | GoodsCenterClient.java:67 | CategoryController.java:39 | GoodsTemplateBffService |  |
| categoryTree | GET | /categories/tree | — | List<CategoryTreeVO> | GoodsCenterClient.java:70 | CategoryController.java:47 | GoodsTemplateBffService, ShopGoodsBffService, StoreGoodsBffService |  |
| categoryPaths | POST | /categories/paths | List<Long> | Map<Long, String> | GoodsCenterClient.java:79 | CategoryController.java:56 | ShopGoodsBffService, StoreGoodsBffService |  |
| updateCategory | PUT | /categories/{id} | Long, CategoryUpdateDTO | void | GoodsCenterClient.java:82 | CategoryController.java:64 | GoodsTemplateBffService |  |
| deleteCategory | DELETE | /categories/{id} | Long | void | GoodsCenterClient.java:85 | CategoryController.java:73 | GoodsTemplateBffService |  |
| pageSpu | GET | /spu/page | SpuPageQueryDTO | PageResult<SpuPageItemVO> | GoodsCenterClient.java:89 | SpuController.java:43 | GoodsTemplateBffService |  |
| spuDetail | GET | /spu/{id} | Long | SpuDetailVO | GoodsCenterClient.java:92 | SpuController.java:61 | GoodsTemplateBffService, StoreGoodsBffService |  |
| spuDetailBySkuCode | GET | /spu/by-sku-code | String | SpuBySkuCodeVO | GoodsCenterClient.java:100 | SpuController.java:53 | StoreGoodsBffService |  |
| saveSpu | POST | /spu | SpuSaveDTO | Long | GoodsCenterClient.java:103 | SpuController.java:69 | GoodsTemplateBffService |  |
| updateSpu | PUT | /spu/{id} | Long, SpuUpdateDTO | void | GoodsCenterClient.java:106 | SpuController.java:77 | GoodsTemplateBffService |  |
| replaceSpuSkus | PUT | /spu/{id}/skus | Long, SpuSkuReplaceDTO | void | GoodsCenterClient.java:109 | SpuController.java:86 | GoodsTemplateBffService |  |
| updateSpuStatus | PUT | /spu/{id}/status | Long, SpuStatusDTO | void | GoodsCenterClient.java:112 | SpuController.java:95 | GoodsTemplateBffService |  |
| deleteSpu | DELETE | /spu/{id} | Long | void | GoodsCenterClient.java:115 | SpuController.java:104 | GoodsTemplateBffService |  |

> 「入参」列里，`Long` 单独出现通常是 `@PathVariable` 的 id。
> `pageBrands` / `pageSpu` 的查询对象是 `@SpringQueryMap`，`categoryPaths` 是 `@RequestBody List<Long>`。

## 三、形状规则

- ✅ **不包 `RespData`**：出参一律是业务类型（`BrandVO` / `PageResult<...>` / `void`）。
- ✅ **无 `@PreAuthorize`**：域内不做鉴权。
- ✅ 错误返回真实 HTTP 状态 + `{code,msg}`，由 `common` 的 `InternalApiErrorDecoder` → `ServiceException` 还原。
  **4xx 不计熔断失败率、5xx 计入**（[cross-cutting.md](./cross-cutting.md) 第 13 条）。

## 四、类型所在包（全部在 `goods-center-interface`，两端引用同一份）

包根：`backend/goods-center-interface/src/main/java/com/panoramic/contract/goods/`

| 包 | 类型 |
|---|---|
| `dto` | BrandPageQueryDTO, BrandSaveDTO, BrandUpdateDTO, CategorySaveDTO, CategoryUpdateDTO, SkuDTO, SpecAttr, SpecConfigItem, SpuPageQueryDTO, SpuSaveDTO, SpuSkuReplaceDTO, SpuStatusDTO, SpuUpdateDTO |
| `vo` | BrandVO, CategoryTreeVO, PageResult, SkuVO, SpuBySkuCodeVO, SpuDetailVO, SpuPageItemVO |

⚠ `contract.goods.vo.PageResult` 与 `contract.store.vo.PageResult` 是**两个同名独立类型**（见 [cross-cutting.md](./cross-cutting.md) 第 3 条）；
本包 `dto` 里的 `SpecAttr` / `SpecConfigItem` 在 `contract.store.dto` 下另有**一份同形同名的孪生类**（2026-09-19 拆分时切成各域自持）。

## 五、Feign 客户端配套类

| 类 | 职责 | 位置 |
|---|---|---|
| `GoodsFeignConfiguration` | 透传身份头 `X-User-Id` / `X-User-Type`（⚠ 缺 `X-User-Type` 时**回退 `admin`**） | `goods-center-interface/.../contract/goods/api/GoodsFeignConfiguration.java:30-56` |
| `InternalApiErrorDecoder` | 非 2xx `{code,msg}` → `ServiceException`（按状态码分野 4xx/5xx） | `common/.../feign/InternalApiErrorDecoder.java` |
| `BffFeignCall` | BFF 侧统一「剥 cause 链 + 降级文案」包装 | `common/.../feign/BffFeignCall.java` |

## 六、业务规则去哪看

本页只登记**契约**。分类树结构、SPU/SKU 的约束、`spec_config` 语义等**业务规则**见
[`backend/goods-center/README.md`](../../backend/goods-center/README.md)（服务说明）。
