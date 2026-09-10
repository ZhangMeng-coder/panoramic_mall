# store — 全景商城店铺端（店主侧）

商城店铺端独立前端项目（Vue 3 + Vite + Element Plus），端口 **5174**，经网关（8080）调 `store-bff`（店铺端 BFF，:8084）接口（店铺数据再经其编排落 store 域），前端根前缀 `/store`。

> 当前为 **Phase 1 店铺管理**：店主注册登录 → 维护店铺信息并提交审核 → 平台审核通过后开放后续开店业务入口（本期商品/订单/库存为占位假页面）。

## 页面与状态机导航

| 路由 | 页面 | 说明 |
|---|---|---|
| `/register` | 注册 | 店主自助注册（注册即登录）；用户名/昵称/手机号/密码 + 确认密码 |
| `/login` | 登录 | 店主登录（支持 redirect 回跳） |
| `/shop-info` | 店铺信息 | **核心页**，按审核状态驱动只读/可编辑与操作按钮 |
| `/goods` `/orders` `/stock` | 商品/订单/库存 | **占位假页面**（el-result + meta 文案），仅店铺已通过（status=2）时侧栏出现并可访问 |

「店铺信息」状态机交互（对应后端 `store_shop.status`：0草稿 / 1待审核 / 2已通过 / 3已驳回）：

- 无店铺 / 草稿(0)：表单可编辑，提供 **保存草稿** 与 **提交审核**
- 待审核(1)：**只读** + 黄色「审核中」提示（含提交时间），编辑/重提被禁用（后端亦强制锁定）
- 已驳回(3)：**只读回显**驳回原因，解锁表单，修改后重新提交 → 待审核
- 已通过(2)：只读展示店铺资料 + 绿色徽标，侧栏出现开店业务占位入口

表单字段 = 店铺资质套（店铺名/LOGO/简介 + 联系人/电话 + 省市区/详细地址 + 企业名/统一社会信用代码/执照照片），与后端 `ShopSaveDTO` 对应；图片本期填 URL，上传功能后续再加。

## 鉴权与登录态

- token 存 `localStorage['pm-store-token']`；用户信息 + `userType=store` 快照存 `src/store/auth.js`
- 未登录访问受限页 → 跳登录（带 `redirect`）；已登录访问 `/login` `/register` → 跳默认页
- `/goods|/orders|/stock` 守卫：进入前拉取「我的店铺」，未审核通过（`isApproved`）则跳回 `/shop-info`
- `src/api/request.js` 统一带 `Authorization: Bearer`，响应拦截校验 `code===200` 直返 `data`，`401` 清登录态跳登录
- 主题明暗切换持久化到 `localStorage['pm-store-theme']`（`index.html` 首帧先应用防闪烁）

## 本地开发

```bash
npm install
npm run dev       # → http://localhost:5174（需后端网关 8080 / store 域 8083 / store-bff 8084 已启动，且库已建表）
npm run build     # 产物输出 dist/
```

## 目录结构

```
src/
├── api/                  # axios 封装 + auth(/store/auth/**)、shop(/store/shops/**)
├── router/               # 路由 + 登录/开店(approved)守卫（hash 模式）
├── store/                # 轻量登录态（auth.js）+ 我的店铺状态（shop.js）
├── styles/               # 沿用 admin 的 Design Token 基础层（tokens/base/components.css）
├── layout/Layout.vue     # 顶栏（主题切换/用户下拉登出）+ 侧栏（店铺信息常驻；商品/订单/库存 isApproved 才显）
└── views/
    ├── login/            # LoginView
    ├── register/         # RegisterView
    ├── shop/             # ShopInfoView（状态机核心页）
    └── placeholder/      # PlaceholderView（开店业务占位）
```
