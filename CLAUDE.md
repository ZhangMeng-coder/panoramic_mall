# 项目约定（Panoramic Mall）

## 代码生成与分层约定

生成/修改 Java 服务层代码时，一律遵循以下规则（admin 与 goods-center 均已按此重构）：

- **CRUD 交给 MyBatis-Plus 基类**：每个实体有独立 service，接口 `extends IService<T>`、实现 `extends ServiceImpl<XxxMapper, Xxx>`。own-entity 的增删改查（`save`/`updateById`/`removeById`/`getById`/`list`/`page`/`count` 等）直接用基类内置方法，能用组件实现就不新写逻辑、不直接用 own Mapper。纯关联表（如 `sys_user_role`、`sys_role_permission`）也要建 service。
- **审计字段交给自动填充，禁止手写赋值**：所有业务实体一律 `extends BaseEntity`，`create_user/update_user/create_time/update_time` 由 common 的 `MyMetaObjectHandler` 经 `UserContext` 自动填充，代码**不得**显式赋值（不得出现 `setCreateUser/setUpdateUser/setCreateTime/setUpdateTime`），也不得绕过 `save/updateById` 等触发填充的 MP 基类方法。纯关联表（无审计列、物理删除，如 `sys_user_role`/`sys_role_permission`）除外，维持现状。
  - **审计值格式为 `UserType:UserId` 字符串**（如 `admin:1` / `store:7`）：多端身份空间（admin/store/user）下同一 id 可能指向不同的人，带类型前缀消歧。故 `BaseEntity.createUser/updateUser` 是 **`String`**，数据库列为 `VARCHAR(32)`（不是 INT）；取不到 userId 时留空，类型缺失时由 `UserContext.getUserType()` 回退 `admin`。
- **跨实体只走 owner service**：Service 内禁止直接持有/调用其他实体的 Mapper；要读写别的实体，必须调用该实体自己的 service（对方缺能力时先在对方 service 上加方法再回来调）。
- **内容归其所属实体的 controller**：不局限在实体 controller 里查询无关内容——要查什么内容，就调什么实体的 controller/service。例：不要在 RoleController/RoleService 里查 User 相关内容。
- **数据验证层的循环引用**用 Spring `@Lazy` 断环（需在模块 `lombok.config` 加 `lombok.copyableAnnotations += org.springframework.context.annotation.Lazy` 使其进入构造参数）。
- **歧义先问**：规则适用或归属有歧义时，先向用户确认，不要自己判断。

## 分层与内部服务调用（BFF 化，进行中）

目标分层：**前端页面只经网关访问"端 BFF"**（admin / store-bff / mall-bff）；**业务域服务不向页面暴露公网路由，只由 BFF 经 Feign 内部调用**。生成/修改代码时按此归属：页面聚合/编排 → BFF；数据归属与领域能力 → 域服务；不得把实体/表复制进 BFF。⚠ 网关公网入口已收敛为**端 BFF 白名单**（`gateway` 配置 `panoramic.gateway.bff-services`，由 `BffRouteGuardFilter` 强制校验，名单外服务经网关一律 403）：当前为 **`admin` 与 `store-bff`**（store 域已拆出 store-bff 下沉纯域、不再对外）；goods-center 同样已下沉纯域、不开放公网路由。mall-bff、trade-center 等仍属后续待建项（todo.md）。新代码一律按目标分层写，不延续直连、不给域服务开公网路由。

**模块归属（鉴权装配层已独立成模块）**：`common`=纯基座（`RespData`/`BaseEntity`/异常/分页/`LoginUser`/`UserContext`/`MyMetaObjectHandler`/Feign 契约）；`common-auth`=鉴权装配层（`SecurityConfig`/`AuthTokenFilter`/`JwtService`/`LoginUserCacheService`，带 Redis 与 JJWT）。**只有端 BFF 依赖 `common-auth`**；业务域（goods-center / store）只依赖 `common`，结构上拿不到认证链与 Redis，因此不装配鉴权、不需要 `datasource-redis.yml` / `auth.yml`。新增需要鉴权/Redis 的公共类，放 `common-auth`；只被业务代码共用的放 `common`。

**Feign 内部接口规约（BFF → 域，M0 起一律遵守）**：
- **熔断**：经 Feign 调业务域必须配熔断器，下游故障不得拖垮调用方（编排接口降级/快速失败）。
- **公共类型**：Feign interface 的入参/出参 DTO 在 common 维护（与接口同源），调用方与被调用方引用**同一份类型**，禁止各自复制一份导致漂移。
- **不包 RespData**：内部 Feign 方法**直接返回业务结果类型**（`Xxx`/`List<Xxx>`/`boolean`…），错误走异常/统一处理传播；RespData（`{code,msg,data}`）仅用于对外页面/网关接口。
- **信任与防线（鉴权与权限判定全部收敛在端 BFF）**：**域服务不做任何鉴权、不做任何权限判断、不校验 token**。内部调用只透传身份头 `X-User-Id` / `X-User-Type`（网关注入 → 端 BFF 经 Feign 原样转发），域服务把它直取填 `UserContext`，**仅用于两件事**：审计字段自动填充（`UserType:UserId`）与 `audit_by` 留痕——读 ≠ 判断，读身份不等于做鉴权。端 BFF 的 `@PreAuthorize` 是唯一授权点，其各操作权限串与域接口一一对应（goods:brand/category/spu 的 list/add/edit/delete）。⚠ **不再有内部令牌 `X-Internal-Token`**（已删除）：域端口只在内网可达是前提，否则可伪造 `X-User-Id`——防线在网络层，不在应用层。
  - **缺头即不填充、不拦截**：域内身份过滤器（`GoodsUserIdentityFilter` / `StoreUserIdentityFilter`）在缺 `X-User-Id` 时直接放行（审计留空），**不得回 401**——那等于在域内做鉴权。
- **store 域的数据权限（D5，已按新边界改写）**：store 采用 **store_id 通用数据权限适配**：域内不持 store_user，owner 侧方法带 `store_id` 参数、只作用于「id==store_id 的店」（**账号店同 ID**，D4，store_shop 主键==店主账号 id、无 owner_user_id 列，店主归属收敛在 store-bff）；platform 侧方法不带 store_id、全量。**owner/platform 的分流由「端 BFF 调哪一侧接口」决定，不由 `X-User-Type` 在域内判断**（`assertOwner`/`requirePlatformAdmin` 之类的域内断言已删除）；store-bff 从登录态取 store_id 传给域，admin 走 platform 侧并由 `@PreAuthorize` 把关。`audit_by` 直取 X-User-Id 仅记录，不与平台账号联查（D6）。新增 store 域方法时按「作用对象表是否带 store_id 列 + 调用意图」决定套 owner(限 store_id)/platform(全量) 哪一侧。
  - **store 域现有 owner 侧能力**：店铺 `store_shop`（mine/save/submit）与店主在售商品 `store_goods_spu`/`store_goods_sku`（`/internal/store/goods/**`，带 `storeId` 且按该列过滤；SKU 经 `spuId` 归属，不再单带 store_id）。**「店铺已审核通过」的门禁不在域内**（域不查店铺状态），由 store-bff 调域前判定并回 `403`。
  - **上下架是推导量、由域内单一写者维护**：`StoreGoodsSpuServiceImpl#refreshShelfStatus` 是 `SPU上架 ⟺ ≥1 SKU 上架` 的唯一写者（上架任一 SKU → SPU 上架；SKU 全下架 → SPU 下架），前端不得直接传 SPU 上下架；已上架 SKU 锁定其规格/价格（须先下架才能改/删），存在上架 SKU 时 SPU 规格配置只读、SPU 不可删除。
  - **中台版本比对属编排职责（store-bff 做）**：域只落库/回读 `center_version` 与商品字段，不调中台、不判版本；「更新提示 + 同步覆盖」（覆盖与否由店主决定、不阻断保存）在 store-bff 详情编排里组装。

**鉴权与登录态（各端各管各的）**：每个端 BFF 只提供**自己身份**的登录接口（admin → `/auth/login` 签 `type=admin`；store-bff → `/auth/register|login` 签 `type=store`；mall-bff 待建，`type=user`）。三端共用同一把 `jwt-secret` 与同一套 `type` claim 契约（`LoginUser.CLAIM_USER_TYPE`），但**登录用户模型与 Redis 键命名空间按身份隔离**：

- **Redis 登录态键 = `panoramic:login:{userType}:{userId}`**（如 `panoramic:login:admin:1` / `panoramic:login:store:7` / `panoramic:login:user:42`）。前缀由 Nacos `auth.yml` 的 `panoramic.auth.redis-prefix`（= `panoramic:login`）提供，网关按 JWT 的 `type` claim 拼同一把键校验——**键格式是网关 ↔ 端 BFF 的共享契约，不可单边改动**。
- 网关只做「验签 + 查登录态 + 注入 `X-User-Id`/`X-User-Type`」，不做身份类型判断；身份类型由签发的 `type` claim 携带、全链路透传。
- 域服务不参与登录态（无 Redis），见上「信任与防线」。

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
