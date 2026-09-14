<!-- contract-meta
service: store-bff
layer: page
baseUrl: /store
scanDirs: backend/store-bff/src/main/java/com/panoramic/storebff/controller
typeDirs: backend/common/src/main/java, backend/store-bff/src/main/java
-->

# 店铺端 BFF（store-bff）对外契约 · 第 ① 层

> 店主端页面接口（8084），经网关 `/store/**` 对外（`StripPrefix=1` 后落到本服务的 `/auth/**`、`/shops/**`、`/goods/**`）。
> 签发 `type=store` 的登录令牌，经内部 Feign 编排 store 域与 goods-center。

**共 17 个接口**。

## 一、接口清单

| 方法 | 路径 | 权限串 | 入参 | 出参 | 声明位置 | 状态 |
|---|---|---|---|---|---|---|
| POST | /auth/register | — | RegisterDTO | RespData<LoginResultVO> | AuthController.java:33 |  |
| POST | /auth/login | — | LoginDTO | RespData<LoginResultVO> | AuthController.java:41 |  |
| POST | /auth/logout | — | — | RespData<Void> | AuthController.java:49 |  |
| GET | /auth/me | — | — | RespData<CurrentUserVO> | AuthController.java:61 |  |
| GET | /shops/mine | — | — | RespData<ShopVO> | ShopController.java:32 |  |
| POST | /shops/save | — | ShopSaveDTO | RespData<Void> | ShopController.java:40 |  |
| POST | /shops/submit | — | ShopSaveDTO | RespData<Void> | ShopController.java:49 |  |
| GET | /goods/spu/page | — | StoreGoodsSpuPageQueryDTO | RespData<PageResult<StoreGoodsSpuPageItemVO>> | GoodsController.java:52 |  |
| GET | /goods/spu/{id} | — | Long | RespData<StoreGoodsSpuDetailBffVO> | GoodsController.java:60 |  |
| POST | /goods/spu | — | StoreGoodsSpuSaveDTO | RespData<Long> | GoodsController.java:68 |  |
| PUT | /goods/spu/{id} | — | Long, StoreGoodsSpuUpdateDTO | RespData<Void> | GoodsController.java:75 |  |
| DELETE | /goods/spu/{id} | — | Long | RespData<Void> | GoodsController.java:85 |  |
| PUT | /goods/spu/{id}/skus | — | Long, StoreGoodsSkuReplaceDTO | RespData<Void> | GoodsController.java:94 |  |
| PUT | /goods/spu/{spuId}/skus/{skuId}/shelf | — | Long, Long, StoreGoodsSkuShelfDTO | RespData<Void> | GoodsController.java:104 |  |
| GET | /goods/categories/tree | — | — | RespData<List<CategoryTreeVO>> | GoodsController.java:115 |  |
| GET | /goods/brands | — | — | RespData<List<BrandVO>> | GoodsController.java:123 |  |
| GET | /goods/center/spu-by-sku-code | — | String | RespData<SpuBySkuCodeVO> | GoodsController.java:133 |  |

> 「路径」列不带网关前缀 `/store`。例：`/goods/spu/page` 对外完整路径是 `/store/goods/spu/page`。

## 二、形状规则

- ✅ **必包 `RespData`**（唯一例外见第三节的 403 门禁，也是 `RespData` 形状的）。
- ⚠ **全 17 个接口都没有 `@PreAuthorize`** —— 店主端**不接 RBAC**。
  登录态是唯一门槛：`/auth/login`、`/auth/register` 在网关与服务两处白名单内免鉴权，**其余全部要求已登录**。
  所以「权限串」列整列为 `—` 是**预期状态**，不是漏登记。

## 三、本层独有的业务门禁（不在域内）

| 门禁 | 说明 | 位置 |
|---|---|---|
| **店铺已审核通过** | `/goods/**` 全部接口在调域**之前**判定店铺状态；未过审返回 **`code=403`** | 域内**不做**该判断（域不查店铺状态） |
| **分类全路径解析** | 列表 / 详情读时调 goods-center `/categories/paths` 批量补 `categoryPath`；**解析失败只告警、路径留空**，前端回退快照名 | 读时解析，非 N+1；降级不得拖垮主流程 |
| **中台版本比对** | 详情页的「更新提示 + 同步覆盖」在**本层**组装；域只落库/回读 `center_version`，不调中台、不判版本 | 编排职责 |
| **锁定商品只读** | 锁定商品在店主端**整行只读**；锁定信息**不含锁定人**（仅平台端展示） | 域内强制，本层透出 |

## 四、类型所在

| 来源 | 类型 |
|---|---|
| `common`（`com.panoramic.common.store.vo` / `.goods.vo`） | ShopVO, ShopSaveDTO, StoreGoodsSpuPageQueryDTO, StoreGoodsSpuPageItemVO, StoreGoodsSpuSaveDTO, StoreGoodsSpuUpdateDTO, StoreGoodsSkuReplaceDTO, StoreGoodsSkuShelfDTO, PageResult, CategoryTreeVO, BrandVO, SpuBySkuCodeVO |
| **store-bff 私有**（不在 `common`，仅本服务用） | `storebff/vo/LoginResultVO`, `storebff/vo/CurrentUserVO`, `storebff/vo/StoreGoodsSpuDetailBffVO`, `storebff/dto/LoginDTO`, `storebff/dto/RegisterDTO` |

⚠ `StoreGoodsSpuDetailBffVO` 是 **BFF 独有**的详情出参（在 owner 侧 `StoreGoodsSpuDetailVO` 基础上扩展），
与 platform 侧的 `StoreGoodsSpuPlatformDetailVO` 是同款「子类扩字段」做法，**两者不可互换**。

## 五、下游依赖（本层调谁）

| 目标 | 通道 | 内容 |
|---|---|---|
| store 域(8083) | Feign `StoreClient`（owner 侧方法） | 店铺 mine/save/submit；在售商品 CRUD 与 SKU 上下架 |
| goods-center(8081) | Feign `GoodsCenterClient` | 分类树、分类全路径、品牌列表、SPU 详情、按 SKU 编码反查 SPU |

全部经 `common` 的 `BffFeignCall` 包装（剥 cause 链 + 降级文案）。降级口径见 [cross-cutting.md](./cross-cutting.md) 第 12 条。

## 六、业务规则去哪看

店主端页面行为、店铺资料字段、商品编辑约束等见 [`backend/store-bff/README.md`](../../backend/store-bff/README.md)。
