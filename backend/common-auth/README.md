# common-auth — 鉴权装配层

**非独立服务、不参与启动**，只作为依赖被**端 BFF** 引用（admin / store-bff / 未来的 mall-bff）。基包 `com.panoramic.common.auth`。

## 为什么单独一个模块

域服务（goods-center / store）的启动类是 `@SpringBootApplication(scanBasePackages = "com.panoramic")`，会把 common 里带 `@Configuration`/`@Service` 的类一并装配。若认证链（`SecurityConfig`、`AuthTokenFilter`、`LoginUserCacheService`）留在 common，**域服务就会被强制装配**——于是被迫依赖 Redis（`datasource-redis.yml`）与 `jwt-secret`（`auth.yml`），哪怕它一个 Redis 命令都不发。

把它们移到 `common-auth`、并让**只有端 BFF 依赖本模块**，域服务只依赖 `common`，就从**模块依赖关系**上保证拿不到认证链与 Redis——比"配置排除/组件扫描排除"更难被后续改动静默破坏。

## 功能清单

| 组件 | 用途 |
|---|---|
| `JwtService` | JWT 签发（端 BFF 登录）与解析（`AuthTokenFilter` 兜底）：subject=userId，claim `type`=userType |
| `LoginUserCacheService` | 登录用户快照的 Redis 读写。**键 = `panoramic:login:{userType}:{userId}`**（如 `panoramic:login:admin:1`）；删除即强制下线 |
| `AuthTokenFilter` | 从网关注入的 `X-User-Id` / `X-User-Type`（或兜底 Bearer JWT）取身份，按同一把键从 Redis 重建登录用户，填 `UserContext` 与方法安全主体 |
| `SecurityConfig` | 无状态安全链 + `@EnableMethodSecurity`：白名单（登录接口）放行、其余要求认证；401/403 用统一 body 写回。同时提供 `PasswordEncoder`(BCrypt) |

## 接入约定（供端 BFF）

1. **依赖**：`pom.xml` 同时引入 `com.panoramic:common` 与 `com.panoramic:common-auth`（版本随父 POM）。
2. **Nacos 共享配置**：需引入 `datasource-redis.yml` 与 `auth.yml`（`jwt-secret` / `jwt-expire-seconds` / `redis-prefix` / `header-name`）。
3. **登录**：本端只提供**自己身份**的登录接口，签发时 `userType` 与 JWT `type` claim 必须一致（`generateToken(id, USER_TYPE_XXX)` 与 `LoginUser.setUserType(USER_TYPE_XXX)` 成对出现），否则网关按 `type` 拼键会查不到登录态。
4. **密钥同源**：`jwt-secret` 由端 BFF 与 gateway 共享（Nacos `auth.yml`），不可单边改动。

## 各端身份空间

| 端 | userType | Redis 键样例 | 状态 |
|---|---|---|---|
| admin | `admin` | `panoramic:login:admin:1` | 已有 |
| store-bff | `store` | `panoramic:login:store:7` | 已有 |
| mall-bff | `user` | `panoramic:login:user:42` | 待建（常量 `LoginUser.USER_TYPE_USER` 已预留） |

> 修改本模块后需执行 `mvn -pl common-auth install` 并重启依赖它的端 BFF 才生效。
