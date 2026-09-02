# common — 公共工具包

后端公共工具模块，**非独立服务、不参与启动**，作为依赖被各业务服务引用（当前由 `goods-center` 使用）。基包 `com.panoramic.common`。

## 功能清单

| 包 | 组件 | 用途 |
|---|---|---|
| `vo` | `RespData<T>` | 统一接口返回结构 `{code, msg, data}`，成功 `code=200`；提供 `success/error` 静态工厂 |
| `vo` | `BasePageVO` | 分页查询参数基类：`pageNum/pageSize` 自带 `@NotNull/@Min/@Max` 校验 |
| `vo` | `BaseEntity` | 实体基类：`createUser/createTime/updateUser/updateTime/isDelete`；`isDelete` 标注 `@TableLogic` 逻辑删除 |
| `exception` | `ServiceException` | 业务异常，支持枚举或自定义 `(code, message)` 构造；业务校验失败统一 `code=400` |
| `exception` | `GlobalExceptionHandler` | `@RestControllerAdvice` 全局异常兜底：业务异常、参数校验、404/403、JSON 解析错误、未知异常 → 统一 `RespData` |
| `valid` | `ValidationGroups` | 校验分组常量 `Create/Update/Delete`，用于 @RequestBody 按场景分组校验 |
| `config` | `MybatisPlusConfig` | 分页插件（PaginationInnerInterceptor） |
| `config` | `MyMetaObjectHandler` | `insertFill/updateFill` 自动填充 `createTime/createUser/updateTime/updateUser`（操作人取 `UserContext`，未登录时为空） |
| `config` | `CorsConfig` | 跨域兜底配置（前端正常走网关同源代理，此项为直连场景兜底） |
| `util` | `UserContext` | 当前用户上下文（ThreadLocal 占位，权限体系接入前无来源） |
| `enums` | `DeleteTypeEnum` / `ServiceExceptionEnums` | 删除标识枚举 / 通用异常码枚举 |

## 接入约定（供业务服务）

1. **依赖**：在服务 `pom.xml` 引入 `com.panoramic:common`（版本随父 POM）。
2. **组件扫描**：common 的配置与异常处理位于 `com.panoramic.common` 包，服务启动类须 `@SpringBootApplication(scanBasePackages = "com.panoramic")` 才能生效。
3. **返回**：Controller 一律返回 `RespData`；业务校验失败抛 `ServiceException(400, "提示文案")`，无需自行 try/catch。
4. **入参**：分页查询 DTO 继承 `BasePageVO`（自动获得分页校验）。
5. **实体**：数据表实体继承 `BaseEntity` + `@TableName` + Lombok `@Data`，主键自行声明 `@TableId(type = IdType.AUTO)`。

## 版本管理

MyBatis-Plus 依赖版本集中在父 POM `backend/pom.xml` 的 `dependencyManagement`（`mybatis-plus.version=3.5.16`），common 与业务服务均免写版本号。

> 修改 common 后需执行 `mvn -pl common install` 并重启依赖它的服务才生效。
