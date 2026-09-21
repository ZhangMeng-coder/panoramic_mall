<!-- contract-meta
service: trade-center
layer: internal
basePath: /internal/trade
feignClient: backend/trade-center-interface/src/main/java/com/panoramic/contract/trade/api/TradeCenterClient.java
implScanDirs: backend/trade-center/src/main/java/com/panoramic/trade/controller
typeDirs: backend/trade-center-interface/src/main/java
-->

# 交易域（trade-center）· 第 ② 层 —— **待建**

> ⚠ **本服务尚不存在。** 此文件为占位，用于固定它在契约体系中的位置与既定约定。
> 待建项记录在仓库根 `todo.md`。

## 一、归属（术语防呆）

根 `CLAUDE.md` 已划定：**"顾客资料 / 收货地址"属 `customer-center`；"购物车 / 订单 / 评价"属本域**。
⚠ 别把这些实体塞进 `goods-center`（标准商品模板库）或 `store`（店铺在售商品/库存/信誉）。

## 二、已确定、不可改的部分

第 ② 层（纯域）的通用约定（不暴露公网路由、域内不鉴权、不包 `RespData`、类型放本域接口模块、
熔断 4xx 不计失败率、Nacos 加载矩阵、前缀由类级 `@RequestMapping` 写死）**照第 ② 层既有各域执行**，
见 [README.md](./README.md) 与 [cross-cutting.md](./cross-cutting.md) 第 2、6、7、12、13、14 条；
本域与既有域的唯一差别是名字：模块 `trade-center` / `trade-center-interface`，包根 `com.panoramic.contract.trade`，
前缀 `/internal/trade`。**建域时同步建 `-interface` 模块**（`common` 已收敛为纯基座、不含契约类型）。

## 三、接口清单

**待建** —— 服务创建后按 [README.md](./README.md) 的格式补齐本表，并同步更新索引里的条数。
