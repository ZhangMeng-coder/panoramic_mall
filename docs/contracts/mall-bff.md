<!-- contract-meta
service: mall-bff
layer: page
baseUrl: /mall
scanDirs: backend/mall-bff/src/main/java/com/panoramic/mallbff/controller
typeDirs: backend/common/src/main/java, backend/mall-bff/src/main/java
-->

# 商城前台 BFF（mall-bff）· 第 ① 层

> 商城**前台（C 端顾客）**的端 BFF，端口 **8085**，网关前缀 `/mall/**`（`StripPrefix=1`）。
> 它是本仓库的**第三个端 BFF**（另两个是 admin / store-bff），签发**第三套身份** `type=user`。
> 一期范围 = **顾客账号骨架**（取码/注册/登录/登出/me），**不调任何业务域**；首页数据聚合是二期。

## 一、接口形态

页面级通用规则见 [README.md](./README.md)：**必包** `RespData{code,msg,data}`。
字段定义**不在本表**，去下列类型所在的源码看（表里不抄字段，抄一份就是制造第二个会漂移的地方）。

| 类型 | 所在包 |
|---|---|
| `SmsCodeDTO` / `RegisterDTO` / `LoginDTO` | `backend/mall-bff/src/main/java/com/panoramic/mallbff/dto/` |
| `CurrentUserVO` / `LoginResultVO` | `backend/mall-bff/src/main/java/com/panoramic/mallbff/vo/` |
| `RespData` | `backend/common/src/main/java/com/panoramic/common/vo/` |

## 二、接口清单（5 条）

| 方法 | 路径 | 权限串 | 入参 | 出参 | 声明位置 | 状态 |
|---|---|---|---|---|---|---|
| POST | /auth/sms-code | — | `SmsCodeDTO` | `Void` | AuthController.java:37 | |
| POST | /auth/register | — | `RegisterDTO` | `LoginResultVO` | AuthController.java:46 | |
| POST | /auth/login | — | `LoginDTO` | `LoginResultVO` | AuthController.java:54 | |
| POST | /auth/logout | — | — | `Void` | AuthController.java:62 | |
| GET | /auth/me | — | — | `CurrentUserVO` | AuthController.java:74 | |

⚠ **权限串一律为空**：C 端顾客**不接 RBAC**（与店主端同理），本模块没有、也不应有任何 `@PreAuthorize`。
登录后顾客对自己的数据全权限——**这是预期状态，不是漏登记**。

### 形状与行为口径（表里放不下的）

| 项 | 口径 |
|---|---|
| 账号形态 | **账号即手机号**：`phone` 为登录账号（`mall_user` 唯一键 `uk_phone`）；`username` 不是独立列，导出到快照与 `CurrentUserVO` 时与 `phone` 同值 |
| 验证方式 | 手机号 + **短信验证码**，无密码。⚠ 短信为**模拟实现**：取码只打日志、不放真实短信、不落库、不落 Redis；校验与固定码 `panoramic.mall.sms-fixed-code`（默认 `888888`）比对 |
| 免鉴权路径 | `/auth/sms-code`、`/auth/register`、`/auth/login`（**两处各写一份**，见 [gateway.md](./gateway.md) 第三节）。⚠ `sms-code` 在登录**之前**被调用，漏登记则「获取验证码」直接 401 |
| 错误码 | 验证码错误 `400`；手机号已注册 `400`；手机号未注册 `400`；账号停用 `USER_DISABLED`（`515`） |
| 校验顺序 | 注册：验码 → 手机号查重 → 建号；登录：验码 → 查账号 → 查状态 |
| 登录态 | 签发 `type=user` 的 JWT，Redis 键 `panoramic:login:user:{userId}` |
| 身份类型绑定 | 本端只接受 `type=user` 的登录态（`panoramic.auth.user-type: user`）；跨端 token（`admin` / `store`）在 `AuthTokenFilter` 处即按未认证处理 → **HTTP 401**（见 [cross-cutting.md](./cross-cutting.md) 第 9 条） |
| 登出 | 删除 Redis 快照即服务端下线；本地 token 由前端清除 |
| 内部依赖 | **一期没有**：`@EnableFeignClients` 未启用、`com.panoramic.common.mall` 包不存在。二期接 goods-center（商品/分类）做首页聚合时再加 |

## 三、前端契约的**视觉与结构**基准

mall 前台的**页面契约**（长什么样、分哪几块）由前端工程自身固定，不是本文档：

- 风格与结构基准：`frontend/mall`（Vue 3 + Vite + TypeScript，端口 5175）
- 约束条文：根 `CLAUDE.md` 的「mall 前台（用户端）视觉与结构约定」

⚠ 该约定约束的是**视觉与结构，不是技术形态**：前端按 BFF 分层走，但**长什么样、分哪几块以 `frontend/mall` 为准**，
且「基准先行」——新增区块先在该工程里改好、定了，再往外铺。
⚠ 前端**账号页已接入本表这 5 条接口**（`/login`、`/register` 两页 + 顶栏登录态 + 守卫的刷新重建，
接入点见 `frontend/mall` 的 `src/api/auth.ts`）；**首页六个区块的数据仍是静态的 `src/mock/`**，
首页数据聚合属二期。
