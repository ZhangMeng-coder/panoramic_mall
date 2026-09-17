# mall 前台商品搜索与分类浏览 · 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 mall 前台首页的搜索框与分类宫格接真实数据，新增搜索页与分类商品页（含分类/品牌筛选器、分页、价格排序）。

**Architecture:** 页面 → 网关 `/mall/**` → mall-bff（新增 catalog 编排层，首次启用 Feign）→ goods-center（分类树）+ store 域（通用化后的跨店商品分页 + 新增 facets 聚合）。C 端展示口径（已审核店铺 + 上架 + 未锁定）由 **BFF 固定传参**，域侧不含隐含约束；域返回的 VO 由 BFF 裁成 C 端自己的形状再输出。

**Tech Stack:** Java 17 / Spring Boot / MyBatis-Plus / Spring Cloud OpenFeign + Resilience4j / Nacos；Vue 3 + Vite + TypeScript(strict) + axios。

**Spec:** `docs/superpowers/specs/2026-09-17-mall-catalog-search-browse-design.md`

## Global Constraints

以下约束适用于**每一个**任务，逐字取自项目 `CLAUDE.md`，不得变通：

- **验证只到编译通过**：允许 `mvn -pl <模块> -am compile`、`mvn -q -N install`、`npm run build`、`npm run type-check`。**禁止**启动服务、跑 dev/preview、`mvn test`、`curl` 打接口——任何"跑起来看结果"的验收手段都不许用。
- **本仓库零测试基建**（`backend` 下无任何 `src/test`）。计划中**不写单测**，任务的验收一律是编译/类型检查/文本核对/SQL 复核。
- **审计字段禁止手写赋值**：不得出现 `setCreateUser/setUpdateUser/setCreateTime/setUpdateTime`；不得绕过 `save/updateById` 等触发自动填充的 MP 基类方法。
- **跨实体只走 owner service**：Service 内不得直接持有/调用其他实体的 Mapper；要读写别的实体，调用其自己的 service（缺能力就在对方 service 上加方法）。
- **域服务不做鉴权**：域内不得出现 `@PreAuthorize`、不得校验 token、不得读 `X-User-Type` 判权。端 BFF 的 `@PreAuthorize` 是唯一授权点；**mall-bff 不接 RBAC，其接口权限串一律为空**（这是预期状态，不是漏登记）。
- **内部 Feign 不包 `RespData`**，直接返回业务类型；**页面级接口必包 `RespData`**。
- **改任何对外接口，同一改动内更新 `docs/contracts/<服务>.md`**；改跨服务隐式约定必须更新 `docs/contracts/cross-cutting.md`。
- **提交前跑 `node docs/contracts/drift-check.mjs`**，退出码非 0 不得提交。
- **前端**：只写 `.ts` / `<script setup lang="ts">`；不许 `any` / `as any` / `@ts-ignore` / `@ts-nocheck`；不许放松 tsconfig。三端 `request.ts` 的 admin / store 两份逐字相同，mall 是自己的入口。
- **mall 前端视觉**：只消费 `frontend/mall/src/styles/tokens.css` 的令牌，**禁止硬编码色值/圆角/阴影**；容器固定 1280px，**不写媒体查询**。
- **提交信息**用中文，结尾带 `Co-Authored-By: Claude Code <noreply@anthropic.com>`。

---

## 文件结构总览

**新增（后端 common）**
- `backend/common/src/main/java/com/panoramic/common/store/dto/StoreGoodsSpuCrossShopPageQueryDTO.java`（由 `StoreGoodsSpuPlatformPageQueryDTO` 改名而来）
- `backend/common/src/main/java/com/panoramic/common/store/vo/StoreGoodsSpuCrossShopPageItemVO.java`（由 `StoreGoodsSpuPlatformPageItemVO` 改名而来）
- `backend/common/src/main/java/com/panoramic/common/store/dto/StoreGoodsSpuFacetQueryDTO.java`
- `backend/common/src/main/java/com/panoramic/common/store/vo/StoreGoodsSpuFacetVO.java`
- `backend/common/src/main/java/com/panoramic/common/store/vo/StoreGoodsFacetItemVO.java`

**新增（store 域）** — 无新文件，全部改既有 `StoreGoodsSpuService(+Impl)` / `GoodsController` / `StoreShopService(+Impl)` / `StoreGoodsSkuService(+Impl)`

**新增（mall-bff）**
- `controller/CatalogController.java`
- `service/CatalogBffService.java`
- `dto/MallGoodsPageQueryDTO.java`、`dto/MallFacetQueryDTO.java`
- `vo/MallGoodsItemVO.java`、`vo/MallFacetVO.java`、`vo/MallFacetItemVO.java`

**新增（前端 mall）**
- `src/types/api.ts`、`src/types/catalog.ts`
- `src/api/catalog.ts`
- `src/views/GoodsListView.vue`
- `src/components/FilterRow.vue`、`src/components/Pager.vue`
- `src/styles/catalog.css`

**删除**：`frontend/mall/src/mock/categories.ts`

---

## Task 1: 数据库全量备份（开工前置）

**Files:**
- Create: `temp/2026-09-17-catalog-backup/`（备份产物目录）

**Interfaces:**
- Produces: 一份可回滚的全量 dump，路径记录在最终汇报里。

- [ ] **Step 1: 确认 temp 目录状态**

```bash
cd /e/workspace/panoramic_mall && ls temp 2>/dev/null || echo "temp 不存在（上一轮已整体删除）"
grep -n "temp" .gitignore || echo "⚠ .gitignore 未忽略 temp/，需要先加"
```

- [ ] **Step 2: 若 `.gitignore` 未忽略 `temp/`，加上**

在 `.gitignore` 追加一行 `temp/`。

- [ ] **Step 3: 写备份脚本**

创建 `temp/2026-09-17-catalog-backup/dump.mjs`，复用 mysql-connect skill 自带的 mysql2：

```js
// ⚠ ESM 的 import 说明符不接受 Windows 绝对路径（会被当成 scheme "c:"），必须用 createRequire
import { createRequire } from 'node:module'
import { writeFileSync } from 'node:fs'
const require = createRequire(import.meta.url)
const mysql = require('C:/Users/lisi/.claude/skills/mysql-connect/node_modules/mysql2/promise.js')

const conn = await mysql.createConnection({
  host: process.env.MYSQL_HOST,
  port: Number(process.env.MYSQL_PORT || 3306),
  user: process.env.MYSQL_USERNAME,
  password: process.env.MYSQL_PASSWORD,
  database: process.env.MYSQL_DB || 'panoramic_mall',
  charset: 'utf8mb4'
})

const [tables] = await conn.query('SHOW TABLES')
const key = Object.keys(tables[0])[0]
const out = []
for (const row of tables) {
  const t = row[key]
  const [ddl] = await conn.query(`SHOW CREATE TABLE \`${t}\``)
  out.push(`DROP TABLE IF EXISTS \`${t}\`;\n${ddl[0]['Create Table']};\n`)
  const [rows] = await conn.query(`SELECT * FROM \`${t}\``)
  if (rows.length) {
    const cols = Object.keys(rows[0])
    const values = rows.map(r =>
      '(' + cols.map(c => (r[c] === null ? 'NULL' : conn.escape(r[c]))).join(',') + ')'
    )
    out.push(`INSERT INTO \`${t}\` (\`${cols.join('`,`')}\`) VALUES\n${values.join(',\n')};\n`)
  }
}
// 输出路径相对脚本所在目录（Step 4 会 cd 进该目录），不要写成仓库相对路径
writeFileSync('panoramic_mall-full.sql', out.join('\n'), 'utf8')
console.log('表数:', tables.length)
await conn.end()
```

- [ ] **Step 4: 执行备份**

凭据从 skill 的 `.env.local` 取（不要写进仓库）。在 `temp/2026-09-17-catalog-backup/` 下运行：

```bash
cd /e/workspace/panoramic_mall/temp/2026-09-17-catalog-backup
set -a; source "C:/Users/lisi/.claude/skills/mysql-connect/.env.local"; set +a
node dump.mjs && ls -la panoramic_mall-full.sql
```

Expected: 打印表数（应 ≥ 12），生成非空 SQL 文件。

- [ ] **Step 5: 记录基线行数（供终验比对）**

```bash
cd /e/workspace/panoramic_mall
node "C:/Users/lisi/.claude/skills/mysql-connect/scripts/query.mjs" --database panoramic_mall \
 "SELECT (SELECT COUNT(*) FROM store_shop) AS shop, (SELECT COUNT(*) FROM store_goods_spu WHERE is_delete=0) AS spu, (SELECT COUNT(*) FROM store_goods_sku WHERE is_delete=0) AS sku, (SELECT COUNT(*) FROM goods_category) AS cat, (SELECT COUNT(*) FROM mall_user) AS mall_user;"
```

把结果抄进本任务末尾（终验要对比 shop / spu / sku / cat 的增量是否为预期值）。

Expected（2026-09-17 实测基线）: `shop=2, spu=50, sku=216, cat=37, mall_user>=0`

> ⚠ 本任务**不提交**任何 `temp/` 内容。SQL 产物只在本地保留，用于回滚。

---

## Task 2: DDL 加列 + 存量回填 + 分类图标种子

**Files:**
- Modify: `backend/store/src/main/resources/db/schema.sql`（`store_goods_spu` 建表语句加列）
- Modify: `backend/goods-center/src/main/resources/db/schema.sql`（`goods_category` 建表语句加列）
- 远程库：`store_goods_spu.min_price`、`goods_category.icon`

**Interfaces:**
- Produces: 列 `store_goods_spu.min_price DECIMAL(10,2) NULL`、`goods_category.icon VARCHAR(255) NULL`，后续任务依赖其存在。

- [ ] **Step 1: 远程库加列**

```bash
node "C:/Users/lisi/.claude/skills/mysql-connect/scripts/query.mjs" --write --database panoramic_mall \
 "ALTER TABLE store_goods_spu ADD COLUMN min_price DECIMAL(10,2) NULL COMMENT '在售SKU最低价（推导量，由SKU联动维护）' AFTER lock_time;"
```

```bash
node "C:/Users/lisi/.claude/skills/mysql-connect/scripts/query.mjs" --write --database panoramic_mall \
 "ALTER TABLE goods_category ADD COLUMN icon VARCHAR(255) NULL COMMENT '分类图标图片URL' AFTER sort;"
```

- [ ] **Step 2: 回填 `min_price`（按不变量：上架且未删 SKU 的最低价）**

先看范围：

```bash
node "C:/Users/lisi/.claude/skills/mysql-connect/scripts/query.mjs" --database panoramic_mall \
 "SELECT COUNT(*) AS 待回填 FROM store_goods_spu s WHERE s.is_delete=0 AND EXISTS (SELECT 1 FROM store_goods_sku k WHERE k.spu_id=s.id AND k.is_delete=0 AND k.shelf_status=1);"
```

再执行：

```bash
node "C:/Users/lisi/.claude/skills/mysql-connect/scripts/query.mjs" --write --database panoramic_mall \
 "UPDATE store_goods_spu s SET s.min_price = (SELECT MIN(k.price) FROM store_goods_sku k WHERE k.spu_id=s.id AND k.is_delete=0 AND k.shelf_status=1) WHERE s.is_delete=0;"
```

- [ ] **Step 3: 复核回填（不变量必须成立）**

```bash
node "C:/Users/lisi/.claude/skills/mysql-connect/scripts/query.mjs" --database panoramic_mall \
 "SELECT COUNT(*) AS 违例行数 FROM store_goods_spu s WHERE s.is_delete=0 AND ((s.shelf_status=1 AND s.min_price IS NULL) OR (s.shelf_status=0 AND s.min_price IS NOT NULL));"
```

Expected: `违例行数 = 0`

- [ ] **Step 4: 9 个顶级分类的 icon 种子**

顶级分类 id 为 `1,4,6,9,13,14,15,16,17`（服装 / 手机数码 / 家用电器 / 电脑办公 / 美妆个护 / 食品生鲜 / 家居家装 / 钟表珠宝 / 车品出行）。图片 URL 用公开可访问的占位图服务（仅种子数据，不写进任何源码）：

```bash
node "C:/Users/lisi/.claude/skills/mysql-connect/scripts/query.mjs" --write --database panoramic_mall \
 "UPDATE goods_category SET icon=CONCAT('https://picsum.photos/seed/cat', id, '/160/160') WHERE id IN (1,4,6,9,13,14,15,16,17);"
```

- [ ] **Step 5: 复核 icon**

```bash
node "C:/Users/lisi/.claude/skills/mysql-connect/scripts/query.mjs" --database panoramic_mall \
 "SELECT id, name, icon FROM goods_category WHERE level=1 ORDER BY id;"
```

Expected: 9 行，icon 均非空。

- [ ] **Step 6: 同步两份 schema.sql**

把 `min_price` 加进 `backend/store/src/main/resources/db/schema.sql` 的 `store_goods_spu` 建表语句（与远程库同类型同注释，位置在 `lock_time` 之后）；把 `icon VARCHAR(255) DEFAULT NULL COMMENT '分类图标图片URL'` 加进 `backend/goods-center/src/main/resources/db/schema.sql` 的 `goods_category`（`sort` 之后）。

- [ ] **Step 7: 文本级核对**

```bash
cd /e/workspace/panoramic_mall
grep -n "min_price" backend/store/src/main/resources/db/schema.sql
grep -n "icon" backend/goods-center/src/main/resources/db/schema.sql
```

Expected: 各命中 1 行。

- [ ] **Step 8: 提交（仅 schema.sql，远程库改动无文件产物）**

```bash
git add backend/store/src/main/resources/db/schema.sql backend/goods-center/src/main/resources/db/schema.sql
git commit -m "DB：store_goods_spu 加 min_price、goods_category 加 icon（含远程库加列与回填）

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

## Task 3: `min_price` 推导列（代码）

**Files:**
- Modify: `backend/common/src/main/java/com/panoramic/common/store/vo/StoreGoodsSpuPlatformPageItemVO.java`（加 `minPrice`；owner 侧 VO **不动**）
- Modify: `backend/store/src/main/java/com/panoramic/store/entity/StoreGoodsSpu.java`（加 `minPrice` 字段）
- Modify: `backend/store/src/main/java/com/panoramic/store/service/StoreGoodsSkuService.java` + `impl/StoreGoodsSkuServiceImpl.java`（加 `minPriceBySpuId`）
- Modify: `backend/store/src/main/java/com/panoramic/store/service/impl/StoreGoodsSpuServiceImpl.java`（拆 `refreshDerived`）

**Interfaces:**
- Consumes: Task 2 的 `store_goods_spu.min_price` 列。
- Produces:
  - `StoreGoodsSpu.getMinPrice() : BigDecimal`
  - `StoreGoodsSkuService.minPriceBySpuId(Long spuId) : BigDecimal`（无上架 SKU 返回 `null`）
  - `StoreGoodsSpuServiceImpl#refreshDerived(StoreGoodsSpu spu) : void`（私有，**所有既有 `refreshShelfStatus(spu)` 调用点改调它**）
  - VO 字段 `minPrice : BigDecimal`

- [ ] **Step 1: 实体加字段**

在 `StoreGoodsSpu` 的 `lockTime` 之后加：

```java
    /**
     * 在售（上架且未删）SKU 的最低价；无上架 SKU 时为 null。
     * <p>推导量，由 {@code StoreGoodsSpuServiceImpl#refreshMinPrice} 唯一写入，
     * 不接受外部直接赋值。用于 C 端列表展示「¥xx.xx 起」与价格排序。</p>
     */
    private BigDecimal minPrice;
```

（`java.math.BigDecimal` 需 import。）

- [ ] **Step 2: 跨店 VO 加 `minPrice`（**只加这一个**）**

只改 `StoreGoodsSpuPlatformPageItemVO`：

```java
    /**
     * 在售 SKU 最低价（无上架 SKU 时为 null）
     */
    private java.math.BigDecimal minPrice;
```

> ⚠ **不要**同时给 owner 侧的 `StoreGoodsSpuPageItemVO` 加这个字段。owner 侧（店主自己的商品列表）本次改动完全不涉及、也没有任何消费方会读它——加了就是纯未使用的新增对外字段（YAGNI）。C 端与「¥xx 起」展示走的是跨店侧。

- [ ] **Step 3: SKU service 加取最低价能力**

接口 `StoreGoodsSkuService` 加：

```java
    /**
     * 某店铺商品名下<b>已上架且未删</b> SKU 的最低价（SPU 的 min_price 推导用）
     *
     * @param spuId 店铺商品 SPU id
     * @return 最低价；无上架 SKU 时返回 null
     */
    java.math.BigDecimal minPriceBySpuId(Long spuId);
```

实现 `StoreGoodsSkuServiceImpl` 加：

```java
    @Override
    public BigDecimal minPriceBySpuId(Long spuId) {
        // 取全量再求最小是有意的：本方法**只在写路径**被调用（save/replaceSkus/updateSkuShelf/lock），
        // 读路径一律直接读 store_goods_spu.min_price 冗余列（这正是加该列的理由），故不存在 N+1。
        // 一个 SPU 的 SKU 数是个位数，不值得为它引入字符串列名的聚合查询。
        List<StoreGoodsSku> onShelf = list(Wrappers.<StoreGoodsSku>lambdaQuery()
                .eq(StoreGoodsSku::getSpuId, spuId)
                .eq(StoreGoodsSku::getShelfStatus, StoreGoodsSku.SHELF_ON));
        return onShelf.stream()
                .map(StoreGoodsSku::getPrice)
                .filter(Objects::nonNull)
                .min(BigDecimal::compareTo)
                .orElse(null);
    }
```

（`list` 是 MP 基类方法；逻辑删除由其自动附加 `is_delete=0`。）

- [ ] **Step 4: 拆出 `refreshDerived` 并接上 `min_price`**

在 `StoreGoodsSpuServiceImpl` 中：

把现有私有方法 `refreshShelfStatus(spu)` **整体改名**为 `refreshDerived(spu)`，内容改为依次调用两个写者：

```java
    /**
     * 推导量统一刷新入口：SPU 的 {@code shelf_status} 与 {@code min_price} 都只经此处写入。
     *
     * @param spu 店铺商品实体（须是**从库中重取**的最新行，不可用被条件更新绕过的旧对象）
     */
    private void refreshDerived(StoreGoodsSpu spu) {
        refreshShelfStatus(spu);
        refreshMinPrice(spu);
    }

    /**
     * 按名下 SKU 重算并回写 SPU 上下架状态（R2/R3）。
     * <p>不变量「SPU上架 ⟺ 至少一个 SKU 上架」的唯一写入口。状态未变则不发 UPDATE。</p>
     */
    private void refreshShelfStatus(StoreGoodsSpu spu) {
        int derived = skuService.hasOnShelfSku(spu.getId())
                ? StoreGoodsSpu.SHELF_ON : StoreGoodsSpu.SHELF_OFF;
        if (Objects.equals(spu.getShelfStatus(), derived)) {
            return;
        }
        spu.setShelfStatus(derived);
        updateById(spu);
    }

    /**
     * 按名下上架 SKU 重算并回写 SPU 最低价。
     * <p>不变量「min_price = 上架且未删 SKU 的最低价」的唯一写入口。
     * ⚠ 必须<b>独立</b>比较 min_price 是否变化，不得复用 {@link #refreshShelfStatus} 的状态早退——
     * 上下架状态没变时 min_price 仍可能变（例：下架高价 SKU 后最低价改变但 SPU 仍为上架）。</p>
     */
    private void refreshMinPrice(StoreGoodsSpu spu) {
        BigDecimal derived = skuService.minPriceBySpuId(spu.getId());
        if (samePrice(spu.getMinPrice(), derived)) {
            return;
        }
        spu.setMinPrice(derived);
        // ⚠ 不能用 updateById：MP 默认 FieldStrategy 为 NOT_NULL，derived 为 null 时该列会被跳过，
        // 「SKU 全部下架 → min_price 清空」这条就静默不落库（同解锁清 lock_* 的陷阱）。
        // 条件更新 + 显式 set 是唯一可靠写法。
        lambdaUpdate()
                .set(StoreGoodsSpu::getMinPrice, derived)
                .eq(StoreGoodsSpu::getId, spu.getId())
                .update();
    }

    /** 价格等值比较（都用 compareTo：BigDecimal.equals 对精度敏感，10.0 与 10.00 判定不等） */
    private boolean samePrice(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) {
            return a == null && b == null;
        }
        return a.compareTo(b) == 0;
    }
```

- [ ] **Step 5: 把所有 `refreshShelfStatus(spu)` 调用点改为 `refreshDerived(spu)`**

已核实：**恰好 3 个调用点**（`replaceSkus` 尾部、`updateSkuShelf` 尾部、`lock` 尾部的 `refreshShelfStatus(getById(id))`）——`delete` 与 `update` 都不调它。逐个改成 `refreshDerived(...)`；改完用下面的 grep 复核：

```bash
cd /e/workspace/panoramic_mall
grep -n "refreshShelfStatus\|refreshDerived" backend/store/src/main/java/com/panoramic/store/service/impl/StoreGoodsSpuServiceImpl.java
```

Expected: `refreshShelfStatus` 只剩「方法定义」1 处 + `refreshDerived` 内部调用 1 处；其余调用点全部是 `refreshDerived`。

- [ ] **Step 6: 编译**

```bash
cd /e/workspace/panoramic_mall
export MAVEN_HOME=/e/tools/apache-maven-3.9.16
/e/tools/apache-maven-3.9.16/bin/mvn -q -pl backend/store -am compile
```

Expected: BUILD SUCCESS（`-am` 会一并编译 common）。

- [ ] **Step 7: 提交**

```bash
git add backend/store backend/common
git commit -m "store：min_price 推导列，与 shelf_status 同处由 refreshDerived 统一维护

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

## Task 4: 跨店分页通用化（含 admin 同步，原子任务）

> ⚠ 本任务跨 common / store / admin 三个模块，**中间状态仓库不编译**，必须一次做完再提交。

**Files:**
- Rename: `common/.../store/dto/StoreGoodsSpuPlatformPageQueryDTO.java` → `StoreGoodsSpuCrossShopPageQueryDTO.java`
- Rename: `common/.../store/vo/StoreGoodsSpuPlatformPageItemVO.java` → `StoreGoodsSpuCrossShopPageItemVO.java`
- Modify: `common/.../store/api/StoreClient.java`
- Modify: `backend/store/.../controller/GoodsController.java`
- Modify: `backend/store/.../service/StoreGoodsSpuService.java` + `impl/StoreGoodsSpuServiceImpl.java`
- Modify: `backend/store/.../service/StoreShopService.java` + `impl/StoreShopServiceImpl.java`（加 `idListByStatus`）
- Modify: `backend/admin/.../dto/ShopGoodsPageQueryDTO.java`、`bff/ShopGoodsBffService.java`、`controller/shop/ShopGoodsController.java`
- Modify: `frontend/admin/src/views/shopgoods/ShopGoodsManage.vue`、`frontend/admin/src/api/shopGoods.ts`

**Interfaces:**
- Consumes: Task 3 的 `minPrice` 字段。
- Produces:
  - `StoreGoodsSpuCrossShopPageQueryDTO`：字段 `keyword` / `categoryIds:List<Long>` / `brandIds:List<Long>` / `storeId:Long` / `shelfStatus:Integer` / `lockStatus:Integer` / `shopStatus:Integer` / `sort:String`
  - `StoreGoodsSpuCrossShopPageItemVO`：字段同原 platform VO + `minPrice:BigDecimal`
  - `StoreClient.pageStoreGoodsCrossShop(StoreGoodsSpuCrossShopPageQueryDTO) : PageResult<StoreGoodsSpuCrossShopPageItemVO>`，路径 `POST /goods/cross-shop/spu/page`
  - `StoreShopService.idListByStatus(Integer status) : List<Long>`

- [ ] **Step 1: 用 `git mv` 重命名两个类型文件**

```bash
cd /e/workspace/panoramic_mall
git mv backend/common/src/main/java/com/panoramic/common/store/dto/StoreGoodsSpuPlatformPageQueryDTO.java \
       backend/common/src/main/java/com/panoramic/common/store/dto/StoreGoodsSpuCrossShopPageQueryDTO.java
git mv backend/common/src/main/java/com/panoramic/common/store/vo/StoreGoodsSpuPlatformPageItemVO.java \
       backend/common/src/main/java/com/panoramic/common/store/vo/StoreGoodsSpuCrossShopPageItemVO.java
```

- [ ] **Step 2: 改 DTO：类名 + `brandId`→`brandIds` + 新增 `shopStatus`/`sort` + 重写 javadoc**

`StoreGoodsSpuCrossShopPageQueryDTO`：

- 类名改 `StoreGoodsSpuCrossShopPageQueryDTO`
- 删 `private Long brandId;`，加：

```java
    /**
     * 品牌筛选（多值），空 = 不按品牌过滤
     */
    private List<Long> brandIds;

    /**
     * 所属店铺审核状态筛选（0 草稿 / 1 待审核 / 2 已通过 / 3 已驳回），空 = 不过滤。
     * <p>C 端商城固定传 2（只出已审核通过店铺的商品）；管理端不传。</p>
     */
    private Integer shopStatus;

    /**
     * 排序：{@code default}（按 id 倒序，等价于原行为）/ {@code priceAsc} / {@code priceDesc}（按 min_price）。
     * <p>取其它值一律按 {@code default} 处理（宽松容错，不抛异常）。</p>
     */
    private String sort;
```

- javadoc 首行改为：「店铺在售商品分页查询参数 · **跨店通用**（无数据权限锚点；admin BFF 与 mall-bff 共用）」，并说明「C 端固定传 `shopStatus=2` + `shelfStatus=1` + `lockStatus=0`，域侧不含 C 端隐含约束」。

- [ ] **Step 3: 改 VO：类名 + 继承关系保持 + 加 `minPrice`**

`StoreGoodsSpuCrossShopPageItemVO` 类名改掉，javadoc 改为「店铺在售商品分页列表项 · **跨店通用**（admin BFF 管理视角 / mall-bff C 端视角共用；C 端输出前由 BFF 裁剪字段）」。`minPrice` 字段已在 Task 3 加好。

> ⚠ `StoreGoodsSpuPlatformDetailVO extends StoreGoodsSpuDetailVO` 与详情接口**保持不动**——详情仍只有管理端用，返回锁定明细，名字里的 Platform 是对的。

- [ ] **Step 4: 全局替换引用（Java 侧）**

```bash
cd /e/workspace/panoramic_mall
grep -rln "StoreGoodsSpuPlatformPage" backend --include=*.java | grep -v /target/
```

对每一个命中文件替换：`StoreGoodsSpuPlatformPageQueryDTO`→`StoreGoodsSpuCrossShopPageQueryDTO`、`StoreGoodsSpuPlatformPageItemVO`→`StoreGoodsSpuCrossShopPageItemVO`。

- [ ] **Step 5: `StoreShopService` 加 `idListByStatus`**

接口加：

```java
    /**
     * 按审核状态取店铺 id 集合（跨店商品查询按店铺状态过滤用，避免 join）
     *
     * @param status 审核状态（0 草稿 / 1 待审核 / 2 已通过 / 3 已驳回）
     * @return 店铺 id 列表；无则空列表
     */
    List<Long> idListByStatus(Integer status);
```

实现加（用 MP 基类 `list`）：

```java
    @Override
    public List<Long> idListByStatus(Integer status) {
        return list(Wrappers.<StoreShop>lambdaQuery()
                .select(StoreShop::getId)
                .eq(StoreShop::getStatus, status))
                .stream().map(StoreShop::getId).collect(Collectors.toList());
    }
```

- [ ] **Step 6: 域 service 改名 + 加新筛选与排序**

`StoreGoodsSpuService`：接口方法 `platformPage(...)` 改名 `crossShopPage(...)`，入参出参换新类型。

⚠ **别漏掉 javadoc 里的 `{@link #platformPage}` 引用**——它们不参与编译，漏了不会报错，但会留下指向不存在方法的断链。已知位置（已核实）：
- `StoreGoodsSpuService.java:23` — 类注释「跨店全量（{@link #platformPage} / {@link #platformDetail} / {@link #lock} / {@link #unlock}）」
- `StoreGoodsSpuServiceImpl.java:59` — 「<b>platform 侧（跨店全量）</b>：{@link #platformPage} / {@link #platformDetail} 不带 store_id 过滤」

两处都把 `{@link #platformPage}` 改为 `{@link #crossShopPage}`，并把「跨店全量」的措辞改成「跨店通用（调用方自设限定条件）」；`platformDetail`/`lock`/`unlock` 的引用保持不动。用 `grep -rn "platformPage" backend --include=*.java | grep -v /target/` 收尾确认（结果应为空）。

`StoreGoodsSpuServiceImpl`：

- 方法改名 `crossShopPage`，返回类型换 `PageResult<StoreGoodsSpuCrossShopPageItemVO>`
- 查询体改为：

```java
    @Override
    public PageResult<StoreGoodsSpuCrossShopPageItemVO> crossShopPage(StoreGoodsSpuCrossShopPageQueryDTO dto) {
        Page<StoreGoodsSpu> spuPage = dto.toPage(StoreGoodsSpu.class);
        boolean hasCategoryFilter = dto.getCategoryIds() != null && !dto.getCategoryIds().isEmpty();
        boolean hasBrandFilter = dto.getBrandIds() != null && !dto.getBrandIds().isEmpty();
        // ⚠ DTO 的 brandIds 是复数（新），但**实体** StoreGoodsSpu 的字段仍是单数 brandId —— 这里是列引用，用 getBrandId
        // 店铺状态过滤：经店铺 service 取 id 集合后 IN（跨实体只走 owner service，不 join）
        List<Long> shopIds = dto.getShopStatus() == null
                ? Collections.emptyList() : shopService.idListByStatus(dto.getShopStatus());
        // 状态过滤命中空集合时，用「永假条件」保证返回空页而不是退化成不过滤
        LambdaQueryWrapper<StoreGoodsSpu> wrapper = Wrappers.<StoreGoodsSpu>lambdaQuery()
                .in(hasCategoryFilter, StoreGoodsSpu::getCategoryId, dto.getCategoryIds())
                .in(hasBrandFilter, StoreGoodsSpu::getBrandId, dto.getBrandIds())
                .eq(dto.getStoreId() != null, StoreGoodsSpu::getStoreId, dto.getStoreId())
                .eq(dto.getShelfStatus() != null, StoreGoodsSpu::getShelfStatus, dto.getShelfStatus())
                .eq(dto.getLockStatus() != null, StoreGoodsSpu::getLockStatus, dto.getLockStatus())
                .like(StringUtils.hasText(dto.getKeyword()), StoreGoodsSpu::getName, dto.getKeyword());
        if (dto.getShopStatus() != null) {
            if (shopIds.isEmpty()) {
                wrapper.apply("1 = 0");
            } else {
                wrapper.in(StoreGoodsSpu::getStoreId, shopIds);
            }
        }
        applySort(wrapper, dto.getSort());
        IPage<StoreGoodsSpu> result = page(spuPage, wrapper);
        // …（店铺名 / SKU 数量批量回填保持原逻辑，VO 类型换新）
    }

    /**
     * 施加排序：默认按 id 倒序；价格排序走 min_price。
     * <p>不处理 min_price 的 NULL 位置：C 端固定 shelf_status=1，而上架 SPU 必有上架 SKU（不变量），
     * 故 C 端 min_price 必非 null；管理端出现下架商品时排序位置不作保证。</p>
     */
    private void applySort(LambdaQueryWrapper<StoreGoodsSpu> wrapper, String sort) {
        if ("priceAsc".equals(sort)) {
            wrapper.orderByAsc(StoreGoodsSpu::getMinPrice);
        } else if ("priceDesc".equals(sort)) {
            wrapper.orderByDesc(StoreGoodsSpu::getMinPrice);
        } else {
            wrapper.orderByDesc(StoreGoodsSpu::getId);
        }
    }
```

- VO 组装处补 `vo.setMinPrice(spu.getMinPrice())`（`BeanUtils.copyProperties` 已能带过去，但仍需确认字段名一致）。

> ⚠ `apply("1 = 0")` 是 MP 的裸 SQL 片段，仅此一处；若评审不接受，可改为 `wrapper.in(StoreGoodsSpu::getStoreId, List.of(-1L))`（等价语义，纯 API）。

- [ ] **Step 7: 域 controller 改路径**

`GoodsController`：

```java
    @PostMapping("/cross-shop/spu/page")
    public PageResult<StoreGoodsSpuCrossShopPageItemVO> crossShopPage(
            @Validated @RequestBody StoreGoodsSpuCrossShopPageQueryDTO dto) {
        return storeGoodsSpuService.crossShopPage(dto);
    }
```

类 javadoc 里「**platform 侧**（`/platform/spu/**`…）」一段补一句：分页已通用化为 `/cross-shop/spu/page`（admin BFF 与 mall-bff 共用），`/platform/spu/**` 仅剩详情与锁定解锁。

- [ ] **Step 8: `StoreClient` 改方法名与路径**

```java
    /**
     * 店铺商品分页（<b>跨店通用</b>：不传 store_id 锚点，调用方自设限定条件）。
     * <p>admin BFF 用于「店铺商品管理」（不限条件，全量）；
     * mall-bff 用于 C 端商品浏览（固定传 shopStatus=2 + shelfStatus=1 + lockStatus=0）。</p>
     * <p>⚠ 用 {@code POST + @RequestBody} 而非 query 参数：{@code categoryIds}/{@code brandIds} 是集合，
     * {@code @SpringQueryMap} 对集合字段的序列化口径不确定，走 body 规避。</p>
     */
    @PostMapping("/goods/cross-shop/spu/page")
    PageResult<StoreGoodsSpuCrossShopPageItemVO> pageStoreGoodsCrossShop(
            @RequestBody StoreGoodsSpuCrossShopPageQueryDTO dto);
```

- [ ] **Step 9: admin 侧同步（后端）**

`ShopGoodsPageQueryDTO`：`private Long brandId;` → `private List<Long> brandIds;`（javadoc 注明「多值，空为全部」）。

`ShopGoodsBffService.pageGoods`：`query.setBrandId(...)` → `query.setBrandIds(dto.getBrandIds())`；`storeClient.platformPageStoreGoods` → `storeClient.pageStoreGoodsCrossShop`；返回类型换 `StoreGoodsSpuCrossShopPageItemVO`。

`ShopGoodsController` 的 javadoc 把 `brandId` 改为 `brandIds`。

- [ ] **Step 10: admin 侧同步（前端）**

⚠ **`shopGoods.ts` 里有 4 处 `brandId`，只有 1 处该改**（已核实行号）：

| 行 | 上下文 | 改成什么 |
|---|---|---|
| 17 | `ShopGoodsPageItem.brandId: number \| null`（**出参**，来自域 VO） | **不动**（域侧 VO 的 `brandId` 仍是单数） |
| 56 | `ShopGoodsDetail.brandId`（**出参**，同上） | **不动** |
| 78 | `ShopGoodsPageQuery.brandId?: number`（**查询入参**） | → `brandIds?: number[]` |
| 104 | 一条文档注释里的字段罗列 | 把注释里的 `brandId` 改为 `brandIds` |

改错前两行会让页面渲染不出品牌名——它们与本次改动无关。

`frontend/admin/src/views/shopgoods/ShopGoodsManage.vue`：
- :19 品牌 `el-select` 加 `multiple collapse-tags`，`v-model="query.brandId"` → `query.brandIds`（宽度 150px 可适当放宽，如 200px，避免多选标签挤爆）
- :197 `query` 初始对象里 `brandId: undefined` → `brandIds: undefined`
- :246 `handleReset` 里 `query.brandId = undefined` → `query.brandIds = undefined`

- [ ] **Step 11: 全局残留扫描**

```bash
cd /e/workspace/panoramic_mall
echo "--- 旧类型名（应为空）---"
grep -rn "StoreGoodsSpuPlatformPage" backend frontend --include=*.java --include=*.ts --include=*.vue | grep -v /target/ | grep -v node_modules
echo "--- 旧路径（应为空）---"
grep -rn "/goods/platform/spu/page" backend --include=*.java | grep -v /target/
echo "--- 旧方法名 tokens（应为空）---"
grep -rn "platformPage\|platformPageStoreGoods" backend --include=*.java | grep -v /target/
```

Expected: 三条**全部无输出**。

⚠ 注意这里**刻意不做任何「排除保留项」的过滤**。早先版本写过 `grep -v "platformDetail"`，那是个自我拆台的过滤器——:23 与 :59 两行 javadoc **同时**含 `platformPage` 与 `platformDetail`，`grep -v` 会把它们整行滤掉，于是「javadoc 漏改」永远查不出来。保留项 `platformDetail` / `platformStoreGoodsDetail` 不含 `platformPage` 这个子串，本来就不会命中，不需要过滤。

```bash
echo "--- admin 前端：查询入参侧不应再有单数 brandId ---"
grep -rn "brandId" frontend/admin/src/api/shopGoods.ts frontend/admin/src/views/shopgoods/
```

Expected: **只剩 `shopGoods.ts:17` 与 `:56` 两行**（出参类型，有意保留单数）。若还出现 `query.brandId` 或 `ShopGoodsPageQuery` 里的 `brandId?`，就是漏改。

> ⚠ **不要**对整个 `frontend/admin/src` 跑 `grep brandId` 然后要求空输出——`api/spu.ts`、`views/spu/SpuManage.vue`、`views/spu/SpuFormDialog.vue` 里的 `brandId` 属于 goods-center「标准商品」模块（SPU 的品牌属性），与本次「店铺商品跨店筛选」是两回事，改它们会破坏无关功能。

- [ ] **Step 12: 编译三端后端**

```bash
cd /e/workspace/panoramic_mall
/e/tools/apache-maven-3.9.16/bin/mvn -q -pl backend/store,backend/admin -am compile
```

Expected: BUILD SUCCESS。

- [ ] **Step 13: admin 前端类型检查**

```bash
cd /e/workspace/panoramic_mall/frontend/admin && npm run type-check
```

Expected: 无错误输出。

- [ ] **Step 14: 提交**

```bash
git add -A backend/common backend/store backend/admin frontend/admin
git commit -m "store：跨店分页通用化（去 Platform 命名、品牌多值、加店铺状态与价格排序）；admin 同步

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

## Task 5: store 域新增 facets 筛选聚合接口

**Files:**
- Create: `common/.../store/dto/StoreGoodsSpuFacetQueryDTO.java`
- Create: `common/.../store/vo/StoreGoodsSpuFacetVO.java`、`common/.../store/vo/StoreGoodsFacetItemVO.java`
- Modify: `common/.../store/api/StoreClient.java`
- Modify: `backend/store/.../service/StoreGoodsSpuService.java` + `impl/StoreGoodsSpuServiceImpl.java`
- Modify: `backend/store/.../controller/GoodsController.java`

**Interfaces:**
- Consumes: Task 4 的 `StoreShopService.idListByStatus`。
- Produces:
  - `StoreGoodsSpuFacetQueryDTO`：`keyword:String` / `scopeCategoryIds:List<Long>` / `filterCategoryIds:List<Long>` / `filterBrandIds:List<Long>` / `shopStatus:Integer` / `shelfStatus:Integer` / `lockStatus:Integer`
  - `StoreGoodsSpuFacetVO`：`categories:List<StoreGoodsFacetItemVO>` / `brands:List<StoreGoodsFacetItemVO>`
  - `StoreGoodsFacetItemVO`：`id:Long` / `name:String` / `count:Integer`
  - `StoreClient.mallFacets(StoreGoodsSpuFacetQueryDTO) : StoreGoodsSpuFacetVO`，路径 `POST /goods/facets`

- [ ] **Step 1: 建三个类型**

`StoreGoodsFacetItemVO`：

```java
package com.panoramic.common.store.vo;

import lombok.Data;

/**
 * 筛选维度的一个可选项（分类 / 品牌通用）
 * <p>{@code name} 取自库中<b>快照</b>（同一 id 快照名一致）。端 BFF 可用权威源覆盖：
 * 分类名与顶级归属由 BFF 用分类树解析，树里查不到时回退本字段。</p>
 */
@Data
public class StoreGoodsFacetItemVO {

    /** 维度值 id（分类 id 或品牌 id） */
    private Long id;

    /** 维度值名称（快照） */
    private String name;

    /** 该维度值下的命中商品数 */
    private Integer count;
}
```

`StoreGoodsSpuFacetVO`：

```java
package com.panoramic.common.store.vo;

import lombok.Data;
import java.util.List;

/**
 * 商品筛选维度聚合结果（分类 / 品牌两个维度）。
 * <p><b>⚠ 核心口径：每个维度计算时排除自己那一维。</b>
 * {@code categories} 只受 {@code filterBrandIds} 影响（不受 {@code filterCategoryIds}），
 * {@code brands} 只受 {@code filterCategoryIds} 影响（不受 {@code filterBrandIds}）——
 * 否则用户每选一个选项，同维度的其它选项就会全部消失。</p>
 */
@Data
public class StoreGoodsSpuFacetVO {

    /** 分类维度可选项（按 count 降序，id 升序兜底） */
    private List<StoreGoodsFacetItemVO> categories;

    /** 品牌维度可选项（按 count 降序，id 升序兜底） */
    private List<StoreGoodsFacetItemVO> brands;
}
```

`StoreGoodsSpuFacetQueryDTO`：

```java
package com.panoramic.common.store.dto;

import lombok.Data;
import java.util.List;

/**
 * 商品筛选维度聚合查询参数（store 域内部接口与端 BFF 同源共享）
 */
@Data
public class StoreGoodsSpuFacetQueryDTO {

    /** 关键字（模糊匹配商品名称），空 = 不按关键字过滤 */
    private String keyword;

    /**
     * 范围锚点（已展开的子树分类 id 集合），空 = 不限。
     * <p>分类页传路由分类的子树；搜索页为空。</p>
     */
    private List<Long> scopeCategoryIds;

    /**
     * 已选分类筛选（已展开的子树分类 id 集合），空 = 未选。
     * <p>⚠ 只作用于 {@code brands} 维度，不作用于 {@code categories} 维度。</p>
     */
    private List<Long> filterCategoryIds;

    /**
     * 已选品牌筛选，空 = 未选。
     * <p>⚠ 只作用于 {@code categories} 维度，不作用于 {@code brands} 维度。</p>
     */
    private List<Long> filterBrandIds;

    /** 所属店铺审核状态，空 = 不过滤（C 端固定传 2） */
    private Integer shopStatus;

    /** 上下架，空 = 不过滤（C 端固定传 1） */
    private Integer shelfStatus;

    /** 锁定状态，空 = 不过滤（C 端固定传 0） */
    private Integer lockStatus;
}
```

- [ ] **Step 2: service 接口加方法**

`StoreGoodsSpuService` 加：

```java
    /**
     * 商品筛选维度聚合（分类 / 品牌两个维度各有独立口径，见 {@link StoreGoodsSpuFacetVO}）
     *
     * @param dto 聚合查询参数
     * @return 两个维度的可选项及命中数
     */
    StoreGoodsSpuFacetVO facets(StoreGoodsSpuFacetQueryDTO dto);
```

- [ ] **Step 3: service 实现**

`StoreGoodsSpuServiceImpl` 加：

```java
    @Override
    public StoreGoodsSpuFacetVO facets(StoreGoodsSpuFacetQueryDTO dto) {
        StoreGoodsSpuFacetVO vo = new StoreGoodsSpuFacetVO();
        // ⚠ 两个维度互斥地排除自己：分类维度不带 filterCategoryIds，品牌维度不带 filterBrandIds
        vo.setCategories(facetBy(dto, "category_id", "category_name", true));
        vo.setBrands(facetBy(dto, "brand_id", "brand_name", false));
        return vo;
    }

    /**
     * 按某一维度的列做 GROUP BY 聚合。
     *
     * @param dto        查询参数
     * @param idColumn   维度列名（{@code category_id} / {@code brand_id}）
     * @param nameColumn 维度名称快照列名
     * @param isCategory true = 本次算分类维度（用 scopeCategoryIds + filterBrandIds）；false = 品牌维度（用 scope+filterCategoryIds）
     */
    private List<StoreGoodsFacetItemVO> facetBy(StoreGoodsSpuFacetQueryDTO dto, String idColumn,
                                                String nameColumn, boolean isCategory) {
        List<Long> scope = dto.getScopeCategoryIds();
        List<Long> filterCats = dto.getFilterCategoryIds();
        List<Long> filterBrands = dto.getFilterBrandIds();
        QueryWrapper<StoreGoodsSpu> qw = new QueryWrapper<>();
        qw.select(idColumn + " AS id", nameColumn + " AS name", "COUNT(*) AS cnt")
          .isNotNull(idColumn)
          .like(StringUtils.hasText(dto.getKeyword()), "name", dto.getKeyword())
          .eq(dto.getShelfStatus() != null, "shelf_status", dto.getShelfStatus())
          .eq(dto.getLockStatus() != null, "lock_status", dto.getLockStatus())
          .in(scope != null && !scope.isEmpty(), "category_id", scope)
          .in(isCategory && filterBrands != null && !filterBrands.isEmpty(), "brand_id", filterBrands)
          .in(!isCategory && filterCats != null && !filterCats.isEmpty(), "category_id", filterCats)
          .groupBy(idColumn, nameColumn)
          .orderByDesc("cnt");
        if (dto.getShopStatus() != null) {
            List<Long> shopIds = shopService.idListByStatus(dto.getShopStatus());
            if (shopIds.isEmpty()) {
                return Collections.emptyList();
            }
            qw.in("store_id", shopIds);
        }
        return listMaps(qw).stream().map(row -> {
            StoreGoodsFacetItemVO item = new StoreGoodsFacetItemVO();
            item.setId(((Number) row.get("id")).longValue());
            item.setName((String) row.get("name"));
            item.setCount(((Number) row.get("cnt")).intValue());
            return item;
        }).collect(Collectors.toList());
    }
```

> 注：聚合必须走 `QueryWrapper`（字符串列名）——`LambdaQueryWrapper` 的 `select` 只接受 `SFunction`，无法表达 `COUNT(*) AS cnt`。这是本仓库里唯一使用字符串列名的地方，仅限本方法。
> 逻辑删除条件由 MP 依 `@TableLogic` 自动附加，无需手写 `is_delete=0`。

- [ ] **Step 4: controller 加端点**

`GoodsController` 加：

```java
    /**
     * 商品筛选维度聚合（分类 / 品牌）。⚠ 两维度互斥排除自身：分类维度不受已选分类影响、
     * 品牌维度不受已选品牌影响（否则选中后同维度选项即消失）。
     */
    @PostMapping("/facets")
    public StoreGoodsSpuFacetVO facets(@RequestBody StoreGoodsSpuFacetQueryDTO dto) {
        return storeGoodsSpuService.facets(dto);
    }
```

- [ ] **Step 5: `StoreClient` 加方法**

```java
    /**
     * 商品筛选维度聚合（分类 / 品牌，各带命中数）。
     * <p>⚠ 口径见 {@link StoreGoodsSpuFacetVO}：两维度互斥排除自身。</p>
     */
    @PostMapping("/goods/facets")
    StoreGoodsSpuFacetVO mallFacets(@RequestBody StoreGoodsSpuFacetQueryDTO dto);
```

- [ ] **Step 6: 编译**

```bash
cd /e/workspace/panoramic_mall
/e/tools/apache-maven-3.9.16/bin/mvn -q -pl backend/store -am compile
```

Expected: BUILD SUCCESS。

- [ ] **Step 7: 提交**

```bash
git add backend/store backend/common
git commit -m "store：新增 facets 筛选聚合（分类/品牌，两维度互斥排除自身）

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

## Task 6: 分类 icon（common + goods-center + admin）

**Files:**
- Modify: `common/.../goods/dto/CategorySaveDTO.java`、`CategoryUpdateDTO.java`
- Modify: `common/.../goods/vo/CategoryTreeVO.java`
- Modify: `backend/goods-center/.../entity/GoodsCategory.java`
- Modify: `backend/goods-center/.../service/impl/CategoryServiceImpl.java`（若 DTO→实体靠 `BeanUtils.copyProperties` 则无需改）
- Modify: `frontend/admin/src/views/category/CategoryFormDialog.vue`

**Interfaces:**
- Consumes: Task 2 的 `goods_category.icon` 列。
- Produces: `CategoryTreeVO.icon : String`、`CategorySaveDTO.icon`、`CategoryUpdateDTO.icon`。

- [ ] **Step 1: 实体加字段**

`GoodsCategory` 的 `sort` 之后加：

```java
    /**
     * 分类图标图片 URL（可空；前台分类宫格展示，空则前端回退渐变占位）
     */
    private String icon;
```

- [ ] **Step 2: 两个 DTO 加字段**

`CategorySaveDTO` / `CategoryUpdateDTO` 各加：

```java
    /**
     * 分类图标图片 URL（可空）。仅做非空时的长度校验，不校验可达性。
     */
    @Size(max = 255, message = "图标 URL 不能超过 255 个字符")
    private String icon;
```

- [ ] **Step 3: 树 VO 加字段**

`CategoryTreeVO` 加：

```java
    /**
     * 分类图标图片 URL（可空）
     */
    private String icon;
```

- [ ] **Step 4: 确认树组装处会带出 icon**

```bash
cd /e/workspace/panoramic_mall
grep -rn "CategoryTreeVO" backend/goods-center/src/main/java --include=*.java | grep -v /target/
```

若树上溯/组装用的是 `BeanUtils.copyProperties(entity, vo)`，`icon` 会自动带出，无需改动；若是逐字段 `set`，需补 `vo.setIcon(entity.getIcon())`。

- [ ] **Step 5: 编译**

```bash
/e/tools/apache-maven-3.9.16/bin/mvn -q -pl backend/goods-center -am compile
```

Expected: BUILD SUCCESS。

- [ ] **Step 6: admin 分类表单加图标录入**

`CategoryFormDialog.vue`：
- form 对象加 `icon: ''`
- 表单里在「排序」之后加一项：

```html
<el-form-item label="图标 URL" prop="icon">
  <el-input v-model="form.icon" maxlength="255" placeholder="分类图标图片地址（可留空）" />
</el-form-item>
```

- 编辑回填与提交 payload 带上 `icon`
- 若表单有 `rules`，加 `icon: [{ max: 255, message: '不能超过 255 个字符', trigger: 'blur' }]`

- [ ] **Step 7: admin 类型检查**

```bash
cd /e/workspace/panoramic_mall/frontend/admin && npm run type-check
```

Expected: 无错误。

- [ ] **Step 8: 提交**

```bash
git add backend/goods-center backend/common frontend/admin
git commit -m "goods-center：分类加 icon 列（含 admin 分类表单录入）

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

## Task 7: mall-bff 启用 Feign + catalog 三接口与编排

**Files:**
- Modify: `backend/mall-bff/src/main/java/com/panoramic/mallbff/MallBffApplication.java`
- Modify: `backend/mall-bff/src/main/resources/application.yml`（白名单）
- Create: `controller/CatalogController.java`、`service/CatalogBffService.java`
- Create: `dto/MallGoodsPageQueryDTO.java`、`dto/MallFacetQueryDTO.java`
- Create: `vo/MallGoodsItemVO.java`、`vo/MallFacetVO.java`、`vo/MallFacetItemVO.java`

**Interfaces:**
- Consumes: `StoreClient.pageStoreGoodsCrossShop` / `StoreClient.mallFacets` / `GoodsCenterClient.categoryTree`；`StoreGoodsSpuCrossShopPageQueryDTO` / `StoreGoodsSpuFacetQueryDTO` / `StoreGoodsSpuFacetVO` / `PageResult` / `CategoryTreeVO`。
- Produces（页面契约）：
  - `GET /catalog/categories` → `List<CategoryTreeVO>`
  - `POST /catalog/goods`（body `MallGoodsPageQueryDTO`）→ `PageResult<MallGoodsItemVO>`
  - `POST /catalog/facets`（body `MallFacetQueryDTO`）→ `MallFacetVO`

- [ ] **Step 1: 启用 Feign**

`MallBffApplication` 加 `@EnableFeignClients(basePackages = {"com.panoramic.common.store", "com.panoramic.common.goods"})`，并把类注释里「一期没有 Feign 客户端」的说明改为「已接 goods-center（分类树）与 store（商品分页/筛选聚合）」。

- [ ] **Step 2: 白名单加 `/catalog/**`**

`backend/mall-bff/src/main/resources/application.yml`：

```yaml
  auth:
    whitelist-paths: /auth/login,/auth/register,/auth/sms-code,/catalog/**
```

同时删掉该 yml 里「一期本模块没有任何 Feign 客户端，feign-circuitbreaker 属空转」那段过期注释。

- [ ] **Step 3: 建 DTO**

`MallGoodsPageQueryDTO extends BasePageVO`：

```java
    /** 关键字（模糊匹配商品名称） */
    private String keyword;
    /** 分类页路由锚点（该分类 + 全部后代），搜索页为空 */
    private Long categoryId;
    /** 已选分类筛选（多选；各自展开子树后取并集），空 = 不按分类过滤 */
    private List<Long> categoryIds;
    /** 已选品牌筛选（多选），空 = 不按品牌过滤 */
    private List<Long> brandIds;
    /** 排序：default / priceAsc / priceDesc */
    private String sort;
```

`MallFacetQueryDTO`：

```java
    /** 关键字 */
    private String keyword;
    /** 分类页路由锚点（可空） */
    private Long categoryId;
    /** 已选分类筛选（多选） */
    private List<Long> categoryIds;
    /** 已选品牌筛选（多选） */
    private List<Long> brandIds;
```

- [ ] **Step 4: 建 VO**

`MallGoodsItemVO`（**C 端形状，与域 VO 解耦**）：

```java
    private Long id;
    private String name;
    /** 主图 URL，空则由前端回退 CSS 渐变占位 */
    private String mainImage;
    /** 在售 SKU 最低价（「¥xx.xx 起」） */
    private BigDecimal minPrice;
    private Long storeId;
    private String storeName;
    private Long categoryId;
    private String categoryName;
    private Long brandId;
    private String brandName;
```

`MallFacetItemVO`：`Long id` / `String name` / `Integer count`
`MallFacetVO`：`List<MallFacetItemVO> categories` / `List<MallFacetItemVO> brands`

- [ ] **Step 5: 建 `CatalogBffService`**

职责与关键实现：

```java
@Service
@RequiredArgsConstructor
public class CatalogBffService {

    private static final String DOWN_MSG = "商品暂不可用，请稍后重试";

    /** C 端固定展示口径：已审核通过店铺 + 上架 + 未锁定 */
    private static final Integer SHOP_STATUS_APPROVED = 2;
    private static final Integer SHELF_ON = 1;
    private static final Integer LOCK_OFF = 0;

    private final GoodsCenterClient goodsCenterClient;
    private final StoreClient storeClient;

    /** 分类树（每次实调 goods-center，不缓存；树很小） */
    public List<CategoryTreeVO> categories() {
        return BffFeignCall.call("goods-center", "分类暂不可用，请稍后重试",
                () -> goodsCenterClient.categoryTree());
    }

    public PageResult<MallGoodsItemVO> goods(MallGoodsPageQueryDTO dto) {
        List<CategoryTreeVO> tree = categoryTreeOrEmpty();
        StoreGoodsSpuCrossShopPageQueryDTO q = new StoreGoodsSpuCrossShopPageQueryDTO();
        q.setPageNum(dto.getPageNum());
        q.setPageSize(dto.getPageSize());
        q.setKeyword(dto.getKeyword());
        q.setShopStatus(SHOP_STATUS_APPROVED);
        q.setShelfStatus(SHELF_ON);
        q.setLockStatus(LOCK_OFF);
        q.setSort(dto.getSort());
        q.setBrandIds(dto.getBrandIds());
        q.setCategoryIds(resolveCategoryIds(tree, dto.getCategoryId(), dto.getCategoryIds()));
        PageResult<StoreGoodsSpuCrossShopPageItemVO> raw =
                BffFeignCall.call("store", DOWN_MSG, () -> storeClient.pageStoreGoodsCrossShop(q));
        PageResult<MallGoodsItemVO> result = new PageResult<>();
        result.setTotal(raw.getTotal());
        result.setRecords(raw.getRecords().stream().map(this::toMallItem).collect(Collectors.toList()));
        return result;
    }

    public MallFacetVO facets(MallFacetQueryDTO dto) { … }
}
```

- `resolveCategoryIds(tree, anchorId, selectedIds)`：
  - `selectedIds` 非空 → 各自 `subtreeIds(tree, id)` 并集去重
  - 否则 `anchorId` 非空 → `subtreeIds(tree, anchorId)`
  - 否则 → `null`（不按分类过滤）
- `subtreeIds(tree, id)`：在树中找该节点，深度优先收集自身 + 全部后代 id
- `toMallItem(域 VO)`：逐字段手工映射，**不透传** `lockReason`/`lockUser`/`goodsSpuId`/`categoryPath`/`skuCount`/`updateTime`
- `facets(...)`：
  - `scopeCategoryIds` = `anchorId` 的子树（无锚点则空）
  - `filterCategoryIds` = 已选分类各自子树并集
  - 调 `storeClient.mallFacets(...)`
  - `brands` 原样映射
  - `categories`：**有锚点（分类页）→ 原样映射；无锚点（搜索页）→ 用树把每个 id 上溯到顶级祖先，同祖先的 count 累加**，名称取树里的权威名
- ⚠ **`BffFeignCall` 的语义是「抛 `ServiceException`」，不是「返回降级值」**（`call` 无任何 fallback 返回值，4xx 原样透传、其余一律抛 `ServiceException(500, 降级文案)`）。所以「树拿不到就退化」**不会自动发生**，必须自己兜：
  - `categories()`：直接让异常抛出（首页宫格是主内容，降级成空块即可，前端整块不渲染）
  - `goods()` / `facets()`：树是**增强**（用于子树展开与筛选名解析），拿不到不应拖垮主流程 → 用
    ```java
    /** 取分类树；任何异常（含 BffFeignCall 抛出的 ServiceException）都吞掉并返回空表 */
    private List<CategoryTreeVO> categoryTreeOrEmpty() {
        try {
            return goodsCenterClient.categoryTree();
        } catch (Exception e) {
            log.warn("分类树获取失败，本次按无树降级（不展开子树、facet 原样输出）", e);
            return Collections.emptyList();
        }
    }
    ```
  - 拿到空表时：`resolveCategoryIds` 退化为「只用调用方传的 id 本身，不展开子树」；`categories` facet 退化为原样映射（不做顶级上溯）。为此 `subtreeIds`/上溯函数在树为空时必须**安全退化**而不是抛异常。
- `PageResult` 用 `com.panoramic.common.store.vo.PageResult`（与前端分页形状同源）。

- [ ] **Step 6: 建 `CatalogController`**

```java
@RestController
@RequestMapping("/catalog")
@RequiredArgsConstructor
public class CatalogController {

    private final CatalogBffService catalogBffService;

    /** 全量分类树（首页宫格 / 分类页标题 / 分类筛选名解析共用） */
    @GetMapping("/categories")
    public RespData<List<CategoryTreeVO>> categories() {
        return RespData.success(catalogBffService.categories());
    }

    /** 商品分页（C 端展示口径由 BFF 固定） */
    @PostMapping("/goods")
    public RespData<PageResult<MallGoodsItemVO>> goods(@RequestBody MallGoodsPageQueryDTO dto) {
        return RespData.success(catalogBffService.goods(dto));
    }

    /** 筛选维度聚合（分类 / 品牌） */
    @PostMapping("/facets")
    public RespData<MallFacetVO> facets(@RequestBody MallFacetQueryDTO dto) {
        return RespData.success(catalogBffService.facets(dto));
    }
}
```

> ⚠ 本 controller **不得有** `@PreAuthorize`（C 端不接 RBAC）。
> `RespData` 只有 `@Getter` 无 setter，只能用静态工厂 `RespData.success(data)`（**不是 `ok`**）。

- [ ] **Step 7: 编译**

```bash
cd /e/workspace/panoramic_mall
/e/tools/apache-maven-3.9.16/bin/mvn -q -pl backend/mall-bff -am compile
```

Expected: BUILD SUCCESS。

- [ ] **Step 8: 文本级核对白名单两处**

```bash
cd /e/workspace/panoramic_mall
grep -n "whitelist-paths" backend/mall-bff/src/main/resources/application.yml
grep -n "whitelist-paths" backend/gateway/src/main/resources/application.yml
```

Expected: mall-bff 含 `/catalog/**`；gateway 含 `/mall/catalog/**`（gateway 的改动在 Task 11，此处仅确认 mall-bff 一侧已加）。

- [ ] **Step 9: 提交**

```bash
git add backend/mall-bff
git commit -m "mall-bff：启用 Feign，新增 catalog 编排（分类树 / 商品分页 / 筛选聚合）

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

## Task 8: mall 前端 · catalog 基础设施与组件

**Files:**
- Create: `frontend/mall/src/types/api.ts`、`frontend/mall/src/types/catalog.ts`
- Create: `frontend/mall/src/api/catalog.ts`
- Create: `frontend/mall/src/components/Pager.vue`、`FilterRow.vue`
- Create: `frontend/mall/src/styles/catalog.css`
- Modify: `frontend/mall/src/components/GoodsCard.vue`（改紧凑版，接收新形状）

**Interfaces:**
- Consumes: Task 7 的三个页面接口。
- Produces:
  - `catalogApi.categories(): Promise<CategoryNode[]>`
  - `catalogApi.goods(body: GoodsPageQuery): Promise<PageResult<GoodsListItem>>`
  - `catalogApi.facets(body: FacetQuery): Promise<FacetResult>`
  - 组件 `Pager` props `{ total: number; pageSize: number; page: number }` emits `change(page)`
  - 组件 `FilterRow` props `{ title: string; items: FacetItem[]; selected: number[] }` emits `toggle(id)`
  - `GoodsCard` props `{ goods: GoodsListItem }`

- [ ] **Step 1: 建 `types/api.ts`**

```ts
/** 后端分页结果形状（与 common 的 PageResult 同源） */
export interface PageResult<T> {
  total: number
  records: T[]
}
```

- [ ] **Step 2: 建 `types/catalog.ts`**

```ts
/** 分类树节点（对应后端 CategoryTreeVO） */
export interface CategoryNode {
  id: number
  parentId: number
  name: string
  level: number
  sort: number
  /** 图标图片 URL，可空 —— 空或加载失败时前端回退 CSS 渐变占位 */
  icon: string | null
  children: CategoryNode[] | null
}

/** 列表页商品项（对应后端 MallGoodsItemVO） */
export interface GoodsListItem {
  id: number
  name: string
  mainImage: string | null
  minPrice: number | null
  storeId: number
  storeName: string
  categoryId: number | null
  categoryName: string | null
  brandId: number | null
  brandName: string | null
}

/** 筛选维度的可选项 */
export interface FacetItem {
  id: number
  name: string
  count: number
}

export interface FacetResult {
  categories: FacetItem[]
  brands: FacetItem[]
}

export type SortKey = 'default' | 'priceAsc' | 'priceDesc'

export interface GoodsPageQuery {
  keyword?: string
  categoryId?: number
  categoryIds?: number[]
  brandIds?: number[]
  sort?: SortKey
  pageNum: number
  pageSize: number
}

export interface FacetQuery {
  keyword?: string
  categoryId?: number
  categoryIds?: number[]
  brandIds?: number[]
}
```

- [ ] **Step 3: 建 `api/catalog.ts`**

```ts
import { request } from './request'
import type { PageResult } from '../types/api'
import type { CategoryNode, FacetQuery, FacetResult, GoodsListItem, GoodsPageQuery } from '../types/catalog'

// ⚠ 路径必须带 /mall 前缀：mall 前端是直连网关的（与 auth.ts 里 '/mall/auth/login' 同一口径）。
// gateway 对该前缀做 StripPrefix=1，落到 mall-bff 时是 /catalog/...（服务本地白名单不带前缀）。

/** 全量分类树（首页宫格 / 分类页标题 / 筛选名解析共用） */
function categories(): Promise<CategoryNode[]> {
  return request.get<CategoryNode[]>('/mall/catalog/categories')
}

/** 商品分页 */
function goods(body: GoodsPageQuery): Promise<PageResult<GoodsListItem>> {
  return request.post<PageResult<GoodsListItem>>('/mall/catalog/goods', body)
}

/** 筛选维度聚合 */
function facets(body: FacetQuery): Promise<FacetResult> {
  return request.post<FacetResult>('/mall/catalog/facets', body)
}

// 导出形态对齐同目录的 auth.ts（`export const authApi = { ... }`）
export const catalogApi = { categories, goods, facets }
```

> ⚠ 上面前缀与导出形态已按 `frontend/mall/src/api/auth.ts` 与 `request.ts` 的实际写法核对过（`request` 是具名导出、`get<T>` 返回已剥壳的 `T`）。落地时若发现仍有出入，**照 auth.ts 改**。

- [ ] **Step 4: 建 `components/Pager.vue`**

自己写分页条（mall 不注册 element-plus），props `{ total, pageSize, page }`、emit `change`。显示：上一页 / 页码（最多 7 个，当前页居中，首尾保留）/ 下一页。样式只用 tokens 令牌。

- [ ] **Step 5: 建 `components/FilterRow.vue`**

一行筛选维度：标题 + 若干可点选项（chips，显示 `名称 数量`），选中态高亮，点击 emit `toggle(id)`。空数组时整行不渲染（`v-if`）。

- [ ] **Step 6: 改 `components/GoodsCard.vue` 为紧凑版**

- props 改为 `{ goods: GoodsListItem }`
- 主图：`<img v-if="goods.mainImage" :src="goods.mainImage" @error="onImgError">`，`imgFailed` 为 true 或 `mainImage` 为空时渲染现有渐变占位（色相由 `goods.id` 取模派生：`hue = goods.id % 360`），占位文字取商品名首字
- 名称 2 行截断（沿用 `clamp-2`）
- 价格：`¥{{ minPrice }} 起`，`minPrice` 为 null 时显示「价格待定」
- 底部一行：店铺名（单行截断）
- **删除** 原价 / 销量 / 角标相关的模板与 `priceParts`/`tagClass`/`trimNum` 中不再使用的引用（`noUnusedLocals` 会报错）

- [ ] **Step 7: 建 `styles/catalog.css`**

列表页与卡片样式：1280 容器、7 列网格（`grid-template-columns: repeat(7, 1fr)`）、筛选行、排序条、分页条、紧凑卡片。**色值/圆角/阴影一律用 `var(--*)` 令牌**（`tokens.css` 里有 `--container`、`--r-md`、`--sh-sm`、`--s-*`、`--t-*`、`--brand*`、`--n*` 等，先 `grep` 一遍变量名再写）。

⚠ 引入位置：本项目所有样式都在 `src/main.ts` 里**按序** import（`tokens → base → mall → account`），**没有**组件内 import css 的先例。所以要在 `main.ts` 第 6 行之后追加：

```ts
import './styles/catalog.css'
```

保持 `tokens.css` 仍是第一个（`base.css` 依赖它先加载）。

- [ ] **Step 8: 类型检查**

```bash
cd /e/workspace/panoramic_mall/frontend/mall && npm run type-check
```

Expected: 无错误。（此时 `GoodsCard` 的旧引用方 `GoodsGrid.vue` 会因 props 形状变化报错——**Step 9 一并处理**，若报错先继续。）

- [ ] **Step 9: 让首页热门商品区继续可用**

首页 `GoodsGrid.vue` 仍用 `src/mock/goods.ts`。两种做法二选一，**取第一种**：

1. `GoodsGrid.vue` 内部把 mock 行映射成 `GoodsListItem` 形状后再传给 `GoodsCard`（`mainImage: null`、`minPrice: price`、`storeName: ''`、`categoryName: null`、`brandId: null`、`brandName: null`），模板与样式零改动；
2. 给 `GoodsCard` 加 `variant` prop 区分两套渲染。

选 1 的理由：热门商品区本次不改，映射层最薄。

- [ ] **Step 10: 类型检查（复核）**

```bash
cd /e/workspace/panoramic_mall/frontend/mall && npm run type-check
```

Expected: 零错误。

- [ ] **Step 11: 提交**

```bash
git add frontend/mall
git commit -m "mall：catalog 前端基础设施（类型/api/分页/筛选行/紧凑商品卡）

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

## Task 9: mall 前端 · 列表页与路由

**Files:**
- Create: `frontend/mall/src/views/GoodsListView.vue`
- Modify: `frontend/mall/src/router/index.ts`

**Interfaces:**
- Consumes: Task 8 的 api / 类型 / 组件。
- Produces: 路由 `/search`、`/category/:categoryId`

- [ ] **Step 1: 加路由**

`router/index.ts` 加两条（沿用既有的懒加载写法）：

```ts
{ path: '/search', name: 'search', component: () => import('../views/GoodsListView.vue') },
{ path: '/category/:categoryId', name: 'category', component: () => import('../views/GoodsListView.vue') },
```

路由**不加** `meta.requiresAuth`（公开可浏览）。

- [ ] **Step 2: 建 `GoodsListView.vue`**

结构：

```
TopBar
搜索区：复用 SearchBar（搜索页可改词重搜；分类页显示分类名 + 「全部分类」链接）
FilterRow 分类（多选）
FilterRow 品牌（多选）
排序条：共 N 件 | 综合 / 价格 ↑ / 价格 ↓
7 列网格（GoodsCard）
Pager（每页 49）
SiteFooter
```

逻辑要点：

- 模式判定：`route.path.startsWith('/category/')` → 分类页，锚点 `Number(route.params.categoryId)`；否则搜索页，关键词 `route.query.keyword`
- 筛选状态**全部放进 URL query**（`categoryIds` / `brandIds` 用逗号分隔的字符串，`sort`、`page`），刷新与分享不丢
- 三个请求：进页面拉一次 `catalogApi.categories()` 缓存（供解析分类名与「全部分类」链接）；`catalogApi.goods()` 与 `catalogApi.facets()` 随 query 变化重新拉
- 分类页：分类筛选行的 items 直接用 facets 返回的 `categories`（后端已按锚点范围输出子分类）；搜索页：也是 `categories`（后端已上溯到顶级）——**前端两种模式渲染逻辑一致**，差异全在 BFF
- 空结果：提示「没有找到相关商品」+ 返回首页按钮
- 加载中：骨架或文本占位

- [ ] **Step 3: 类型检查**

```bash
cd /e/workspace/panoramic_mall/frontend/mall && npm run type-check
```

Expected: 零错误。

- [ ] **Step 4: 构建（类型门禁 + 产物）**

```bash
cd /e/workspace/panoramic_mall/frontend/mall && npm run build
```

Expected: `vue-tsc --noEmit` 通过且 `vite build` 产出 `dist/`。

- [ ] **Step 5: 提交**

```bash
git add frontend/mall
git commit -m "mall：新增搜索结果页与分类商品页（共用一套列表骨架）

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

## Task 10: mall 前端 · 首页搜索框与分类宫格接入

**Files:**
- Modify: `frontend/mall/src/components/SearchBar.vue`
- Modify: `frontend/mall/src/components/CategoryGrid.vue`
- Delete: `frontend/mall/src/mock/categories.ts`

**Interfaces:**
- Consumes: Task 8 的 `catalogApi.categories()`、类型 `CategoryNode`。
- Produces: 首页两个区块接真实数据。

- [ ] **Step 1: 改 `SearchBar.vue` 真跳转**

- 删掉 `hint` 那句「演示：搜索未接入」以及 `search__hint` 提示区（或改为无关键词时的轻提示）
- `doSearch()` 改为 `router.push({ path: '/search', query: kw ? { keyword: kw } : {} })`
- 点热搜词：回填输入框后同样跳转；热搜词数组**保持现有静态 mock**（本次忽略该需求），删掉「仅提示」的注释

- [ ] **Step 2: 改 `CategoryGrid.vue` 接真实分类树**

- `onMounted` 调 `catalogApi.categories()`，取 `level === 1` 的顶级分类渲染
- 删掉对 `../mock/categories` 的 import
- 图标：有 `icon` 渲染 `<img>`（加载失败或为空 → 回退现有渐变圆 + 名称首字，色相按 `id % 360` 派生）
- 点击 → `router.push(`/category/${c.id}`)`
- 列数动态：容器加 `:style="{ '--cols': Math.min(list.length, 10) }"`，CSS 用 `grid-template-columns: repeat(var(--cols), 1fr)`，保住「一行铺满」的基准又不写死 10
- 加载失败（BFF 降级）时整块不渲染，不留空白骨架

- [ ] **Step 3: 删除 mock 分类**

```bash
cd /e/workspace/panoramic_mall
git rm frontend/mall/src/mock/categories.ts
grep -rn "mock/categories" frontend/mall/src || echo "无残留引用 ✓"
```

- [ ] **Step 4: 构建**

```bash
cd /e/workspace/panoramic_mall/frontend/mall && npm run build
```

Expected: 通过。

- [ ] **Step 5: 提交**

```bash
git add frontend/mall
git commit -m "mall：首页搜索框真跳转、分类宫格接真实分类树（含图标）

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

## Task 11: 契约与文档同步

**Files:**
- Modify: `docs/contracts/store.md`、`mall-bff.md`、`gateway.md`、`cross-cutting.md`、`admin.md`
- Modify: `CLAUDE.md`（根）
- Modify: `backend/store/README.md`、`backend/mall-bff/README.md`（若存在）

- [ ] **Step 1: `gateway.md` 免鉴权表补一行**

网关侧表加 `/mall/catalog/**`（说明：C 端商品浏览公开）；服务本地侧表 mall-bff 那行的路径补 `/catalog/**`。

- [ ] **Step 2: `gateway/src/main/resources/application.yml` 加白名单**

`whitelist-paths` 追加 `,/mall/catalog/**`。

- [ ] **Step 3: `store.md` 更新**

- 第二节接口表：`platformPageStoreGoods` 行改为 `pageStoreGoodsCrossShop` / `POST` / `/goods/cross-shop/spu/page` / `StoreGoodsSpuCrossShopPageQueryDTO` / `StoreGoodsSpuCrossShopPageItemVO`，调用方列改为 `ShopGoodsBffService(admin), CatalogBffService(mall-bff)`
- 新增 `mallFacets` 行：`POST` / `/goods/facets` / `StoreGoodsSpuFacetQueryDTO` / `StoreGoodsSpuFacetVO` / `CatalogBffService(mall-bff)`
- 「共 18 个接口（owner 10 + platform 8）」改为实际条数并重述「跨店通用」侧
- 第三节 owner/platform 表：把该分页从 platform 行移出，单列一节说明「跨店通用（无锚点）——调用方自设限定条件；C 端固定传 shopStatus=2 + shelfStatus=1 + lockStatus=0」
- 第五节类型表补新类型
- 补一条形状说明：facets 的两维度互斥口径

- [ ] **Step 4: `mall-bff.md` 更新**

- 接口清单 5 条 → 8 条，加 `/catalog/categories`、`/catalog/goods`、`/catalog/facets`（权限串均为空）
- 免鉴权路径行改为含 `/catalog/**`
- 「内部依赖：**一期没有**」一段改写为已接 goods-center 与 store
- 补充「C 端展示口径由 BFF 固定」的说明

- [ ] **Step 5: `cross-cutting.md` 新增条目**

新增三条（编号顺延）：
1. **C 端商品展示口径在端 BFF，不在域**：mall-bff 调 store 域商品查询时固定传 `shopStatus=2` + `shelfStatus=1` + `lockStatus=0`；域侧不含 C 端隐含约束。新增 C 端查询时必须保持这三个条件。
2. **筛选维度聚合「排除自身维度」**：分类维度不受已选分类影响、品牌维度不受已选品牌影响；否则选中后同维度选项消失。
3. **跨店分页通用化**：store 域 `/goods/cross-shop/spu/page` 由 admin BFF 与 mall-bff 共用，调用方自设限定条件；域返回的 VO 含管理端字段（`lockUser` 等），**C 端输出前必须由 BFF 裁剪**。

- [ ] **Step 6: `admin.md` 更新**

「店铺商品管理」分页那行的入参描述：品牌由单选改多值（`brandIds`）。

- [ ] **Step 7: 根 `CLAUDE.md` 更新**

- store 域段：补「跨店通用」侧与 `min_price` 不变量（`refreshDerived` 是推导量统一刷新入口）；明确 owner 侧不动的原因
- mall 前端段：「不引外部图片与字体」改为「**数据驱动**的图片（分类图标等）可由后端 URL 提供；**前端源码内**不写死外链、不引外链字体、不接外链图床」
- 补一句 mall 列表页形态（1280 固定容器 + 7 列 + 每页 49）

- [ ] **Step 8: `backend/store/README.md` 更新**

补 `min_price` 推导不变量与 facets 口径；把 `refreshShelfStatus` 的表述改为「`refreshDerived` 是推导量统一刷新入口，内含 `refreshShelfStatus` 与 `refreshMinPrice` 两个不变量写者」。

- [ ] **Step 9: 跑契约检查器**

```bash
cd /e/workspace/panoramic_mall && node docs/contracts/drift-check.mjs; echo "exit=$?"
```

Expected: `exit=0`。非 0 按输出逐条修正后再跑。

- [ ] **Step 10: 提交**

```bash
git add docs CLAUDE.md backend/store/README.md backend/gateway/src/main/resources/application.yml
git commit -m "契约与文档同步：catalog 三接口、跨店分页通用化、C 端口径、分类 icon 约定

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

## Task 12: 数据灌入（批量上架 + 新灌一家店）

**Files:** 无仓库文件产物（脚本放 `temp/2026-09-17-catalog-backup/`）

**Interfaces:**
- Consumes: Task 2 的 `min_price` 列。
- Produces: C 端列表有可展示的数据。

- [ ] **Step 1: 批量上架 `store_id=5` 的商品**

目标：让大部分 SPU 上架、且价格分布有层次（便于验价格排序）。

```bash
node "C:/Users/lisi/.claude/skills/mysql-connect/scripts/query.mjs" --write --database panoramic_mall \
 "UPDATE store_goods_sku k JOIN store_goods_spu s ON s.id = k.spu_id
  SET k.shelf_status = 1
  WHERE s.store_id = 5 AND s.is_delete = 0 AND s.lock_status = 0
    AND k.is_delete = 0 AND (k.id % 4) <> 0;"
```

（每 SPU 留 1 个 SKU 不上架，避免全部铺满，也保留「部分上架」的真实形态。）

- [ ] **Step 2: 用同一口径重算两个推导量（保不变量）**

```bash
node "C:/Users/lisi/.claude/skills/mysql-connect/scripts/query.mjs" --write --database panoramic_mall \
 "UPDATE store_goods_spu s
  SET s.shelf_status = CASE WHEN EXISTS (
        SELECT 1 FROM store_goods_sku k
        WHERE k.spu_id = s.id AND k.is_delete = 0 AND k.shelf_status = 1) THEN 1 ELSE 0 END,
      s.min_price = (SELECT MIN(k.price) FROM store_goods_sku k
        WHERE k.spu_id = s.id AND k.is_delete = 0 AND k.shelf_status = 1)
  WHERE s.is_delete = 0 AND s.lock_status = 0;"
```

- [ ] **Step 3: 复核不变量**

```bash
node "C:/Users/lisi/.claude/skills/mysql-connect/scripts/query.mjs" --database panoramic_mall \
 "SELECT
   (SELECT COUNT(*) FROM store_goods_spu s WHERE s.is_delete=0 AND ((s.shelf_status=1 AND NOT EXISTS (SELECT 1 FROM store_goods_sku k WHERE k.spu_id=s.id AND k.is_delete=0 AND k.shelf_status=1)) OR (s.shelf_status=0 AND EXISTS (SELECT 1 FROM store_goods_sku k WHERE k.spu_id=s.id AND k.is_delete=0 AND k.shelf_status=1)))) AS shelf_违规,
   (SELECT COUNT(*) FROM store_goods_spu s WHERE s.is_delete=0 AND s.lock_status=0 AND ABS(COALESCE(s.min_price,-1) - COALESCE((SELECT MIN(k.price) FROM store_goods_sku k WHERE k.spu_id=s.id AND k.is_delete=0 AND k.shelf_status=1),-1)) > 0.001) AS price_违规;"
```

Expected: 两项均为 `0`。

- [ ] **Step 4: 新灌一家店（`store_shop` id=7）+ 商品**

先确认 id=7 未被占用：

```bash
node "C:/Users/lisi/.claude/skills/mysql-connect/scripts/query.mjs" --database panoramic_mall "SELECT id, shop_name, status FROM store_shop ORDER BY id;"
```

插入店铺（状态直接为 2 已通过，便于 C 端展示）：

```bash
node "C:/Users/lisi/.claude/skills/mysql-connect/scripts/query.mjs" --write --database panoramic_mall \
 "INSERT INTO store_shop (id, shop_name, logo, intro, contact_name, contact_phone, region, address, license_name, license_no, license_img, status, submit_time, audit_by, audit_time, create_user, create_time, update_user, update_time, is_delete)
  VALUES (7, '云栖数码专营店', NULL, '数码配件与智能硬件专营', '云栖', '18000000007', '浙江省杭州市', '西湖区文三路 100 号', '云栖数码有限公司', 'YQSM2026', NULL, 2, NOW(), 1, NOW(), 'store:7', NOW(), 'store:7', NOW(), 0);"
```

复制商品：把 `store_id=5` 的 SPU 抽 12 个复制给 `store_id=7`（分类打散到多个顶级下），SKU 一并复制：

```bash
node "C:/Users/lisi/.claude/skills/mysql-connect/scripts/query.mjs" --write --database panoramic_mall \
 "INSERT INTO store_goods_spu (store_id, goods_spu_id, center_version, name, category_id, category_name, brand_id, brand_name, main_image, image_list, description, spec_config, shelf_status, lock_status, min_price, create_user, create_time, update_user, update_time, is_delete)
  SELECT 7, goods_spu_id, center_version, CONCAT(name, '（云栖）'), category_id, category_name, brand_id, brand_name, main_image, image_list, description, spec_config, 1, 0, NULL, 'store:7', NOW(), 'store:7', NOW(), 0
  FROM store_goods_spu WHERE store_id = 5 AND is_delete = 0 ORDER BY id LIMIT 12;"
```

复制其 SKU。新行的 id 无法用偏移推算（插入的是新自增 id），只能按业务字段一对一映射——
上一步给新行名字加了「（云栖）」后缀，正是用来做这个映射键。

⚠ **先验证映射键唯一**（源码店 5 若存在同名 SPU，下面的 join 会退化成笛卡尔积、SKU 成倍复制）：

```bash
node "C:/Users/lisi/.claude/skills/mysql-connect/scripts/query.mjs" --database panoramic_mall \
 "SELECT name, COUNT(*) AS 份数 FROM store_goods_spu WHERE store_id = 5 AND is_delete = 0 GROUP BY name HAVING 份数 > 1;"
```

Expected: **空结果**。若非空，停止本步并改用「按 id 显式配对」的写法（把 5 的 id 与 7 的 id 按序配对成 VALUES 列表），不要硬跑下面的 join。

```bash
node "C:/Users/lisi/.claude/skills/mysql-connect/scripts/query.mjs" --write --database panoramic_mall \
 "INSERT INTO store_goods_sku (spu_id, spec_attrs, sku_code, main_image, price, shelf_status, create_user, create_time, update_user, update_time, is_delete)
  SELECT n.id, k.spec_attrs, k.sku_code, k.main_image, k.price, k.shelf_status, 'store:7', NOW(), 'store:7', NOW(), 0
  FROM store_goods_sku k
  JOIN store_goods_spu o ON o.id = k.spu_id
  JOIN store_goods_spu n ON n.store_id = 7 AND n.name = CONCAT(o.name, '（云栖）')
  WHERE o.store_id = 5 AND k.is_delete = 0;"
```

- [ ] **Step 5: 重算新店的推导量**

```bash
node "C:/Users/lisi/.claude/skills/mysql-connect/scripts/query.mjs" --write --database panoramic_mall \
 "UPDATE store_goods_spu s
  SET s.shelf_status = CASE WHEN EXISTS (SELECT 1 FROM store_goods_sku k WHERE k.spu_id=s.id AND k.is_delete=0 AND k.shelf_status=1) THEN 1 ELSE 0 END,
      s.min_price = (SELECT MIN(k.price) FROM store_goods_sku k WHERE k.spu_id=s.id AND k.is_delete=0 AND k.shelf_status=1)
  WHERE s.store_id = 7;"
```

- [ ] **Step 6: 终态复核（C 端口径能看到多少商品）**

```bash
node "C:\Users\lisi\.claude\skills\mysql-connect\scripts\query.mjs" --database panoramic_mall \
 "SELECT sh.id AS 店铺, sh.shop_name, COUNT(*) AS 在售商品数
  FROM store_goods_spu s JOIN store_shop sh ON sh.id = s.store_id
  WHERE s.is_delete=0 AND s.shelf_status=1 AND s.lock_status=0 AND sh.status=2 AND sh.is_delete=0
  GROUP BY sh.id, sh.shop_name;"
```

Expected: **2 家店**都有在售商品（这是本次关键验收点：跨店混排有内容）。

- [ ] **Step 7: 复核分类分布（确保筛选器有多个分类可展示）**

```bash
node "C:\Users\lisi\.claude\skills\mysql-connect\scripts\query.mjs" --database panoramic_mall \
 "SELECT c.id AS 顶级分类, c.name, COUNT(*) AS 在售商品数
  FROM store_goods_spu s
  JOIN goods_category sub ON sub.id = s.category_id
  JOIN goods_category c ON c.id = CASE WHEN sub.parent_id = 0 THEN sub.id ELSE sub.parent_id END
  JOIN store_shop sh ON sh.id = s.store_id
  WHERE s.is_delete=0 AND s.shelf_status=1 AND s.lock_status=0 AND sh.status=2 AND sh.is_delete=0
  GROUP BY c.id, c.name ORDER BY 在售商品数 DESC;"
```

Expected: 至少 3~4 个顶级分类有商品（搜索结果页的分类筛选器才有东西可展示）。

> ⚠ 本任务不动任何仓库文件，**不产生提交**。所有脚本落在 `temp/` 下，不进 git。

---

## Task 13: 终验与提交

- [ ] **Step 1: 全量后端编译**

```bash
cd /e/workspace/panoramic_mall
/e/tools/apache-maven-3.9.16/bin/mvn -q -N install
/e/tools/apache-maven-3.9.16/bin/mvn -q -pl backend/common,backend/store,backend/goods-center,backend/admin,backend/mall-bff,backend/gateway -am compile
```

Expected: BUILD SUCCESS。

> 若报「本地仓库 common stale」或「运行中 jar 锁」，按既有经验：停掉相关进程 → `-am clean package`。

- [ ] **Step 2: 三端前端构建**

```bash
cd /e/workspace/panoramic_mall/frontend/mall && npm run build
cd /e/workspace/panoramic_mall/frontend/admin && npm run build
cd /e/workspace/panoramic_mall/frontend/store && npm run build
```

Expected: 三者均通过（`vue-tsc --noEmit` 是构建门禁）。

- [ ] **Step 3: 契约检查器**

```bash
cd /e/workspace/panoramic_mall && node docs/contracts/drift-check.mjs; echo "exit=$?"
```

Expected: `exit=0`。

- [ ] **Step 4: 文本级核对清单**

```bash
cd /e/workspace/panoramic_mall
echo "--- 1. 禁用写法残留（应为空）---"
grep -rn "setCreateUser\|setUpdateUser\|setCreateTime\|setUpdateTime" backend --include=*.java | grep -v /target/
echo "--- 2. 域内鉴权残留（应为空）---"
grep -rn "@PreAuthorize" backend/store/src backend/goods-center/src --include=*.java | grep -v /target/
echo "--- 3. mall-bff 不得有 @PreAuthorize（应为空）---"
grep -rn "@PreAuthorize" backend/mall-bff/src --include=*.java
echo "--- 4. 旧类型名残留（应为空）---"
grep -rn "StoreGoodsSpuPlatformPage" backend frontend --include=*.java --include=*.ts --include=*.vue | grep -v /target/ | grep -v node_modules
echo "--- 5. 前端 any/ts-ignore 残留（应为空）---"
grep -rn ": any\|as any\|@ts-ignore\|@ts-nocheck" frontend/mall/src frontend/admin/src | grep -v node_modules
echo "--- 6. mock/categories 残留（应为空）---"
grep -rn "mock/categories" frontend/mall/src
echo "--- 7. 免鉴权两处 ---"
grep -n "whitelist-paths" backend/gateway/src/main/resources/application.yml
grep -n "whitelist-paths" backend/mall-bff/src/main/resources/application.yml
```

Expected: 1–6 全空；7 里 gateway 含 `/mall/catalog/**`、mall-bff 含 `/catalog/**`。

- [ ] **Step 5: 确认无 `.superpowers/` 与 `temp/` 进入版本控制**

```bash
cd /e/workspace/panoramic_mall
git status --porcelain | grep -E "superpowers|temp/" || echo "无污染 ✓"
git check-ignore -v .superpowers temp 2>/dev/null || echo "⚠ 未忽略，需补 .gitignore"
```

若未忽略，把 `.superpowers/` 与 `temp/` 追加进 `.gitignore` 并提交。

- [ ] **Step 6: 确认工作区干净**

```bash
cd /e/workspace/panoramic_mall && git status --porcelain && git log --oneline -8
```

Expected: 日志含本计划的各次提交。工作区**不要求全空**——开工前就存在 4 个与本计划无关的未暂存删除（`docs/superpowers/specs/2026-09-14-*.md`，非本次产生），**不要**把它们一并提交或还原，原样留着。

- [ ] **Step 7: 汇报**

向用户汇总：做了什么、我自行裁决的取舍、以及 merge/PR 选项。**不擅自 merge**。

裁决项至少包含：
- 页面级接口用 POST 做查询（此前无先例，理由是集合入参的序列化）
- `apply("1 = 0")` 的替代写法（若用了）
- facet 聚合改用字符串列名的 `QueryWrapper`（仓库里唯一一处）
- 热门商品区通过「mock → GoodsListItem 映射层」保持不动
- 测试数据的具体规模与店铺 id=7 的选号

---

## Self-Review

**Spec 覆盖检查**：spec 十一节逐条对应——目标 1↔Task 10、目标 2↔Task 10、目标 3↔Task 9、目标 4↔Task 5+7+9、目标 5↔Task 4+9；非目标（详情页/热门商品/owner 侧/ES/缓存/热搜）在计划中均未触碰；§5.1↔Task 4、§5.2↔Task 5、§5.3↔Task 3、§5.4↔Task 6、§6↔Task 7、§7↔Task 8-10、§8↔Task 11、§9↔Task 1+2+12、§10↔Task 13、§11 风险↔各任务里的 ⚠ 标注。

**占位符扫描**：无 TBD / TODO / 「类似 Task N」；每个代码步骤均给出可落地的内容。少数「按既有写法照做」的指引（`RespData` 构造、`request` 导入形态、`account.css` 引入方式）是**刻意的**——这些点的准确写法依赖仓库现状，写死反而会错，由执行者读一眼源码确认。

**类型一致性**：`StoreGoodsSpuCrossShopPageQueryDTO` / `StoreGoodsSpuCrossShopPageItemVO` / `StoreGoodsSpuFacetQueryDTO` / `StoreGoodsSpuFacetVO` / `StoreGoodsFacetItemVO` / `MallGoodsItemVO` / `MallFacetVO` / `MallFacetItemVO` / `MallGoodsPageQueryDTO` / `MallFacetQueryDTO` 在 Task 4/5/7 中的命名与字段前后一致；`refreshDerived` / `refreshShelfStatus` / `refreshMinPrice` / `idListByStatus` / `minPriceBySpuId` / `pageStoreGoodsCrossShop` / `mallFacets` / `crossShopPage` 在定义任务与调用任务中同名同参。
