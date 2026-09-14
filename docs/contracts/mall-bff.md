<!-- contract-meta
service: mall-bff
layer: page
baseUrl: /mall
scanDirs: backend/mall-bff/src/main/java/com/panoramic/mallbff/controller
typeDirs: backend/common/src/main/java, backend/mall-bff/src/main/java
-->

# 商城前台 BFF（mall-bff）· 第 ① 层 —— **待建**

> ⚠ **本服务尚不存在。** 此文件为占位，用于固定它在契约体系中的位置与既定约定。
> 待建项记录在仓库根 `todo.md`。

## 一、已确定、不可改的部分

| 项 | 约定 | 依据 |
|---|---|---|
| 身份类型 | 签发 `type=user` 的令牌（第三套身份空间） | [cross-cutting.md](./cross-cutting.md) 第 4 条；`LoginUser.USER_TYPE_USER` |
| Redis 键 | `panoramic:login:user:{userId}` | 同上第 5 条 |
| 网关前缀 | `/mall/**` → `lb://mall-bff`（`StripPrefix=1`） | [gateway.md](./gateway.md) |
| 网关白名单 | 上线时**必须**把 `mall-bff` 加进 `panoramic.gateway.bff-services`，否则该端全部 403 | 同上第二节 |
| 鉴权白名单 | 登录/注册路径需**同时**登记在网关侧与服务本地侧 | 同上第三节 |
| 内部依赖 | 经 Feign 调 goods-center（商品/分类/品牌）；交易类后续接 `trade-center` | [cross-cutting.md](./cross-cutting.md) 第 13 条 |
| 前端形态 | `frontend/mall` 从零构建静态样张转为 Vue 3 + Vite 子项目（建议端口 5175） | 根 `CLAUDE.md`「mall 前台（用户端）视觉与结构约定」 |

## 二、前端契约的**视觉与结构**基准

mall 前台的**页面契约**目前由样张固定，不是本文档：

- 风格与结构基准：`frontend/mall`（风格样张）
- 约束条文：根 `CLAUDE.md` 的「mall 前台（用户端）视觉与结构约定」

⚠ 该约定约束的是**视觉与结构，不是技术形态**：正式实现仍按 BFF 分层走，但**长什么样、分哪几块以样张为准**，
且「样张先行」——新增区块先在样张里改好、定了，再落到正式页面。

## 三、接口清单

**待建** —— 服务创建后按 [README.md](./README.md) 的格式补齐本表，并同步更新索引里的条数。
