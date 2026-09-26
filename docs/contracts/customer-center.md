<!-- contract-meta
service: customer-center
layer: internal
basePath: /internal/customer
feignClient: backend/customer-center-interface/src/main/java/com/panoramic/contract/customer/api/CustomerCenterClient.java
implScanDirs: backend/customer-center/src/main/java/com/panoramic/customer/controller
typeDirs: backend/customer-center-interface/src/main/java
respEnvelope: RespData
-->

# 顾客域（customer-center）内部契约 · 第 ② 层

> 顾客域：顾客资料 `customer_profile` + 收货地址 `customer_address`。
> **不暴露公网路由**，只被 mall-bff 经内部 Feign 调用。
> 域内**不做任何鉴权、不做权限判断**（见 [cross-cutting.md](./cross-cutting.md) 第 6、7、14 条）。

## 一、归属与形状

- **锚点 `customerId` = `mall_user.id`**：跨域 id 引用、**无外键**，与 `store_id` = 店主账号 id 同一手法。
  所有方法**全按传入锚点过滤**，`customerId` 就是数据权限本身。
- **无 owner / platform 分侧**：本期只做 **C 端自助**，没有平台侧能力，故不存在 store 那种
  「owner / platform 分流由 BFF 调哪一侧决定」的形态（对比 [store.md](./store.md) 第三节）。
- **域内不做任何身份判断**：不判 `X-User-Type`、不校验 token、无 `@PreAuthorize`；
  身份头只读来填 `UserContext`，且**仅用于审计留痕**。
  调用方传的 `customerId` 是否真是「本人」，**由 mall-bff 从登录态取**，域侧不校验 —— 防线在 BFF。
- **形状**：出参**包 `RespData<T>`**（无返回值用 `RespData<Void>`）：业务结果（含业务失败 `code=400/403/404`）一律 **HTTP 200 + `{code,msg,data}`**，只有兜底异常才是 **HTTP 500** —— 见 [cross-cutting.md](./cross-cutting.md) 第 2、13 条。
- **前缀**：`/internal/customer`；拼法（不是 context-path、由 Controller 类级 `@RequestMapping` 写死）
  见 [README.md](./README.md) 的「内部 Feign 的「路径」前缀怎么来的」。

## 二、接口清单

| Feign 方法 | 方法 | 路径 | 入参 | 出参 | 契约声明(接口模块) | 域实现 | 调用方 | 状态 |
|---|---|---|---|---|---|---|---|---|
| getProfile | GET | /profile/{customerId} | `Long` | `RespData<CustomerProfileVO>` | `CustomerCenterClient#getProfile` | `ProfileController#getProfile` | CustomerProfileBffService(mall-bff) |  |
| saveProfile | POST | /profile/{customerId} | `Long`, `CustomerProfileSaveDTO` | `RespData<Void>` | `CustomerCenterClient#saveProfile` | `ProfileController#saveProfile` | CustomerProfileBffService(mall-bff) |  |
| listProfilesByIds | POST | /profile/batch | `CustomerProfileBatchQueryDTO` | `RespData<List<CustomerProfileVO>>` | `CustomerCenterClient#listProfilesByIds` | `ProfileController#listProfilesByIds` | EvaluationBffService(mall-bff), StoreEvaluationBffService(store-bff) |  |
| listAddresses | GET | /addresses/{customerId} | `Long` | `RespData<List<CustomerAddressVO>>` | `CustomerCenterClient#listAddresses` | `AddressController#listAddresses` | CustomerAddressBffService(mall-bff) |  |
| getAddress | GET | /addresses/{customerId}/{id} | `Long`, `Long` | `RespData<CustomerAddressVO>` | `CustomerCenterClient#getAddress` | `AddressController#getAddress` | OrderBffService(mall-bff) |  |
| saveAddress | POST | /addresses/{customerId} | `Long`, `CustomerAddressSaveDTO` | `RespData<Long>` | `CustomerCenterClient#saveAddress` | `AddressController#saveAddress` | CustomerAddressBffService(mall-bff) |  |
| updateAddress | PUT | /addresses/{customerId}/{id} | `Long`, `Long`, `CustomerAddressSaveDTO` | `RespData<Void>` | `CustomerCenterClient#updateAddress` | `AddressController#updateAddress` | CustomerAddressBffService(mall-bff) |  |
| deleteAddress | DELETE | /addresses/{customerId}/{id} | `Long`, `Long` | `RespData<Void>` | `CustomerCenterClient#deleteAddress` | `AddressController#deleteAddress` | CustomerAddressBffService(mall-bff) |  |
| setDefaultAddress | POST | /addresses/{customerId}/{id}/default | `Long`, `Long` | `RespData<Void>` | `CustomerCenterClient#setDefaultAddress` | `AddressController#setDefaultAddress` | CustomerAddressBffService(mall-bff) |  |

> 「入参」列里**连续两个 `Long`** 时，第一个是 **`customerId`**（数据权限锚点），第二个是 `id`。
> 例：`getAddress` 的 `Long, Long` = `customerId, id`。

> ⚠ **批量读资料（`listProfilesByIds`）为「评价区的昵称 / 头像」而加**（2026-09-24 落契约）：
> 调用方拿本页的 `customerId` 集合**一次**取回，**禁止**逐条调 `getProfile`（10 行评价 = 10 次跨服务调用）。
> 三点形状：
> ① **查不到的 id 跳过、不出现在出参里**（口径同 store 域的 `batchSpuDetail`）——调用方按
> 「拿不到 = 无资料」处理，**不整批失败**（评价列表不能因为某个顾客资料缺失就整页取不回来）；
> ② ⚠ 与 `getProfile` 的「无资料行返回**仅含 id 的空 VO**」刻意不同：批量场景给每人补一个占位没有意义，
> 反而让调用方分不清「真有一条空资料」与「压根没这个人」；
> ③ 用 `POST + @RequestBody` 传 id 集合（`@SpringQueryMap` 对集合字段的序列化口径不确定，与
> [store.md](./store.md) 的跨店分页同因）。
> ⚠ 它**不返回手机号**——手机号是 `mall_user` 的列，本域不持（见上「不含账号字段」）。

> 字段定义**不在本表**，去 `customer-center-interface`（包根 `com.panoramic.contract.customer`）的
> `dto` / `vo` 包里看；表里只登记**有哪些接口、形状是什么、类型在哪、谁在调**，不抄字段。

## 三、业务规则去哪看

资料与地址的落库口径、**默认地址唯一性**（同一顾客至多一条 `is_default=1`）、
删默认地址后**不自动递补**等业务规则，见 [`backend/customer-center/README.md`](../../backend/customer-center/README.md)。
本文件只写接口与形状。
