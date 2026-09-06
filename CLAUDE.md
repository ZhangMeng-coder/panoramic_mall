# 项目约定（Panoramic Mall）

## 代码生成与分层约定

生成/修改 Java 服务层代码时，一律遵循以下规则（admin 与 goods-center 均已按此重构）：

- **CRUD 交给 MyBatis-Plus 基类**：每个实体有独立 service，接口 `extends IService<T>`、实现 `extends ServiceImpl<XxxMapper, Xxx>`。own-entity 的增删改查（`save`/`updateById`/`removeById`/`getById`/`list`/`page`/`count` 等）直接用基类内置方法，能用组件实现就不新写逻辑、不直接用 own Mapper。纯关联表（如 `sys_user_role`、`sys_role_permission`）也要建 service。
- **审计字段交给自动填充，禁止手写赋值**：所有业务实体一律 `extends BaseEntity`，`create_user/update_user/create_time/update_time` 由 common 的 `MyMetaObjectHandler` 经 `UserContext` 自动填充，代码**不得**显式赋值（不得出现 `setCreateUser/setUpdateUser/setCreateTime/setUpdateTime`），也不得绕过 `save/updateById` 等触发填充的 MP 基类方法。纯关联表（无审计列、物理删除，如 `sys_user_role`/`sys_role_permission`）除外，维持现状。
- **跨实体只走 owner service**：Service 内禁止直接持有/调用其他实体的 Mapper；要读写别的实体，必须调用该实体自己的 service（对方缺能力时先在对方 service 上加方法再回来调）。
- **内容归其所属实体的 controller**：不局限在实体 controller 里查询无关内容——要查什么内容，就调什么实体的 controller/service。例：不要在 RoleController/RoleService 里查 User 相关内容。
- **数据验证层的循环引用**用 Spring `@Lazy` 断环（需在模块 `lombok.config` 加 `lombok.copyableAnnotations += org.springframework.context.annotation.Lazy` 使其进入构造参数）。
- **歧义先问**：规则适用或归属有歧义时，先向用户确认，不要自己判断。

## 分层与内部服务调用（BFF 化，进行中）

目标分层（详见 `Architecture-BFF.md`，收口路线见 `todo.md`）：**前端页面只经网关访问"端 BFF"**（admin / store-bff / mall-bff）；**业务域服务不向页面暴露公网路由，只由 BFF 经 Feign 内部调用**。生成/修改代码时按此归属：页面聚合/编排 → BFF；数据归属与领域能力 → 域服务；不得把实体/表复制进 BFF。⚠ 网关公网入口已收敛为**端 BFF 白名单**（`gateway` 配置 `panoramic.gateway.bff-services`，由 `BffRouteGuardFilter` 强制校验，名单外服务经网关一律 403）：当前为 `admin` 与 `store-center`（后者暂兼店铺端后端角色，等价于店铺端 BFF；拆出 store-bff 后名单改指向 store-bff）；goods-center 已下沉纯域、不开放公网路由。store-bff 拆分、mall-bff 等仍属后续待迁项（todo.md）。新代码一律按目标分层写，不延续直连、不给域服务开公网路由。

**Feign 内部接口规约（BFF → 域，M0 起一律遵守）**：
- **熔断**：经 Feign 调业务域必须配熔断器，下游故障不得拖垮调用方（编排接口降级/快速失败）。
- **公共类型**：Feign interface 的入参/出参 DTO 在 common 维护（与接口同源），调用方与被调用方引用**同一份类型**，禁止各自复制一份导致漂移。
- **不包 RespData**：内部 Feign 方法**直接返回业务结果类型**（`Xxx`/`List<Xxx>`/`boolean`…），错误走异常/统一处理传播；RespData（`{code,msg,data}`）仅用于对外页面/网关接口。
- **信任与防线（权限判定收敛在端 BFF）**：内部调用带信任头（主身份 + type + scope）。goods-center 信任内部令牌与 BFF 透传身份，**只负责执行 + 审计填充**（user_id 直取 X-User-Id 填 `UserContext`，不再打 Redis 重建登录用户），**不再做权限判定**——端 BFF 的 `@PreAuthorize` 是唯一授权点，其各操作权限串与域接口一一对应（goods:brand/category/spu 的 list/add/edit/delete）。⚠ 若未来出现带行级/店铺归属、确实需要域内范围判定的域（如 store），另行评估，勿照搬 goods-center 的“纯执行”模式。

**术语防呆（避免跨域加错表）**：`goods-center`=标准商品模板库（标准商品平台）；"店铺在售商品/库存/信誉"属 store 域；"顾客/购物车/订单/评价"属未来 trade 域。别把别域实体塞进 goods-center。

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

## 执行 todo.md 大改动前的数据库备份

- **凡按 `todo.md` 执行一次“大的项目改动”**（例如新增/重构服务、改表结构、灌权限种子、动业务库数据等，会改变当前库状态的操作），**开工前先对当前数据库做一次备份**。
- 备份方式：经个人 skill `mysql-connect` 连库导出全量备份（或库级 dump），备份产物存到仓库内/临时目录并记下文件位置与时间点，改动出问题可回滚。
- 备份属于数据操作（用户已授权直接操作数据库），不属于“验证代码”范畴。
- 无法确认某改动是否算“大改动”时，先与用户确认是否需要备份。
