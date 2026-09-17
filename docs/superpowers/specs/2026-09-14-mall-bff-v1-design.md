# mall-bff 一期：C 端账号骨架 + 网关接入（设计）

> 日期：2026-09-14
> 范围：新建 `backend/mall-bff`（8085）+ 网关路由/白名单 + `mall_user` 表 + 契约与检查器
> 验证口径：后端**只到编译通过**；前端**只到构建通过**。**不启动任何服务、不打任何接口**（根 CLAUDE.md 硬规则）

---

## 一、目标与非目标

### 目标

给商城前台（`frontend/mall`）建它对应的端 BFF（`mall-bff`，第三套身份空间 `type=user`），
并在网关开 `/mall/**` 路由。一期只做**账号链路**：注册 / 登录 / 登出 / 当前用户 + 短信验证码（模拟）。

一期结束时应达到的状态：**前台登录链路在配置与结构上完整闭环**——
页面 → 网关 `/mall/**`（验签 + 查登录态）→ mall-bff（签发 `type=user`）→ Redis `panoramic:login:user:{id}`。

### 非目标（YAGNI）

- ❌ 首页数据聚合（分类宫格 / 商品列表 / 商品详情 / 轮播 / 热搜词）—— 二期
- ❌ 购物车 / 订单 / 评价（属未来 trade 域，尚不存在）
- ❌ `frontend/mall` 接线（除 `vite.config.ts` 一句已失效的注释外**一行不改**；它没有登录页）
- ❌ 真实短信通道、验证码落库/落 Redis
- ❌ C 端 RBAC（与店主端同理，见 D6）
- ❌ 自动提交（完成后给 merge/PR 选项）

### 为什么拆成两期

一期动到的面已经跨「新模块 + 新表 + 网关 + 契约 + 检查器」五处；首页数据还额外依赖
**库里现在填不满**（一级分类 9 个 vs 前端基准 10 个、在售商品仅 1 件、无销量字段、无 banner/热搜词表），
那是另一个需要先定数据口径的决策。两者混在一次改动里，风险与不可验证面都翻倍。

---

## 二、已确认决策

| # | 决策项 | 结论 | 依据 |
|---|---|---|---|
| D1 | 一期范围 | C 端账号 + 网关路由；首页数据二期 | 用户确认 |
| D2 | 服务骨架 | 端口 **8085**、包 `com.panoramic.mallbff`、pom 与 Nacos 装配**逐字对齐 store-bff** | 用户要求「mall-bff 要和其他 BFF 一致」 |
| D3 | 账号模型 | **手机号即账号** + **短信验证码**登录；验证码**固定 888888**（可配常量），`sms-code` 接口**空转**（不真发、不落库、不落 Redis） | 用户确认 |
| D4 | 表结构 | `mall_user`：`phone` 单列作账号（唯一索引）+ **保留 `password` 列作占位**（本期不读写，为将来加密码登录免一次 `ALTER`） | 用户确认 |
| D5 | 接口集合 | **5 条**：`sms-code` / `register` / `login` / `logout` / `me` | 用户确认 |
| D6 | 权限 | **不接 RBAC**：无 `@PreAuthorize`、不灌任何权限种子（对齐 store-bff） | 用户要求一致 |
| D7 | `CurrentUserVO` | 与 store-bff **同构**（`id/username/nickname/phone/perms`）；因手机号即账号，`username` 与 `phone` 值相同，且 `phone` 由快照里的手机号**回填、不查库** | 用户要求一致 |
| D8 | 网关接入 | 加 `/mall/**` 路由 + `bff-services` 加 `mall-bff` + **两侧白名单各写一份（含 `sms-code`）** | `/auth/sms-code` 是登录前调用的，漏登记则按钮 401 |
| D9 | 检查器 | 端 BFF 名单与网关前缀改为**从网关配置推导**，推不出前缀**即 fail**；另加「路由指向名单外服务」告警 | 硬编码名单在新增 BFF 时会静默漏检 |
| D10 | 数据库 | **不备份**（不碰任何既有表）；只执行 `CREATE TABLE IF NOT EXISTS mall_user` | 用户口径：不涉及现有表修改则不需备份 |
| D11 | 前端 | 不改（`vite.config.ts` 仅更新已失效注释） | 一期不含接线 |
| D12 | 文档同步 | 与代码**同一改动内**改完（清单见第七节） | 仓库硬规则 |

---

## 三、服务骨架

### 模块

| 项 | 值 |
|---|---|
| 目录 / artifactId | `backend/mall-bff` / `mall-bff` |
| 端口 | **8085**（8080–8084 已占） |
| 基包 | `com.panoramic.mallbff` |
| `spring.application.name` | `mall-bff` ←与目录名、`lb://mall-bff` 三者必须一字不差 |
| 启动类 | `MallBffApplication`：`@SpringBootApplication(scanBasePackages="com.panoramic")` + `@MapperScan("com.panoramic.mallbff.mapper")` + `@EnableDiscoveryClient` |

> `scanBasePackages="com.panoramic"` 让 common 的 `GlobalExceptionHandler` / `MybatisPlusConfig` / `CorsConfig`、
> common-auth 的 `SecurityConfig` / `JwtService` / `LoginUserCacheService` 一并装配——这是端 BFF 的装配面，与 store-bff 同源。

### 依赖

pom **逐字对齐 `store-bff/pom.xml`**：`common`、`common-auth`、`spring-boot-starter-web`、`starter-validation`、
`nacos-discovery`、`nacos-config`、`mybatis-plus-spring-boot4-starter`、`mybatis-plus-jsqlparser`、
`mysql-connector-j`(runtime)、`jackson-databind`、`jackson-datatype-jsr310`、`lombok`(optional)。
OpenFeign / LoadBalancer / Resilience4j 由 `common` 传递带入（`common/pom.xml:65-76`）。

### 配置

```yaml
server:
  port: 8085
spring:
  application:
    name: mall-bff
  config:
    import:
      - nacos:datasource-mysql.yml
      - nacos:datasource-redis.yml
      - nacos:auth.yml
      - nacos:feign-circuitbreaker.yml
panoramic:
  auth:
    whitelist-paths: /auth/login,/auth/register,/auth/sms-code
  mall:
    sms-fixed-code: 888888
```

- **四个 dataId 全加载**（不带 `optional:`）：cross-cutting 第 12 条矩阵规定「端 BFF 加载 4 个」。
  ⚠ 一期**没有 Feign 客户端**，故 `feign-circuitbreaker.yml` 是随矩阵加载的空转件——在 yml 注释里写明，
  二期接域调用时无需再改配置。
- **`@EnableFeignClients` 一期不加**：`com.panoramic.common.mall` 包还不存在，加了等于去扫一个空包（假装配）。
- **`JacksonConfig` 必须加**：⚠ 不是装饰。`LoginUserCacheService` 构造要注入 `ObjectMapper`，
  而 Spring Boot 4 的 web starter **不再自动注册** `ObjectMapper`（store-bff 因此显式提供）。
  漏了它 **mall-bff 起不来**。
- **`lombok.config`**：照抄 store-bff（`lombok.copyableAnnotations += ...Lazy`），为 `@Lazy` 断环预留。
- **本地白名单必须覆盖**：`SecurityConfig` 默认值只含 `/auth/login`，注册与取码都要自己补。

---

## 四、账号与登录态

### 表 `mall_user`

`backend/mall-bff/src/main/resources/db/schema.sql`，与 store-bff **同库** `panoramic_mall`（只划分表所有权、不复刻库）：

| 列 | 类型 | 说明 |
|---|---|---|
| `id` | `BIGINT UNSIGNED AUTO_INCREMENT` | 主键 |
| `phone` | `VARCHAR(20) NOT NULL` | **手机号即账号**；`UNIQUE KEY uk_phone` |
| `password` | `VARCHAR(100) NULL` | ⚠ **占位**：本期不读写（C 端走验证码）；将来加密码登录/改密时免一次 `ALTER` |
| `nickname` | `VARCHAR(50) NULL` | 昵称；注册时留空则默认取手机号（对齐 store-bff 行为） |
| `status` | `TINYINT NOT NULL DEFAULT 1` | 1 启用 / 0 停用；`KEY idx_status` |
| `create_user` / `update_user` | `VARCHAR(32) NULL` | 审计，`UserType:UserId` |
| `create_time` / `update_time` | `DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP` | 审计（`update_time` 带 `ON UPDATE`） |
| `is_delete` | `TINYINT NOT NULL DEFAULT 0` | 逻辑删除 |

与 `store_user` 的差异只有一处：`username` → `phone`。

### 接口（5 条）

`AuthController`（`@RequestMapping("/auth")`），**全部返回 `RespData`**、**权限串整列 `—`**（见 D6）：

| 方法 | 路径 | 入参 | 出参 | 免鉴权 |
|---|---|---|---|---|
| POST | `/auth/sms-code` | `SmsCodeDTO` | `RespData<Void>` | ✅ |
| POST | `/auth/register` | `RegisterDTO` | `RespData<LoginResultVO>` | ✅ |
| POST | `/auth/login` | `LoginDTO` | `RespData<LoginResultVO>` | ✅ |
| POST | `/auth/logout` | — | `RespData<Void>` | ❌ |
| GET | `/auth/me` | — | `RespData<CurrentUserVO>` | ❌ |

### 入参 DTO / 出参 VO 字段

字段即契约，逐个定死（`LoginResultVO` = `token` + `CurrentUserVO`，后者见下）：

| 类型 | 字段 |
|---|---|
| `SmsCodeDTO` | `phone`：`@NotBlank` + `@Pattern(^1[3-9]\d{9}$)`（手机号） |
| `RegisterDTO` | `phone`（同上）、`code`：`@NotBlank`、`nickname`：`@Size(max=50)`（可空，留空则默认取手机号） |
| `LoginDTO` | `phone`（同上）、`code`：`@NotBlank` |
| `CurrentUserVO` | `id` / `username` / `nickname` / `phone` / `perms`（D7） |

### 校验与错误口径

| 场景 | 结果 |
|---|---|
| `sms-code`：手机号格式不符 | 400 `参数错误`（`@Pattern` 兜底） |
| `sms-code`：正常 | 200 + `RespData<Void>`；服务内 `log.info` 打出「[模拟短信] {phone} 的验证码：888888」**后什么都不做**——不真发短信、不落库、不落 Redis。**不校验手机号是否已注册**（取码同时服务注册与登录，不能偏袒任一侧） |
| 验证码 ≠ 888888 | 400 `验证码错误` |
| `register`：手机号已存在 | 400 `手机号已注册` |
| `login`：手机号不存在 | 400 `手机号未注册` |
| `login`：`status != 1` | 515 `用户已被停用`（复用 `ServiceExceptionEnums.USER_DISABLED`） |

判定顺序：**先验证码 → 再查用户 → 再判停用**。先校验验证码可在不命中库的情况下快速失败。

### 登录态

与 store-bff 逐条同构，**唯一差异是身份类型**：

```java
loginUser.setUserType(LoginUser.USER_TYPE_USER);              // ← 与下一行必须成对一致
String token = jwtService.generateToken(user.getId(), LoginUser.USER_TYPE_USER);
```

⚠ **这两处必须同时是 `USER_TYPE_USER`**：`LoginUserCacheService` 按 `userType` 拼键、网关按 JWT 的 `type` claim 拼同一把键，
不一致 → 网关查不到登录态 → **全端 401**。`loginUser.setRoleIds(emptyList())` + `setPerms(emptySet())`（C 端无 RBAC）。

Redis 键：`panoramic:login:user:{userId}`。登出即 `loginUserCacheService.delete(USER_TYPE_USER, userId)`。

`logout` 取身份的方式对齐 store-bff 的防御写法（`/auth/logout` 不在白名单内，正常情况下必有登录态，此为兜底）：

```java
LoginUser loginUser = UserContext.getLoginUser();
authService.logout(loginUser == null ? LoginUser.USER_TYPE_USER : loginUser.getUserType(), UserContext.getUserId());
```

### `CurrentUserVO`（D7）

与 store-bff 同构的五个字段；因手机号即账号，`username` 与 `phone` **值相同**：

| 字段 | 来源 |
|---|---|
| `id` / `username` / `nickname` | Redis 登录快照（`UserContext.getLoginUser()`），`/auth/me` **不查库** |
| `phone` | 由快照的 `username` **回填**（`LoginUser` 无 phone 字段，而账号本身就是手机号） |
| `perms` | 恒为空数组（C 端无 RBAC） |

> ⚠ 与 store-bff 的一处**行为**差异（形状相同）：store-bff 的 `toCurrentUser` 声明了 `phone` 却从不赋值（现存死字段），
> mall-bff **必须**填——手机号是本端的账号标识，前端要用来展示「已登录」态。此处写明，免得后人按 store-bff 照抄成 null。

`LoginResultVO` = `token` + `CurrentUserVO`（同 store-bff）。

---

## 五、网关接入

`backend/gateway/src/main/resources/application.yml`：

```yaml
- id: mall-bff-route
  uri: lb://mall-bff
  filters: [StripPrefix=1]
  predicates: [Path=/mall/**]

panoramic:
  gateway:
    bff-services: admin,store-bff,mall-bff
  auth:
    whitelist-paths: >-
      /admin/auth/login,/store/auth/login,/store/auth/register,
      /mall/auth/login,/mall/auth/register,/mall/auth/sms-code,/discovery/**
```

**无需改动的两处**（说明清楚，免得被误当成漏做）：

- `BffRouteGuardFilter` —— 配置驱动；`bff-services` 里加了名字即放行。
- `AuthGlobalFilter` —— 按 JWT 的 `type` claim 拼键，**对 userType 无白名单限制**，`type=user` 天然可通。

⚠ **白名单两处各写一份**（网关带前缀、服务侧不带），三对路径逐条对应：

| 网关侧 | 服务侧 |
|---|---|
| `/mall/auth/login` | `/auth/login` |
| `/mall/auth/register` | `/auth/register` |
| `/mall/auth/sms-code` | `/auth/sms-code` |

---

## 六、检查器改动（`docs/contracts/drift-check.mjs`）

### 问题

第 8 项（网关）把端 BFF 名单**硬编码**为 `['admin','store-bff']`（:605），前缀映射也只有 `/admin`、`/store`（:616）。
只加网关白名单而不改检查器，**mall-bff 的「两侧白名单互查」会被静默跳过**——而这项正是本次最该守住的地方。

### 改法

名单从已解析出的 `bff-services` 值推导，前缀从 routes 的 `uri: lb://<svc>` ↔ `Path=/<prefix>/**` 推导：

- 推不出前缀 → **fail**（不是 warn）。**覆盖不允许静默下降**，这是本项设计的核心约束。
- 附带一条 warn：某条路由的转发目标不在 `bff-services` 里（该路由经网关一律 403，属死配置）。
- 硬编码的 `for (const svc of [...])` 与三元前缀映射一并删除。

---

## 七、文档同步清单

| 文件 | 改动 |
|---|---|
| `docs/contracts/mall-bff.md` | 重写：去「待建」，补 5 行接口表 + 形状规则 + 类型所在 + 「一期不调域」 + 已确定的身份/Redis/网关约定 |
| `docs/contracts/README.md` | 索引里 `mall-bff` 条数 `待建` → `5` |
| `docs/contracts/gateway.md` | 路由表 2→3 条、`bff-services` 值、两侧白名单表、相关位置注释 |
| `docs/contracts/cross-cutting.md` | 第 1 条（消费位置补 mall-bff Controller）、第 4 条（签发位置补 mall-bff）、第 10/11 条（路由与白名单值）、**第 12 条加载矩阵加 mall-bff 列**、第 13 条（如实登记：三端中 mall-bff 一期无调用点） |
| `docs/contracts/drift-check.mjs` | 第六节的改法 |
| `backend/pom.xml` | `<modules>` 加 `mall-bff` |
| `backend/mall-bff/README.md` | 新建：服务说明（职责/架构位置/实体标记/边界/一期不做什么），**不列接口** |
| `backend/README.md` | 模块表 + 启动清单 + MySQL 表清单 + 请求链路图 + 「鉴权只到端 BFF」段 |
| `README.md`（根） | 简介句、架构图、目录表、已实现功能、快速开始、开发路线 |
| `CLAUDE.md`（根） | 白名单当前值；「mall-bff 待建」→ 已建；补一条规则：C 端与店主端同理不接 RBAC |
| `backend/common-auth/README.md` | 「各端身份空间」表 mall-bff 状态 `待建` → `已有`；首句「未来的 mall-bff」 |
| `frontend/README.md`、`frontend/mall/README.md`、`frontend/mall/vite.config.ts` | 「mall-bff 尚未创建/未接接口」→ 已建（仅账号接口），首页仍未接 |

两处 ASCII 架构图要重排（加 mall 前端与 mall-bff 两个框）。按**显示宽度**（中文算 2 列）核对边框对齐且 ≤ 96 列，不靠肉眼。

---

## 八、验证口径（止步于编译/构建，不启动服务、不打接口）

1. `cd backend && mvn -N install`（父 POM 的 modules 变了）
2. `mvn -pl mall-bff -am compile`
3. **`node docs/contracts/drift-check.mjs` 退出码 0** —— 本次最硬的集成检查，覆盖：
   5 条接口双向差集、`RespData` 必现、入出参类型存在性、页面级无 `@PreAuthorize`、
   网关路由 ↔ 契约页、**两侧白名单互查（含 mall-bff）**、Nacos 无 `optional:`
4. 文本级人工核对（检查器覆盖不到的）：
   - `mall_user` 列名 ↔ `MallUser` 实体字段一一对应（`password` 是唯一的有意保留项）
   - 无 `setCreateUser/setUpdateUser/setCreateTime/setUpdateTime` 手写审计
   - `AuthService` 里 `setUserType` 与 `generateToken` 两处都是 `USER_TYPE_USER`
   - 两处 ASCII 架构图宽度
5. `cd frontend/mall && npm run build`（只改了 `vite.config.ts` 注释，但它是 `.ts`，照规矩做构建级验证）
6. 数据操作（不属验证）：执行 `mall-bff/db/schema.sql` → 回读 `SHOW CREATE TABLE mall_user` + 列清单

---

## 九、风险

| 风险 | 应对 |
|---|---|
| 白名单只改一处 → 登不进去 / 接口裸露 | 检查器双向核对，且本次专门补上了 mall-bff 这一路的覆盖 |
| 漏登记 `/auth/sms-code` → 「获取验证码」按钮 401 | 已列入白名单三对路径；契约页也会登记 |
| `setUserType` 与 `generateToken` 的 type 不一致 → 全端 401 | 文本核对项；`USER_TYPE_USER` 全网只应出现在这两处 |
| 漏 `JacksonConfig` → `ObjectMapper` 无 bean → **起不来** | 已列为骨架必做项 |
| 一期无 Feign 客户端却加载熔断配置 | 空转、无害；yml 注释写明，第 13 条如实登记 |
| 检查器改为推导后解析失效 → 覆盖静默下降 | 推不出前缀即 **fail**，不降级为 warn |
| 固定验证码 888888 被误当正式实现 | 配置键命名 `sms-fixed-code` 自带「临时」语义；schema/README/契约三处标注「模拟」与升级路径 |
