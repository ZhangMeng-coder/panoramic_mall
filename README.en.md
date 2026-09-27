# Panoramic Mall

`[简体中文](README.md) | English`

[![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.7-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Spring Cloud](https://img.shields.io/badge/Spring%20Cloud-2025.1.2-6DB33F?logo=spring&logoColor=white)](https://spring.io/projects/spring-cloud)
[![Nacos](https://img.shields.io/badge/Nacos-2025.1.0.0-1E9FFF)](https://nacos.io/)
[![MyBatis-Plus](https://img.shields.io/badge/MyBatis--Plus-3.5.16-red)](https://baomidou.com/)
[![MySQL](https://img.shields.io/badge/MySQL-8-4479A1?logo=mysql&logoColor=white)](https://www.mysql.com/)
[![Vue](https://img.shields.io/badge/Vue-3-4FC08D?logo=vuedotjs&logoColor=white)](https://vuejs.org/)
[![TypeScript](https://img.shields.io/badge/TypeScript-strict-3178C6?logo=typescript&logoColor=white)](https://www.typescriptlang.org/)
[![Vite](https://img.shields.io/badge/Vite-7-646CFF?logo=vite&logoColor=white)](https://vite.dev/)

**A three-role e-commerce project, end to end**: one Spring Cloud microservice backend (**BFF + downstream domains**) and three independent frontend apps (platform admin / merchant console / customer storefront).

Pages reach the gateway and talk only to **edge BFFs**; business domains expose no public routes and perform no authentication. **Interface contracts are written down before the implementation**, and a static checker verifies that contract tables, controllers and Feign clients stay in sync.

> ⚠ **This is a personal / portfolio-grade demo, not a production system — no real payment or SMS provider is wired in**: checkout uses a **mock payment** (it only asserts `amount == order total`), and the SMS verification code is a **mock fixed code**. Every trade-off here is ranked by *clarity of the demo and soundness of the engineering rules*, not by production completeness.
>
> 📖 The per-module READMEs and the contract tables are written in **Chinese**; this file mirrors [`README.md`](README.md).

## Table of contents

- [Highlights](#highlights)
- [Features](#features)
- [Architecture](#architecture)
- [Repository layout](#repository-layout)
- [Tech stack](#tech-stack)
- [Requirements](#requirements)
- [Quick start](#quick-start)
- [Documentation map](#documentation-map)
- [Known boundaries](#known-boundaries)

## Highlights

**① Layering that ends the argument: pages only see edge BFFs, domains are "dumb"**
The three BFFs (admin / store-bff / mall-bff) each own their account table and login state. The four business domains (goods-center / store / customer-center / trade-center) **expose no public routes** (enforced by a gateway whitelist — anything else gets a 403) and **structurally cannot reach the auth chain**: the security wiring lives in its own module, `common-auth`, which only BFFs depend on. So "domains never authenticate" is not a convention to remember — it is what the dependency direction gives you.

**② Contract-first, and the contracts are machine-checked**
[`docs/contracts/`](docs/contracts/README.md) registers **three layers of contracts** (page-level / internal Feign / cross-service implicit), together with [`drift-check.mjs`](docs/contracts/drift-check.mjs): it scans contract tables against controllers and Feign clients, comparing **path / HTTP method / permission string / row counts**, plus **reverse sentinels** so a deleted endpoint cannot quietly come back. Contract rows are marked "new" vs "modified" according to rules written down in [`CLAUDE.md`](CLAUDE.md).

**③ Data scope only ever comes from the session**
Anchors such as `customerId` / `storeId` are **read from the login state by the edge BFF and written into the domain request DTOs**; page-level DTOs have no such field (a value sent by the browser is ignored) — so data authorization never becomes the page's responsibility. The three identity spaces (platform admin / merchant / customer) are isolated: the JWT carries a `userType`, and Redis session keys are partitioned per side.

**④ The hardest flow (checkout) is treated as such**
Two-level idempotency (per-request `requestId` plus a fingerprint fallback window), a **claim-first key** recorded per submission, an order state machine with a status-trail table, stock deduction and rollback, and a **Seata global transaction** (`@GlobalTransactional` at the use-case entry points, with the store domain as a branch transaction).

**⑤ Exactly one cross-domain edge exists**
`trade-center → store` is **the only domain-to-domain call in the repository** (product snapshot plus stock deduction/rollback in the checkout pipeline). It is registered in [cross-cutting §24](docs/contracts/cross-cutting.md) and guarded by a compile-time sentinel. Every other cross-domain collaboration is orchestrated by an edge BFF.

**⑥ Every rule is written down once**
Window arithmetic and bucket merging for statistics endpoints live in `common` (`com.panoramic.common.stats`); domains accept only explicit `start` / `end` and always emit daily points. Audit columns (`create_user` / `create_time`, …) are filled automatically via `MyMetaObjectHandler` + `UserContext`, and business code is forbidden from setting them by hand. Conventions that *would not fail a build* if violated are all documented, each with its own "how to verify" note.

**⑦ One frontend shape across three apps, with type checking as the build gate**
Vue 3 + Vite + TypeScript (`strict`) + axios, three independent projects. `npm run build` is `vue-tsc --noEmit && vite build` — **a type error fails the build**, and loosening the gate (`any`, `@ts-ignore`, disabling `strict`) to make an error disappear is not allowed.

## Features

> Only the capability surface is listed here. Per-page behavior and field-level shapes live in the per-module READMEs and the [page contracts](docs/contracts/README.md).

| App | Capabilities |
|---|---|
| **Platform admin** ([frontend/admin](frontend/admin/) · [backend/admin](backend/admin/)) | Login + RBAC (users / roles / permissions / menus); standard product catalog (categories / brands / SPU-SKU); **shop review** and **cross-shop product management** (query / detail / lock & unlock, platform side); order management (**read-only**, global scope); **home dashboard** (users / shops / products / revenue / orders & conversion / two time series) |
| **Merchant console** ([frontend/store](frontend/store/) · [backend/store-bff](backend/store-bff/)) | Merchant registration & login; shop profile and review status; on-sale products and SKUs (shelf toggling, catalog version comparison); stock management (low-stock filter); orders (list / detail / ship); reviews (list / star filter / reply); **home dashboard** (on-shelf / off-shelf / abnormal stock + revenue / orders / review distribution / order series) |
| **Customer storefront** ([frontend/mall](frontend/mall/) · [backend/mall-bff](backend/mall-bff/)) | Phone-number login (mock SMS); product browsing (category tree / search / detail with ratings and reviews); cart; checkout (idempotency and per-shop order splitting) → payment → receipt → review; my orders, profile and shipping addresses |
| **Domains** | [goods-center](backend/goods-center/) catalog · [store](backend/store/) shops and on-sale products (review state machine, shelf-status derivation, lock read-only, stock, reviews) · [customer-center](backend/customer-center/) customer profiles and addresses · [trade-center](backend/trade-center/) cart and orders (DDD layering, capability-oriented, side-agnostic) |

## Architecture

```
┌──────────────────────────────────── Frontend layer ─────────────────────────────────────┐
│  admin (console :5173)        store (merchant :5174)        mall (storefront :5175)      │
└────────────┬─────────────────────────────────┬───────────────────────────────────────────┘
             │  /admin, /store, /mall, /discovery (Vite dev proxy → gateway 8080)
┌────────────▼─────────────────────────────────▼───────────────────────────────────────────┐
│  gateway  API gateway (:8080, Spring Cloud Gateway / WebFlux)                            │
│    public entry = BFF whitelist (BffRouteGuardFilter; domain services always 403)        │
│      /admin/** ─▶ lb://admin        /store/** ─▶ lb://store-bff                          │
│      /mall/**  ─▶ lb://mall-bff     /discovery/** health probe (not routed)              │
└───────┬─────────────────────────────────────────┬────────────────────────────────────────┘
       │ Nacos discovery (:8848)                 │ internal Feign (X-User-Id/X-User-Type + circuit breaker)
┌───────────────────────────────────────────┐  ┌───────────────────────────────────────────┐
│  Edge BFF layer (account tables, JWT w/  │  │  Pure domains (no auth, no public routes)  │
│  userType)                                │  │  goods-center (:8081) categories/brands    │
│  admin (:8082) platform account + RBAC    │  │  store (:8083) store_shop + on-sale goods  │
│  store-bff (:8084) merchant account       │  │  customer-center (:8086) profiles/addresses│
│  mall-bff (:8085) customer account        │  │  trade-center (:8087) cart / orders        │
│  identities isolated, never call each     │  │                                            │
│  other                                    │  │                                            │
└───────────────────────────────────────────┘  └───────────────────────────────────────────┘
        MySQL 8 (database panoramic_mall: goods_* / sys_* / store_* / mall_* / customer_* / trade_*)
```

`common` is the shared base every service depends on; the security wiring (JWT / Redis / filter chain) lives separately in `common-auth`, **depended on by edge BFFs only** — a business domain cannot even reach it, which is why domains do not authenticate and never touch login state. (⚠ `trade-center` is the only domain loading `datasource-redis.yml`, used solely for cart add-deduplication and count caching; MySQL remains the single source of truth — see [cross-cutting §12](docs/contracts/cross-cutting.md).)

Domains and edge BFFs talk to each other through **each domain's own `<domain>-interface` contract module** (Feign client + DTO/VO under `com.panoramic.contract.<domain>`), passing only identity headers plus circuit-breaker fallbacks; no permission checks happen inside a domain.

⚠ **The storefront "popular products" list on the home page is still static mock data.**

## Repository layout

| Path | Description | Docs |
|---|---|---|
| [backend/](backend/) | Microservice backend (Maven multi-module) | [README](backend/README.md) |
| ├── [common/](backend/common/) | Shared base (not a service): response envelope, base entities, exceptions, paging, audit auto-fill, login-user model | [README](backend/common/README.md) |
| ├── [common-auth/](backend/common-auth/) | Auth wiring (not a service): JWT + Redis sessions + security filter chain; **depended on by BFFs only** | [README](backend/common-auth/README.md) |
| ├── [gateway/](backend/gateway/) | API gateway (8080): routing, prefix stripping, auth pass-through, BFF whitelist | [README](backend/gateway/README.md) |
| ├── [goods-center/](backend/goods-center/) | Catalog domain (8081, pure domain) | [README](backend/goods-center/README.md) |
| ├── [store/](backend/store/) | Shop domain (8083, pure domain): shops + review state machine + on-sale products + product reviews | [README](backend/store/README.md) |
| ├── [customer-center/](backend/customer-center/) | Customer domain (8086, pure domain): profiles + addresses | [README](backend/customer-center/README.md) |
| ├── [trade-center/](backend/trade-center/) | Trade domain (8087, pure domain): cart + orders (DDD layering) | [README](backend/trade-center/README.md) |
| ├── [store-bff/](backend/store-bff/) | Merchant BFF (8084): shop profile, products, orders (list / detail / ship), reviews (list / filter / reply), home dashboard | [README](backend/store-bff/README.md) |
| ├── [mall-bff/](backend/mall-bff/) | Storefront BFF (8085): customer account (phone + mock SMS code) + browsing / cart / order orchestration | [README](backend/mall-bff/README.md) |
| ├── [admin/](backend/admin/) | Platform admin (8082, BFF): login + RBAC + shop review + cross-shop products + read-only orders + dashboard | [README](backend/admin/README.md) |
| └── [nacos-config/](backend/nacos-config/) | **Source of truth for Nacos shared config** (datasource / Redis / auth / circuit breaker / Seata), published manually | [README](backend/nacos-config/README.md) |
| [frontend/](frontend/) | Frontends (one project per app; Vue 3 + Vite + TypeScript + axios across all three) | [README](frontend/README.md) |
| ├── [admin/](frontend/admin/) | Platform console (5173) | [README](frontend/admin/README.md) |
| ├── [store/](frontend/store/) | Merchant console (5174) | [README](frontend/store/README.md) |
| └── [mall/](frontend/mall/) | Customer storefront (5175) | [README](frontend/mall/README.md) |
| [docs/contracts/](docs/contracts/) | **Interface contract registry** (page / internal Feign / cross-service) + static drift checker | [README](docs/contracts/README.md) |

## Tech stack

- **Backend**: Java 21 · Spring Boot 4.0.7 · Spring Cloud 2025.1.2 · Spring Cloud Alibaba 2025.1.0.0 · Nacos · MyBatis-Plus 3.5.16 · MySQL 8 · Seata (AT mode, checkout flow only) · Lombok
- **Frontend**: Vue 3 · Vite 7 · Vue Router 4 · **TypeScript (`strict`, all three apps)** · Element Plus (console / merchant) · **ECharts** (both dashboards) · **Axios** (single `src/api/request.ts` entry point per app)
  - The three projects are independent but share conventions and gates (type check wired into the build, identical `tsconfig` baseline, `any` banned) — see [frontend/README.md](frontend/README.md)
  - Console and merchant console share one design-token set; the storefront uses **a different one** (promotional orange/red)
- **Page-facing responses are wrapped in `RespData{code,msg,data}`; internal domain responses are not** — see [cross-cutting §1 / §2](docs/contracts/cross-cutting.md)

## Requirements

| Component | Address | Notes |
|---|---|---|
| JDK | 21 | Build & run |
| Maven | 3.9+ | Backend build |
| Node.js | ≥ 20.19 | Frontend build (Vite 7) |
| Nacos | `127.0.0.1:8848` (nacos/nacos) | Discovery + **shared config** (five data-ids, source files in [backend/nacos-config/](backend/nacos-config/); `spring.config.import` has no `optional:` — a missing data-id fails startup) |
| MySQL | Connection comes from the Nacos shared config `datasource-mysql.yml` | Database `panoramic_mall`; host/port/user/password can be overridden via `MYSQL_HOST/MYSQL_PORT/MYSQL_USERNAME/MYSQL_PASSWORD/MYSQL_DB` |
| Redis | `localhost:6379` | Session cache + cart deduplication |
| Seata TC | `127.0.0.1:8091` (direct grouplist) | Loaded by **`trade-center` and `store` only**; set `seata.enabled: false` to run them without global transactions |

## Quick start

```bash
# 0. Prerequisites: JDK 21 / Maven 3.9+ / Node >= 20.19; Nacos and MySQL reachable,
#    with the five data-ids from backend/nacos-config/ published to the Nacos console

# 1. Backend: install parent POM + utility modules first, then start services in dependency order
cd backend
mvn -N install
mvn -pl common,common-auth,goods-center-interface,store-interface,customer-center-interface,trade-center-interface install
mvn -pl gateway spring-boot:run          # gateway 8080 (plus goods-center/admin/store/store-bff/mall-bff/customer-center/trade-center)

# 2. Frontend: each app installs and starts independently (Vite dev proxy → gateway 8080)
cd ../frontend/admin && npm install && npm run dev   # → http://localhost:5173
```

> 📖 The full recipe (schema scripts, service start order, config overrides) lives in [backend/README.md](backend/README.md), the Nacos publishing steps in [backend/nacos-config/README.md](backend/nacos-config/README.md), and the frontend scripts in [frontend/README.md](frontend/README.md) — deliberately not duplicated here.

## Documentation map

| What you need | Where to look |
|---|---|
| Path / shape / permission string of a given endpoint | [`docs/contracts/`](docs/contracts/README.md) — the single source of truth for page contracts |
| Cross-service implicit rules (envelope, error mapping, data scope, circuit breaking, statistics windows…) | [cross-cutting.md](docs/contracts/cross-cutting.md) — each rule carries *why / where defined / cost of breaking it / how to verify* |
| Which tables a service owns, what it refuses to do | Per-module README, sections "architecture position / entity marks / responsibilities & boundaries" |
| Development workflow and verification gates (requirement → contract → implementation) | [`CLAUDE.md`](CLAUDE.md) (Chinese) |

## Known boundaries

- **No real providers**: mock payment (amount must equal the order total), mock fixed SMS code, no third-party login, no carrier/logistics integration.
- **The storefront home-page "popular products" list is still static mock data**; every other customer-facing page talks to mall-bff.
- **Deployment is single-host** (Nacos / MySQL / Redis / Seata TC / gateway / three frontends on one machine); no containerization, no multi-instance setup.
- **No CI pipeline yet**: the gates run locally — backend compiles, frontend runs `vue-tsc --noEmit && vite build`, and `node docs/contracts/drift-check.mjs` checks for contract drift before a commit.
- The platform admin treats orders as **read-only**; refunds are always **full refunds** — there is no partial-refund concept.
- Some deliberate divergences are documented on purpose (e.g. the merchant dashboard's "abnormal stock" is *not* the same predicate as the stock page's "low stock only"). Read the relevant README before "fixing" one of them.
