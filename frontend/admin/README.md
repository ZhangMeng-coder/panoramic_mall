# admin — 全景商城后台管理

平台管理端的页面工程（Vue 3 + Vite + **TypeScript** + Element Plus），端口 **5173**：经网关**只访问端 BFF `admin`**——商品域（goods-center）与店铺域（store）已下沉为纯域，页面不再直连，一律由 admin BFF 内部 Feign 编排。

页面域：分类 / 品牌 / 标准商品（SPU 与 SKU）/ 用户 / 角色 / 权限 / 店铺列表 / 店铺商品（跨店列表 + 只读详情）。

## 业务边界

- **RBAC 只有本端有**：后端 `@PreAuthorize` 与按钮上的 `v-perm` 是唯一授权点；侧栏菜单由后台接口按当前登录用户的角色动态生成，改动角色 / 权限后需**重新登录**刷新 Redis 快照。
- 「店铺商品」是**页面(2)级权限**，权限行由后端 `db/schema.sql` 灌入；**角色授权需在「角色管理 → 分配权限」手工勾选**，勾选后相关账号重新登录才生效（超管持 `*` 不受限）。
- 侧栏能点开的前提是**前端路由 `path` 与 `sys_permission.route` 逐字一致**；商品详情页靠 `meta.activeMenu` 让侧栏仍高亮所属列表项。

## 商品 SKU 规格编辑器（`views/spu/SpuSkuManageDialog.vue`）

- **规格维度配置**：动态添加「规格名（颜色/内存/…）+ 规格值」，按**笛卡尔积**自动生成全部 SKU 组合
- **编辑回显**：按现有 SKU 规格反推维度；已有组合保留其 `sku.id`（后端据此做 diff，SKU 主键保持稳定），新组合 `id=null`
- 行内规格值重复 / 组合重复自动拦截
- 每个 SKU 可单独维护商家编码（skuCode）与图片

## 换肤与主题

设计令牌集中在 `src/styles/tokens.css`（文件头即说明）：顶部色板是**唯一换肤入口**，同文件的「EP 主题映射」把 `--el-color-*` 指到令牌色，故按钮 / 标签 / 表格自动随主色走；明暗双主题把 `.dark` 挂在 `<html>`，选择持久化在 `localStorage`（键名与首帧防闪烁脚本见 `index.html`）。不依赖 EP 的自定义区域直接套 `src/styles/components.css` 的预设类。

## 本地开发

```bash
npm install
npm run dev        # → http://localhost:5173（需网关 8080 与 admin BFF 8082 已启动）
npm run type-check # vue-tsc --noEmit（只查类型，不出产物）
npm run build      # vue-tsc --noEmit && vite build，产物输出 dist/
```

`vite.config.ts` 把 `/admin` 与 `/discovery` 转发到网关 8080（开发期前后端同源）。分页参数为 `pageNum`/`pageSize`，对应后端 `BasePageVO`；分页响应为 `PageResult<T>` = `{ total, records }`。

页面契约见 [docs/contracts/admin.md](../../docs/contracts/admin.md)。
