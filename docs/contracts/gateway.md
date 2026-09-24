<!-- contract-meta
service: gateway
layer: gateway
config: backend/gateway/src/main/resources/application.yml
-->

# 网关契约（基础设施层）

> 网关（8080）是全部公网流量的唯一入口。它本身几乎没有业务接口，但它持有**三类契约**：
> 路由、BFF 白名单、鉴权白名单。这三类都是"改了不会编译报错、只会 403 或裸露"的东西。

## 一、路由

配置位置：`gateway/src/main/resources/application.yml:31-51`（仅有 3 条路由，无其他）

| route id | 断言路径 | 转发目标 | 过滤器 |
|---|---|---|---|
| `admin-route` | `Path=/admin/**` | `lb://admin` | `StripPrefix=1` |
| `store-bff-route` | `Path=/store/**` | `lb://store-bff` | `StripPrefix=1` |
| `mall-bff-route` | `Path=/mall/**` | `lb://mall-bff` | `StripPrefix=1` |

⚠ **域服务没有路由**（商品域 `/goods/**` 的历史路由已随下沉移除）。新增端 BFF 时才加路由，
且必须同步登记进下面的白名单，否则流量进不来。

⚠ `StripPrefix=1` 意味着**网关侧路径带前缀、服务侧路径不带**。
这条差异贯穿整个白名单体系（见第三节），也是"两处白名单写法不同"的根因。

## 二、BFF 白名单（公网可达性）

| 项 | 值 | 位置 |
|---|---|---|
| 配置键 | `panoramic.gateway.bff-services` | `application.yml:65` |
| 当前值 | `admin,store-bff,mall-bff`（逗号分隔，**无空格**） | 同上 |
| 强制者 | `BffRouteGuardFilter` | `gateway/filter/BffRouteGuardFilter.java` |

> `BffRouteGuardFilter` 的判定语义（改动前务必读完）见
> [`backend/gateway/README.md`](../../backend/gateway/README.md) 的「`BffRouteGuardFilter` 判定语义」。

## 三、鉴权白名单（**两处各写一份**）

免鉴权路径必须**同时**登记在网关侧与服务本地侧。**网关侧带前缀、服务侧不带**（因为 `StripPrefix=1`）。

### 网关侧

`gateway/src/main/resources/application.yml:67` → `panoramic.auth.whitelist-paths`：

| 路径 | 说明 |
|---|---|
| `/admin/auth/login` | 平台管理登录 |
| `/store/auth/login` | 店主登录 |
| `/store/auth/register` | 店主注册（注册即登录） |
| `/mall/auth/login` | 顾客登录（手机号 + 验证码） |
| `/mall/auth/register` | 顾客注册（注册即登录） |
| `/mall/auth/sms-code` | 顾客取短信验证码（**在登录之前被调用**，漏登记则取码按钮直接 401） |
| `/mall/catalog/categories` | C 端**首页宫格的分类树**——首页公开、不要求登录，故免鉴权。⚠ **精确路径，不是 `/mall/catalog/**`**：商品分页 / 筛选 / 详情一律要登录态（「首页免登录，一涉及商品查询与详情就鉴权」，见 [cross-cutting.md](./cross-cutting.md) 第 11 条） |
| `/discovery/**` | 服务发现探活 |

### 服务本地侧

各服务 `application.yml` 的 `panoramic.auth.whitelist-paths`（**服务侧不带前缀**）——条目直接看
配置文件，抄一份到这里只会漂移。

⚠ 只改一处 → 要么登录接口被拦（登不进去），要么本应鉴权的接口裸露到公网。

## 四、探活接口

| 方法 | 路径 | 权限串 | 入参 | 出参 | 声明位置 |
|---|---|---|---|---|---|
| GET | /discovery/services | — | — | `Mono<List<ServiceInfo>>` | `DiscoveryController#services` |

> 这是网关唯一的 `@RestController`，`@RequestMapping("/discovery")`（:19）。
> 它**不在任何路由内**（无 `GATEWAY_ROUTE_ATTR`），所以 `BffRouteGuardFilter` 直接放行；靠第三节的白名单免鉴权。
> 返回 `ServiceInfo(String, List<String>)`（内部 record，非 `common` 类型）—— 唯一一个不遵守"页面级必包 `RespData`"的对外端点，属**探活/运维用途**，不是业务接口。
