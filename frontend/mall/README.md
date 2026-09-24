# 全景商城 — 前台商城（mall）

C 端前台（顾客侧）的正式前端工程：**Vue 3 + Vite + TypeScript**，端口 **5175**。页面只经网关访问端 BFF `mall-bff`（:8085）——`vite.config.ts` 的 `/mall` 代理 → 网关 8080（`StripPrefix=1`）→ mall-bff。

页面域：首页（六区块）、搜索结果与分类商品列表、商品详情、**购物车**、**我的订单**（列表 / 详情，支付 / **改收货地址** / **取消** / **仅退款** 都在详情页内，**待支付倒计时**两页都有）、登录 / 注册、账号（个人资料 / 收货地址）。

## 业务边界

- **本工程是 mall 前台视觉与结构的唯一风格源头**（新增区块或调风格，先在本工程里改好、定了，再往外铺）；色板唯一来源是 [`src/styles/tokens.css`](src/styles/tokens.css)，只消费其中的令牌、禁止硬编码色值。
- 登录分级：首页公开；**一涉及商品查询 / 详情与顾客自己的数据就要登录**，401 一律提示并跳登录页（与 store / admin 两端一致）。路由级拦截与静默 401 的口径见 [cross-cutting.md](../../docs/contracts/cross-cutting.md) 第 11 条。
- 页面契约见 [docs/contracts/mall-bff.md](../../docs/contracts/mall-bff.md)：**写 / 改页面只照表写，不照后端代码写**。

## 仅在本文件登记的事

- **唯一换肤入口**：[`src/styles/tokens.css`](./src/styles/tokens.css)。它是 C 端促销风橙红令牌，与 admin / store 两端的靛蓝后台令牌**刻意不同源**，两套不通用；换风格只改该文件顶部「可调整」区块，其余样式文件不用动。
- **本端唯一的模态层**：[`src/components/ModalShell.vue`](./src/components/ModalShell.vue)（样式在
  [`src/styles/modal.css`](./src/styles/modal.css)，遮罩色是令牌 `--scrim`）。遮罩层级 / Esc 与点遮罩关闭 /
  打开时锁滚动都在它里面，**别各页再写一层遮罩**；它的内容是插槽（如选地址
  [`src/components/AddressPicker.vue`](./src/components/AddressPicker.vue)，三个入口共用同一份）。
  ⚠ 这只针对**弹窗**：支付面板与破坏性动作的两步确认仍是**行内展开**（本端既有做法，不因这一层而改）。
- **`src/mock/` 是首页的静态数据**（`banners` / `goods` / `hotwords`，分别供 ④ 轮播广告、⑥ 热门商品列表、② 热搜词），其中**⑥ 热门商品列表仍读它、不接接口**。数据逐条刻意探过边界，各文件顶部的 `[探]` 注释逐条记着该项在探什么（长文案、无原价、双角标、销量 0 / 万+、价格位数、最坏叠加…）——**这是刻意基准，不要随手改小**。
- ⑤ 用户信息框里的优惠券 / 积分 / 收藏**恒为 0**：这三项接口属二期，不编造假数字。
- **购物车徽标只有一份状态**：[`src/store/cart.ts`](./src/store/cart.ts)（`count` ref + `refresh` / `clear` / `setCount`），
  顶栏徽标、详情页加购后的刷新、购物车页写操作后的刷新都走它——别在组件里各写一个计数 ref。
  口径是**购物车行数**（`GET /cart/count`），与购物车页页脚的「有效行件数之和」**不同**（契约明确如此）。
- **购物车页**（[`src/views/CartView.vue`](./src/views/CartView.vue)，`/cart`，需登录态）与商品详情页的购买区：
  样式各自在 [`src/styles/cart.css`](./src/styles/cart.css) 与 `catalog.css` 的 `.detail__buy*` 里；
  **我的订单**两页（[`src/views/order/`](./src/views/order/)）的样式在 [`src/styles/order.css`](./src/styles/order.css)。
  ⚠ **「去结算」与详情页「立即下单」均已接真实下单**（`source=CART` / `DIRECT`）：点开后先过一道**地址分支**
  （判定只此一处，在 [`src/composables/useAddressGate.ts`](./src/composables/useAddressGate.ts)）——
  **没地址** → 提示并送去收货地址页、**不提交订单**；**有地址但没默认** → 弹窗选一条；**有默认** → 直接用，
  不问不弹。选中地址按「确认下单」才提交。⚠ C 端**没有收银台、不跳支付页**：下单成功后按「一单一店」分流——
  单笔跳订单详情、多笔跳订单列表，支付是**订单详情页内行内展开的面板**。
  ⚠ 订单详情页在**待支付**时另给「修改地址」入口（地址块旁 + 支付面板内各一个，闸门在服务端）——它改的是
  **那一单的快照**，不动顾客地址簿。
  ⚠ **待支付倒计时**（[`src/composables/useCountdown.ts`](./src/composables/useCountdown.ts)，**列表页与详情页
  共用一份**：单个 interval 驱动、组件卸载即停，列表 N 行共用一个 tick、**不是每行一个定时器**），数据源是
  订单上的 `expireTime`；**只对 `PENDING_PAYMENT` 渲染**（截止时刻为 `null` 的老单没有这一行）。
  ⚠ **倒计时归零不自动取消**：权威在服务端（超时关单任务 + 支付时校验），页面只是显示与引导——
  归零后不再催付、改成一句既成事实，列表页另会短时轮询等关单任务把那一笔关掉（口径见该页文件头 ⑧）。
  ⚠ **取消订单（仅待支付）/ 仅退款（仅已支付未发货）**是**两个动作**——「没付过钱的单不买了」与
  「付过的钱退回去」，闸门都在服务端状态机（页面只用**一份**判据把入口摆对位置——待支付的三个入口
  共用一个、仅退款一个，不各写一遍），
  两个入口都走**行内两步确认**、成功后重拉当前页。
  失效行照常渲染（灰显 + 「已失效」）、但不计入件数与金额；
  车里有失效行时顶部操作条多一个「**清除失效商品 (N)**」，**点了就删**（不做两步确认——
  它删的本来就是买不了的行），且**没有专用接口**，是把服务端下发的 `invalid` 行 id 收集起来
  走批量删除 `POST /cart/items/remove`。

## 本地开发

```bash
npm install && npm run dev   # → http://localhost:5175
```

`npm run build` = `vue-tsc --noEmit && vite build`（类型不过即构建失败）；`npm run type-check` 只跑类型检查，`npm run preview` 预览构建产物。
