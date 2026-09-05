# admin — 全景商城后台管理

后端管理端（Vue 3 + Vite + Element Plus），端口 **5173**，经网关（8080）调用后端商品中心（goods-center）、平台管理（admin）与店铺中心（store-center）接口。

## 主题与设计令牌（Design Tokens）

- **集中换肤**：`src/styles/tokens.css` 顶部 `:root` 即换肤入口（主色 `#4F46E5` / 语义色 / 圆角等），文件头有「可调整变量」注释
- **Element Plus 主题映射**：同一文件内把 EP 的 `--el-color-*` 色板（含 light-3/5/7/8/9 与 dark-2）映射到令牌色，全站按钮/标签/表格等自动随主色走；`info` 保留中性灰以维持“灰=次要”语义
- **明暗双主题**：加载 `element-plus/theme-chalk/dark/css-vars.css`，`.dark` 挂在 `<html>`；EP 暗色结构变量 + 令牌暗色层联动。`index.html` 首帧内联脚本先应用主题，避免切换闪烁
- **亮/暗切换**：顶栏右上角图标按钮，选择写入 `localStorage['pm-admin-theme']`，未设置时跟随系统 `prefers-color-scheme`
- **排版/间距/圆角/阴影/动效**：按 8px 网格与 Modular Scale 提供 `--space-*`、`--text-*`、`--radius-*`、`--shadow-*`、`--duration-*`
- **组件预设**：`src/styles/components.css` 提供 `.btn/.btn-primary/.btn-ghost/.btn-sm/.btn-lg`、`.card`、`.input`、`.badge`（语义变体）、`.container-section`，供非 EP 自定义区域直接套用

## 功能页面

| 页面 | 路由 | 功能 |
|---|---|---|
| 分类管理 | `/category` | 分类多级树展示（el-table 树形数据，一次加载全展开，非懒加载）；行悬停操作：新增子分类 / 编辑 / 删除；删除受后端保护（有子分类或有商品时提示失败原因） |
| 品牌管理 | `/brand` | 品牌列表分页 + 名称关键字搜索；新增/编辑弹窗（LOGO 实时预览）；删除 |
| 商品管理 | `/spu` | 商品为商城商品的信息模板（无上下架概念，以 展示/隐藏 表示对商城是否可见）；分页筛选（分类树下拉/品牌/展示状态/名称关键字）。**编辑**＝基本信息+规格属性配置（各规格维度可选项）；「规格」弹窗独立管理 SKU（展示编码/图片/规格组合，组合取值来自规格属性配置，可一键生成缺失组合）；「预览」只读查看 名称/分类完整链条/主图/轮播图/详情/规格配置/SKU 明细 |
| 用户管理 | `/user` | 列表内联展示已分配角色（多角色并排标签）；用户分页（关键字/状态筛选）+ CRUD；密码 BCrypt 存储，编辑留空不改密码；分配角色（全角色勾选回显、可整体替换/清空） |
| 角色管理 | `/role` | 角色分页 + CRUD；分配权限（权限树勾选，父节点级联全选子级、可清空）；分配用户（左侧展示“不在该角色内”的用户分页可加，右侧已分配可移除） |
| 权限管理 | `/permission` | 权限树表格展示（el-table 树形数据：目录/页面/按钮逐级递减，含权限字符串、页面级路由地址）；行悬停新增顶级目录 / 新增子级 / 编辑 / 删除（受后端层级与引用保护）；页面(2)级权限带路由地址 `route`，供前端菜单导航 |
| 店铺管理 | `/shop` | 店主店铺列表（店铺名关键字 + 审核状态筛选、状态 badge 草稿/待审核/已通过/已驳回）；详情抽屉（资质字段只读回显 + 店主账号）；审核弹窗：通过 / 驳回（驳回原因必填），按钮挂 `v-perm`（`store:shop:list/audit`） |

> 侧边栏菜单由后台 `/admin/permissions/menus` 动态生成（按当前登录用户角色过滤 `menusByRoleIds`：商品中台 / 系统管理 / 店铺管理 目录→页面，页面携带 `route`）；改动角色/权限后需**重新登录**刷新 Redis 快照。

## 商品编辑的 SKU 规格编辑器（SkuEditor）

- **规格维度配置**：动态添加「规格名（颜色/内存/…）+ 规格值」，按**笛卡尔积**自动生成全部 SKU 组合
- **编辑回显**：按现有 SKU 规格反推维度；已有组合保留其 `sku.id`（后端据此做 diff，SKU 主键保持稳定），新组合 `id=null`
- 行内规格值重复 / 组合重复自动拦截
- 每个 SKU 可单独维护商家编码（skuCode）与图片

## 技术要点

- **响应拦截**：`axios` 拦截器校验 `RespData.code === 200` → 直接返回 `data`；否则 `ElMessage.error(msg)` 并 reject（业务提示统一来自后端）
- **代理**：`vite.config.js` 将 `/goods`、`/admin`、`/store`、`/discovery` 转发至 `http://localhost:8080`（网关），开发期前后端同源（网关按 StripPrefix 分发到 goods-center/admin/store-center）
- 分页参数为 `pageNum/pageSize`，与后端 `BasePageVO` 对应

## 本地开发

```bash
npm install
npm run dev       # → http://localhost:5173（需后端网关 8080 与 goods-center 8081 / admin 8082 / store-center 8083 已启动）
npm run build     # 产物输出 dist/
```

## 目录结构

```
src/
├── api/                 # axios 封装 + 分类/品牌/商品/用户/角色/权限/店铺接口模块
├── router/              # 路由（默认跳转分类管理；店铺管理 /shop）
├── styles/              # Design Token 基础层（换肤/EP主题映射/组件预设）
│   ├── tokens.css       #   可调变量色板 + 浅/暗令牌 + EP --el-* 主题映射
│   ├── base.css         #   Reset + 排版 + 滚动条（纯 var 驱动）
│   └── components.css   #   .btn/.card/.input/.badge/.container-section 预设
├── views/
│   ├── category/        # CategoryManage + CategoryFormDialog
│   ├── brand/           # BrandManage + BrandFormDialog
│   ├── spu/             # SpuManage + SpuFormDialog(基础+规格配置) + SpuSkuManageDialog + SpuPreviewDialog
│   ├── user/            # UserManage + UserFormDialog + AssignRoleDialog
│   ├── role/            # RoleManage + RoleFormDialog + AssignPermissionDialog + AssignUserDialog
│   ├── permission/      # PermissionManage + PermissionFormDialog
│   └── shop/            # ShopManage（店铺列表 + 详情抽屉 + 审核弹窗）
├── App.vue              # 布局壳：侧边导航（商品/系统管理）+ 顶栏（页名/明暗切换）
└── main.js              # Element Plus（zh-cn）+ 暗色 css-vars + tokens/base/components + 路由
```
