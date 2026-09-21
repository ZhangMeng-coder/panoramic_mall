# common — 公共基座

后端公共基座模块，**非独立服务、不参与启动**，作为依赖被**所有服务**引用（业务域 goods-center / store / customer-center，端 BFF admin / store-bff / mall-bff）。基包 `com.panoramic.common`。

> ⚠ **本模块刻意不引入 Redis / JJWT**：鉴权装配类已下移到 [common-auth](../common-auth/)，**只有端 BFF 依赖它**。业务域只依赖 common，结构上拿不到认证链与登录态——这是「域服务不鉴权」的模块级保证。

## 功能清单

| 包 | 组件 | 用途 |
|---|---|---|
| `vo` | `RespData<T>` | 统一接口返回结构 `{code, msg, data}`，成功 `code=200`；提供 `success/error` 静态工厂 |
| `vo` | `BasePageVO` | 分页查询参数基类：`pageNum/pageSize` 自带 `@NotNull/@Min/@Max` 校验 |
| `vo` | `BaseEntity` | 实体基类：`createUser/createTime/updateUser/updateTime/isDelete`；`isDelete` 标注 `@TableLogic` 逻辑删除。审计列的形状与取值格式见 `CLAUDE.md`「代码生成与分层约定」（操作人为 `String`，对应列 `VARCHAR(32)`） |
| `exception` | `ServiceException` | 业务异常，支持枚举或自定义 `(code, message)` 构造；业务校验失败统一 `code=400` |
| `exception` | `GlobalExceptionHandler` | `@RestControllerAdvice` 全局异常兜底：业务异常、参数校验、404/403、JSON 解析错误、未知异常 → 统一 `RespData` |
| `valid` | `ValidationGroups` | 校验分组常量 `Create/Update/Delete`，用于 @RequestBody 按场景分组校验 |
| `config` | `MybatisPlusConfig` | 分页插件（PaginationInnerInterceptor） |
| `config` | `MyMetaObjectHandler` | `insertFill/updateFill` 自动填充 `createTime/createUser/updateTime/updateUser`；操作人取 `UserContext` 拼为 `{userType}:{userId}`，未登录（无 userId）时留空 |
| `config` | `CorsConfig` | 跨域兜底配置（前端正常走网关同源代理，此项为直连场景兜底） |
| `security` | `LoginUser` | 登录用户模型（含 `userType` 与 `USER_TYPE_ADMIN/STORE/USER` 常量、`HEADER_USER_TYPE`）。**留在 common**，供域服务读身份做审计填充 |
| `util` | `UserContext` | 当前用户上下文（ThreadLocal）：`getUserId()` / `getUserType()`（类型缺失回退 `admin`）/ 角色权限等 |
| `util` | `HtmlSanitizer` | 富文本消毒：把店主录入的商品详情等**不可信 HTML** 洗成可安全渲染的 HTML。**消毒点在消费端 BFF 的出口**（域只原样存取、前端不得各引一套），各端 BFF 共用本类这一份白名单（见 cross-cutting 第 21 条） |
| `feign` | `InternalApiErrorDecoder` | 把下游非 2xx 的 `{code,msg}` 还原为 `ServiceException`（按 HTTP 状态码分野 4xx/5xx） |
| `feign` | `BffFeignCall` | 端 BFF 调域的统一执行器：沿 cause 链剥出下游业务异常、其余降级为「…暂不可用」；4xx 原样透传（见 cross-cutting 第 13 条） |
| `enums` | `DeleteTypeEnum` / `ServiceExceptionEnums` | 删除标识枚举 / 通用异常码枚举 |

> ⚠ **各域的 Feign 客户端与同源 DTO/VO 不在本模块**（2026-09-19 拆出）：`GoodsCenterClient` + `contract.goods.*`
> 在 [goods-center-interface](../goods-center-interface/)；`StoreClient` + `contract.store.*` 在 [store-interface](../store-interface/)；
> `CustomerCenterClient` + `contract.customer.*` 在 [customer-center-interface](../customer-center-interface/)。
> 本模块**不含任何域契约类型**——这正是为了「引用别域类型必然编译失败」。

## 接入约定（供业务服务）

1. **依赖**：在服务 `pom.xml` 引入 `com.panoramic:common`（版本随父 POM）；需要鉴权的端 BFF 再额外引入 `com.panoramic:common-auth`。
2. **组件扫描**：common 的配置与异常处理位于 `com.panoramic.common` 包，服务启动类须 `@SpringBootApplication(scanBasePackages = "com.panoramic")` 才能生效。
3. **返回**：Controller 一律返回 `RespData`；业务校验失败抛 `ServiceException(400, "提示文案")`，无需自行 try/catch。**内部 Feign 接口不包 RespData**，直返业务类型。
4. **入参**：分页查询 DTO 继承 `BasePageVO`（自动获得分页校验）。
5. **实体**：数据表实体继承 `BaseEntity` + `@TableName` + Lombok `@Data`，主键自行声明 `@TableId(type = IdType.AUTO)`；审计列一律交给自动填充，代码内不得显式赋值。
6. **调域**：端 BFF 出站调域统一包 `feign/BffFeignCall`（业务 4xx 透传、其余降级），不各 BFF 各写一份。

## 版本管理

MyBatis-Plus 依赖版本集中在父 POM `backend/pom.xml` 的 `dependencyManagement`（`mybatis-plus.version=3.5.16`），common 与业务服务均免写版本号。

> 修改 common 后需执行 `mvn -pl common install` 并重启依赖它的服务才生效。
