# admin — 全景商城后端管理后台

商品中台管理端（Vue 3 + Vite + Element Plus），端口 **5173**，经网关（8080）调用后端商品中心接口。

## 功能页面

| 页面 | 路由 | 功能 |
|---|---|---|
| 分类管理 | `/category` | 分类多级树展示；节点悬停操作：新增子分类 / 编辑 / 删除；删除受后端保护（有子分类或有商品时提示失败原因） |
| 品牌管理 | `/brand` | 品牌列表分页 + 名称关键字搜索；新增/编辑弹窗（LOGO 实时预览）；删除 |
| 商品管理 | `/spu` | 商品分页筛选（分类树下拉/品牌/上下架状态/名称关键字）；新增/编辑/上下架/删除（上架中禁止删除） |

## 商品编辑的 SKU 规格编辑器（SkuEditor）

- **规格维度配置**：动态添加「规格名（颜色/内存/…）+ 规格值」，按**笛卡尔积**自动生成全部 SKU 组合
- **编辑回显**：按现有 SKU 规格反推维度；已有组合保留其 `sku.id`（后端据此做 diff，SKU 主键保持稳定），新组合 `id=null`
- 行内规格值重复 / 组合重复自动拦截
- 每个 SKU 可单独维护商家编码（skuCode）与图片

## 技术要点

- **响应拦截**：`axios` 拦截器校验 `RespData.code === 200` → 直接返回 `data`；否则 `ElMessage.error(msg)` 并 reject（业务提示统一来自后端）
- **代理**：`vite.config.js` 将 `/goods`、`/discovery` 转发至 `http://localhost:8080`（网关），开发期前后端同源
- 分页参数为 `pageNum/pageSize`，与后端 `BasePageVO` 对应

## 本地开发

```bash
npm install
npm run dev       # → http://localhost:5173（需后端网关 8080 与商品中心 8081 已启动）
npm run build     # 产物输出 dist/
```

## 目录结构

```
src/
├── api/                 # axios 封装 + 分类/品牌/商品接口模块
├── router/              # 路由（默认跳转分类管理）
├── views/
│   ├── category/        # CategoryManage + CategoryFormDialog
│   ├── brand/           # BrandManage + BrandFormDialog
│   └── spu/             # SpuManage + SpuFormDialog + SkuEditor
├── App.vue              # 侧边导航 + 内容区布局
└── main.js              # Element Plus（zh-cn）+ 路由挂载
```
