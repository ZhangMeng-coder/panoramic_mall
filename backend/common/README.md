# common — 公共基座

后端公共基座模块，**非独立服务、不参与启动**，作为依赖被**所有服务**引用（业务域 goods-center / store，端 BFF admin / store-bff）。基包 `com.panoramic.common`。

> ⚠ **本模块刻意不引入 Redis / JJWT**：鉴权装配类（`SecurityConfig` / `AuthTokenFilter` / `JwtService` / `LoginUserCacheService`）已下移到 [common-auth](../common-auth/)，**只有端 BFF 依赖它**。业务域只依赖 common，因此结构上拿不到认证链与登录态——这是「域服务不鉴权」的模块级保证（2026-09-10）。

## 功能清单

| 包 | 组件 | 用途 |
|---|---|---|
| `vo` | `RespData<T>` | 统一接口返回结构 `{code, msg, data}`，成功 `code=200`；提供 `success/error` 静态工厂 |
| `vo` | `BasePageVO` | 分页查询参数基类：`pageNum/pageSize` 自带 `@NotNull/@Min/@Max` 校验 |
| `vo` | `BaseEntity` | 实体基类：`createUser/createTime/updateUser/updateTime/isDelete`；`isDelete` 标注 `@TableLogic` 逻辑删除。**`createUser`/`updateUser` 为 `String`，值 `UserType:UserId`**（如 `admin:1`），对应列 `VARCHAR(32)` |
| `exception` | `ServiceException` | 业务异常，支持枚举或自定义 `(code, message)` 构造；业务校验失败统一 `code=400` |
| `exception` | `GlobalExceptionHandler` | `@RestControllerAdvice` 全局异常兜底：业务异常、参数校验、404/403、JSON 解析错误、未知异常 → 统一 `RespData` |
| `valid` | `ValidationGroups` | 校验分组常量 `Create/Update/Delete`，用于 @RequestBody 按场景分组校验 |
| `config` | `MybatisPlusConfig` | 分页插件（PaginationInnerInterceptor） |
| `config` | `MyMetaObjectHandler` | `insertFill/updateFill` 自动填充 `createTime/createUser/updateTime/updateUser`；操作人取 `UserContext`，拼为 **`{userType}:{userId}`**，未登录（无 userId）时留空 |
| `config` | `CorsConfig` | 跨域兜底配置（前端正常走网关同源代理，此项为直连场景兜底） |
| `security` | `LoginUser` | 登录用户模型（含 `userType` 与 `USER_TYPE_ADMIN/STORE/USER` 常量、`HEADER_USER_TYPE`）。**留在 common**，供域服务读身份做审计填充 |
| `util` | `UserContext` | 当前用户上下文（ThreadLocal）：`getUserId()` / `getUserType()`（类型缺失回退 `admin`）/ 角色权限等 |
| `feign` | `InternalApiErrorDecoder` | 把下游非 2xx 的 `{code,msg}` 还原为 `ServiceException` |
| `goods.api` / `store.api` | `GoodsCenterClient` / `StoreClient` + `*FeignConfiguration` | BFF→域 的内部 Feign 客户端与同源 DTO/VO；出站透传 `X-User-Id`/`X-User-Type`（**不再带内部令牌**） |
| `enums` | `DeleteTypeEnum` / `ServiceExceptionEnums` | 删除标识枚举 / 通用异常码枚举 |

## 接入约定（供业务服务）

1. **依赖**：在服务 `pom.xml` 引入 `com.panoramic:common`（版本随父 POM）；需要鉴权的端 BFF 再额外引入 `com.panoramic:common-auth`。
2. **组件扫描**：common 的配置与异常处理位于 `com.panoramic.common` 包，服务启动类须 `@SpringBootApplication(scanBasePackages = "com.panoramic")` 才能生效。
3. **返回**：Controller 一律返回 `RespData`；业务校验失败抛 `ServiceException(400, "提示文案")`，无需自行 try/catch。**内部 Feign 接口不包 RespData**，直返业务类型。
4. **入参**：分页查询 DTO 继承 `BasePageVO`（自动获得分页校验）。
5. **实体**：数据表实体继承 `BaseEntity` + `@TableName` + Lombok `@Data`，主键自行声明 `@TableId(type = IdType.AUTO)`；审计列用 `VARCHAR(32)`。

## 版本管理

MyBatis-Plus 依赖版本集中在父 POM `backend/pom.xml` 的 `dependencyManagement`（`mybatis-plus.version=3.5.16`），common 与业务服务均免写版本号。

> 修改 common 后需执行 `mvn -pl common install` 并重启依赖它的服务才生效。

