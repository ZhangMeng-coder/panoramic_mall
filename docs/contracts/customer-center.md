<!-- contract-meta
service: customer-center
layer: internal
basePath: /internal/customer
feignClient: backend/customer-center-interface/src/main/java/com/panoramic/contract/customer/api/CustomerCenterClient.java
implScanDirs: backend/customer-center/src/main/java/com/panoramic/customer/controller
typeDirs: backend/customer-center-interface/src/main/java
-->

# 顾客域（customer-center）内部契约 · 第 ② 层

> 顾客域：顾客资料 `customer_profile` + 收货地址 `customer_address`。
> **不暴露公网路由**，只被 mall-bff 经内部 Feign 调用。
> 域内**不做任何鉴权、不做权限判断**（见 [cross-cutting.md](./cross-cutting.md) 第 6、7、14 条）。

**共 8 个接口。**

⚠ **本表 8 条状态均为 `待实现`**（契约先行，服务尚未创建）：本文件先把接口面固定下来，
`契约声明(接口模块)` / `域实现` 两列填 `—` —— 代码不存在，写计划落点只会变成新的漂移点。
`待实现` 行**只核对路径 / 方法 / 权限串的写法**，不参与「契约 ↔ 代码」双向核对（见 [README.md](./README.md)）。
实现完成后须在**同一改动内**把对应行的「状态」摘回留空，否则检查器的反向哨兵会报错。

## 一、归属与形状

- **锚点 `customerId` = `mall_user.id`**：跨域 id 引用、**无外键**，与 `store_id` = 店主账号 id 同一手法。
  所有方法**全按传入锚点过滤**，`customerId` 就是数据权限本身。
- **无 owner / platform 分侧**：本期只做 **C 端自助**，没有平台侧能力，故不存在 store 那种
  「owner / platform 分流由 BFF 调哪一侧决定」的形态（对比 [store.md](./store.md) 第三节）。
- **域内不做任何身份判断**：不判 `X-User-Type`、不校验 token、无 `@PreAuthorize`；
  身份头只读来填 `UserContext`，且**仅用于审计留痕**。
  调用方传的 `customerId` 是否真是「本人」，**由 mall-bff 从登录态取**，域侧不校验 —— 防线在 BFF。
- **形状**：✅ **不包 `RespData`**，直接返回业务结果类型；错误走 `ServiceException` + 真实 HTTP 状态。
- **前缀**：类级 `@RequestMapping` 写死 `/internal/customer/xxx`（本仓库既有做法，**不用 context-path**），
  下表「路径」列是**去掉 `/internal/customer` 前缀后**的部分。

## 二、接口清单

| Feign 方法 | 方法 | 路径 | 入参 | 出参 | 契约声明(接口模块) | 域实现 | 调用方 | 状态 |
|---|---|---|---|---|---|---|---|---|
| getProfile | GET | /profile/{customerId} | `Long` | `CustomerProfileVO` | — | — | CustomerProfileBffService(mall-bff) | 待实现 |
| saveProfile | POST | /profile/{customerId} | `Long`, `CustomerProfileSaveDTO` | `void` | — | — | CustomerProfileBffService(mall-bff) | 待实现 |
| listAddresses | GET | /addresses/{customerId} | `Long` | `List<CustomerAddressVO>` | — | — | CustomerAddressBffService(mall-bff) | 待实现 |
| getAddress | GET | /addresses/{customerId}/{id} | `Long`, `Long` | `CustomerAddressVO` | — | — | CustomerAddressBffService(mall-bff) | 待实现 |
| saveAddress | POST | /addresses/{customerId} | `Long`, `CustomerAddressSaveDTO` | `Long` | — | — | CustomerAddressBffService(mall-bff) | 待实现 |
| updateAddress | PUT | /addresses/{customerId}/{id} | `Long`, `Long`, `CustomerAddressSaveDTO` | `void` | — | — | CustomerAddressBffService(mall-bff) | 待实现 |
| deleteAddress | DELETE | /addresses/{customerId}/{id} | `Long`, `Long` | `void` | — | — | CustomerAddressBffService(mall-bff) | 待实现 |
| setDefaultAddress | POST | /addresses/{customerId}/{id}/default | `Long`, `Long` | `void` | — | — | CustomerAddressBffService(mall-bff) | 待实现 |

> 「入参」列里**连续两个 `Long`** 时，第一个是 **`customerId`**（数据权限锚点），第二个是 `id`。
> 例：`getAddress` 的 `Long, Long` = `customerId, id`。

> 字段定义**不在本表**，去 `customer-center-interface`（包根 `com.panoramic.contract.customer`）的
> `dto` / `vo` 包里看；表里只登记**有哪些接口、形状是什么、类型在哪、谁在调**，不抄字段。

## 三、业务规则去哪看

资料与地址的落库口径、**默认地址唯一性**（同一顾客至多一条 `is_default=1`）、
删默认地址后**不自动递补**等业务规则，见 [`backend/customer-center/README.md`](../../backend/customer-center/README.md)（随实现任务创建）。
本文件只写接口与形状。
