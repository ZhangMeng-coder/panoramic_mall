# admin / store 架构拉平到 Vue 3 + TypeScript（设计）

> 三端前端（admin / store / mall）**技术形态拉平**：admin 与 store 从 Vue 3 + JS
> 升到 Vue 3 + **TypeScript（strict）**，工具链与 `frontend/mall` 完全一致。
> 这是「统一前端架构」的第二半 —— 第一半（mall 接入账号接口）已完成并提交 `8908222`。

## 一、目标与非目标

### 目标

1. **工具链与 mall 一字不差**：tsconfig 照抄、`vue-tsc` 进构建、`.js` 全量转 `.ts`。
2. **类型系统真的干活**：`api/` 层的入参出参、以及被多处共用的公共形状有类型；
   类型错误清零（**不靠放松 tsconfig 清零**）。
3. axios 已是三端统一（admin / store 本来就是，mall 本次已加），本设计不改请求库。

### 非目标（YAGNI）

- ❌ **不引入 monorepo / 共享包**：三端各有自己的 `package.json` 与 `node_modules`，
  现在 admin 与 store 的 `api/request.js` 就是**逐字节相同的两份**，这次拉平不顺手做去重 ——
  那需要 workspace 或本地包，是另一个话题（见「风险」里记的一笔）。
- ❌ **不为每个接口建完整 DTO 模型**：按用户决定，只给「被 ≥2 处共用的公共形状」建类型，
  单页私有的表单对象靠推断（`ref<XxxForm | null>(null)`）。
- ❌ **不改任何业务行为**：不重构组件、不改样式、不动接口路径与字段。
  这次是**类型层改造**，diff 里不应出现行为变化。
- ❌ **不做 Vue 2 → Vue 3 或 Options → Composition 的改造**：两端本来就都是
  `<script setup>`。
- ❌ 不给 mall 再做一遍（它已经是 TS）。

## 二、现状（事实）

| 前端 | 栈 | tsconfig | 构建 | 规模 |
|---|---|---|---|---|
| `mall` | Vue 3 + **TS**(strict) | ✅ | `vue-tsc --noEmit && vite build` | 10 SFC + 12 ts |
| `admin` | Vue 3 + **JS** | ❌ | `vite build` | **25 SFC + 14 js，5312 行** |
| `store` | Vue 3 + **JS** | ❌ | `vite build` | **10 SFC + 8 js，2800 行** |

- 35 个 SFC **全是 `<script setup>`，无一带 `lang="ts"`**。
- 两端的 `api/request.js` **逐字节相同**；两端的 `api/` 都只有 `request.js` 一个 axios 入口，
  全仓库前端**无 `fetch` / `XMLHttpRequest`**。
- 改造面（strict 下会直接报错的写法）：

| 写法 | admin | store | 说明 |
|---|---|---|---|
| `ref(null)` | 22 | 8 | strict 下推成 `Ref<null>`，赋对象即报错 —— **主要错误来源** |
| `defineProps` / `defineEmits` | 12 / 12 | 2 / 2 | 运行期声明，可保留；能顺手改类型声明的就改 |
| `ElMessageBox` | 18 | 4 | 返回值与 `catch` 分支需注意 `unknown` |
| `reactive(` | 9 | 4 | 多数能推断，少数需显式接口 |

## 三、「拉平」的定义（必须一致的东西）

| 项 | 值 |
|---|---|
| `tsconfig.json` | **照抄 `frontend/mall/tsconfig.json`**：`strict` + `noUnusedLocals` + `noUnusedParameters` + `noFallthroughCasesInSwitch`，`noEmit`，`include` 含 `vite.config.ts` |
| `compilerOptions.types` | `["vite/client", "element-plus/global"]` —— admin / store 全局注册了 EP，需要它的全局组件类型（mall 不用 EP，故只有 `vite/client`） |
| devDeps | `typescript@^5.9.3`、`vue-tsc@^3.3.11`（与 mall 同版本区间） |
| scripts.build | `vue-tsc --noEmit && vite build` |
| scripts.type-check | `vue-tsc --noEmit` |
| 入口类型声明 | 新增 `src/env.d.ts`（`/// <reference types="vite/client" />`，与 mall 同） |
| 配置文件 | `vite.config.js` → `vite.config.ts` |

⚠ 唯一允许的差异就是 `types` 里多一个 `element-plus/global`（因为 mall 不注册 EP）。
这个差异要写进两端的 tsconfig 注释里，免得下次「对齐」时被误删。

## 四、文件映射（`.js` → `.ts`，共 22 个 + 2 个 vite 配置）

**admin（14）**：`router/index`、`api/{request,auth,user,role,permission,category,brand,spu,store,shopGoods}`、
`store/auth`、`directives/perm`、`main`

**store（8）**：`router/index`、`api/{request,auth,shop,goods}`、`store/{auth,shop}`、`main`

**两端共同**：`vite.config.js` → `vite.config.ts`

**新增（每端 3 个）**：`tsconfig.json`、`src/env.d.ts`、`src/types/*.ts`

## 五、类型策略

### 5.1 公共形状（要建类型的）

只建「接口层形状」与「被 ≥2 个文件共用」的：

| 类型 | 放哪 | 内容 |
|---|---|---|
| `RespData<T>` | `types/api.ts` | `{code, msg, data}`（与 common 同构）；`request.ts` 里用它解包 |
| `PageResult<T>` | `types/api.ts` | 分页返回（列表 + 总数），形状照后端分页 VO |
| `PageQuery` | `types/api.ts` | 分页查询基参（页码 / 每页条数 / 关键字） |
| 登录用户 | `types/auth.ts` | admin：含 `perms` 的当前用户 + 菜单树节点；store：店主 + 店铺态 |
| 各 `api/*.ts` 的入参出参 | 就放在各自的 `api/*.ts` 里 | 只被该文件用的请求体 / 返回体，就近声明并 `export` |

### 5.2 私有形状（靠推断）

单页私有的表单对象 / 列表行：`ref<XxxForm | null>(null)`、`reactive<XxxForm>({...})`，
`XxxForm` 就近声明在该 SFC 的 `<script setup lang="ts">` 里。

### 5.3 明确不做的事

- 不为了「消灭 `any`」而造类型：EP 插槽、`ElMessageBox` 的 reason 等确实无类型可依的地方，
  用最小断言并**加一行注释说明为什么**，不硬造。
- 不把后端 DTO 在前端完整复刻一遍（那是「全量建类型」方案，本次不做）。

## 六、预期错误来源与处置原则

按 strict 的实际报错面预判，处置顺序与原则：

1. **`ref(null)` 赋值报错（30 处）** → 补泛型 `ref<X | null>(null)`。
2. **`noUnusedLocals` / `noUnusedParameters` 报错** → 删掉真正无用的导入 / 变量 / 形参。
3. **`catch (e)` 里访问 `e.message`** → `unknown` 不能直接取属性；改成 `catch {}`（不绑定）
   或按需收窄。**不改成 `catch (e: any)`**。
4. **`defineProps` 运行期声明**（14 组）→ 能顺手改成类型声明的就改；对象复杂、带默认值的保留运行期声明（Vue 能从运行期声明推出 props 类型，这不影响 strict）。
5. **EP 深层路径 / 插槽类型** → 允许 `as` 最小断言或 `// @ts-expect-error` + 注释。

### ⚠ 一条硬约束

**不通过放松 tsconfig 让错误消失。** 不许关 `strict`、不许把 `noUnusedLocals`/`noUnusedParameters`
打开变关闭、不许把报错文件 `exclude` 掉。若某处严格化成本确实过高，
**在代码里做最小让步 + 写明注释**，而不是改配置 —— 否则「拉平」就是假的。

## 七、验证

- ✅ `cd frontend/admin && npm run build`（`vue-tsc --noEmit && vite build`）通过
- ✅ `cd frontend/store && npm run build` 通过
- ✅ `node docs/contracts/drift-check.mjs` 退出码 0（**不改契约**：接口没动）
- ✅ 文本级核对：两端 `tsconfig.json` 与 mall 的差异**只有 `types` 一项**；
  `git diff --stat` 里不出现业务行为改动（只应有类型注解、导入、`.js`→`.ts` 重命名）
- ❌ 不启动 dev / preview、不打接口、不跑 `mvn test`
- ⚠ 这次验证是**真的**：`vue-tsc` 属编译期检查，正是本仓库允许的验证手段，
  它会给出「类型是否自洽」的确定信号。

## 八、文档同步（同一次改动内）

| 文件 | 改什么 |
|---|---|
| `frontend/README.md` | 三端技术栈表：admin / store 加 **TypeScript**；补一句「三端统一 Vue 3 + Vite + TypeScript + axios，构建都带 `vue-tsc` 类型检查」 |
| `frontend/admin/README.md`、`frontend/store/README.md` | 技术栈与构建脚本（`npm run build` 现在含类型检查） |
| 根 `CLAUDE.md` | 加一条前端约定：**三端统一 Vue 3 + TS + axios，`tsconfig` 与 mall 一致（仅 admin/store 多 `element-plus/global`），构建必须带 `vue-tsc`**；新增前端代码一律 `.ts` / `lang="ts"` |
| 根 `README.md` | 技术栈行的「TypeScript（商城前台）」→ 三端都是；已实现功能补一条 |

## 九、风险

| 风险 | 处置 |
|---|---|
| 类型错误量开工前无法准确预判 | `vue-tsc` 逐条给出，按文件清零；先跑一遍总览再动手，若某文件错误过多单独处理并记录 |
| 大 diff 里混入行为改动 | 只加类型、不动逻辑；提交前用 `git diff` 重点看非类型行 |
| EP 深层类型不匹配 | 最小断言 + 注释，不改配置 |
| 三端 `request.ts` 重复三份（admin/store 仍逐字节相同） | **本次不解决**，只记一笔：将来若做 workspace/共享包再收敛 |
| 构建从「不检查」变成「检查」会让既有代码的隐患暴露 | 这正是目的；暴露出来的都修，不绕过 |
