# 项目约定（Panoramic Mall）

## 代码生成与分层约定

生成/修改 Java 服务层代码时，一律遵循以下规则（admin 与 goods-center 均已按此重构）：

- **CRUD 交给 MyBatis-Plus 基类**：每个实体有独立 service，接口 `extends IService<T>`、实现 `extends ServiceImpl<XxxMapper, Xxx>`。own-entity 的增删改查（`save`/`updateById`/`removeById`/`getById`/`list`/`page`/`count` 等）直接用基类内置方法，能用组件实现就不新写逻辑、不直接用 own Mapper。纯关联表（如 `sys_user_role`、`sys_role_permission`）也要建 service。
- **审计字段交给自动填充，禁止手写赋值**：所有业务实体一律 `extends BaseEntity`，`create_user/update_user/create_time/update_time` 由 common 的 `MyMetaObjectHandler` 经 `UserContext` 自动填充，代码**不得**显式赋值（不得出现 `setCreateUser/setUpdateUser/setCreateTime/setUpdateTime`），也不得绕过 `save/updateById` 等触发填充的 MP 基类方法。纯关联表（无审计列、物理删除，如 `sys_user_role`/`sys_role_permission`）除外，维持现状。
- **跨实体只走 owner service**：Service 内禁止直接持有/调用其他实体的 Mapper；要读写别的实体，必须调用该实体自己的 service（对方缺能力时先在对方 service 上加方法再回来调）。
- **内容归其所属实体的 controller**：不局限在实体 controller 里查询无关内容——要查什么内容，就调什么实体的 controller/service。例：不要在 RoleController/RoleService 里查 User 相关内容。
- **数据验证层的循环引用**用 Spring `@Lazy` 断环（需在模块 `lombok.config` 加 `lombok.copyableAnnotations += org.springframework.context.annotation.Lazy` 使其进入构造参数）。
- **歧义先问**：规则适用或归属有歧义时，先向用户确认，不要自己判断。

## 代码验证只到“编译通过”

本仓库生成/修改代码后，验证一律**止步于编译通过**：

- ✅ **允许**：仅做编译级验证，例如 `mvn -q -N install`、`mvn -pl <模块> -am compile`、`mvn -pl <模块> compile`、`mvn test-compile` 等（确保能编译即可）。
- ❌ **禁止**：为验证而进行的任何**程序执行与接口测试**，包括但不限于：
  - 启动/重启服务去验证（Nacos、gateway、goods-center 等 `spring-boot:run`）；
  - 运行前端 dev/preview 去验证；
  - `mvn test`/接口测试/`curl` 打接口验证行为；
  - 任何把“跑起来看结果”当作验收手段的行为。

判断原则：不清楚某操作是否属于“执行/接口测试”时，默认**不做**，除非用户当场明确要求。

## 例外与边界

- 用户**明确要求**运行的验证（如“跑一下接口”“启动看效果”）不算违规——本规则约束的是**默认行为**，不替用户做决定。
- 数据库/数据类操作（例如用个人 skill `mysql-connect` 连远程库、同步/查询数据）属于数据处理，不属于“验证生成代码”，用户要求时照常执行；但**不得**把它当作验证本仓库刚生成代码的手段。
- 本规则针对代码产物的验证；其他与验证无关的必要操作（装依赖、编译缓存清理等）照常。
