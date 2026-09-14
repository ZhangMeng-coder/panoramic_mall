/* ============================================================================
   Mock 数据 —— 风格样张
   ----------------------------------------------------------------------------
   样张不接接口，所有内容在这里写死。数据不是随便凑的：
   每一项都刻意探一条「内容范围」的边界，用来判断每个区块装多少、装不下怎么办。
   每段注释末尾的 [探] 就是该项在探什么。
   ========================================================================== */

/* ---- 搜索区热搜词 ---- */
window.MALL_HOTWORDS = [
  '手机',
  '空调',
  '蓝牙耳机',
  '运动鞋',
  '咖啡机',
  '办公椅'
]

/* ---- 全分类展示 ----
   [探] 数量刚好铺满一行（10 项）；hue 决定图标圆的渐变色相。
   增删分类时看宫格是否还成行、换行后好不好看。 */
window.MALL_CATS = [
  { id: 'c1', name: '手机数码', emoji: '📱', hue: 12 },
  { id: 'c2', name: '电脑办公', emoji: '💻', hue: 210 },
  { id: 'c3', name: '家用电器', emoji: '🧊', hue: 190 },
  { id: 'c4', name: '服饰鞋包', emoji: '👕', hue: 330 },
  { id: 'c5', name: '食品生鲜', emoji: '🍎', hue: 140 },
  { id: 'c6', name: '美妆个护', emoji: '💄', hue: 350 },
  { id: 'c7', name: '家居家装', emoji: '🛋️', hue: 40 },
  { id: 'c8', name: '母婴玩具', emoji: '🧸', hue: 25 },
  { id: 'c9', name: '运动户外', emoji: '⚽', hue: 90 },
  { id: 'c10', name: '图书文娱', emoji: '📚', hue: 260 }
]

/* ---- 大型滚动广告框 ----
   [探] 第 1 张是「短语 + 大字」的常规促销版式；
        第 3 张标题刻意拉长，看版式在长文案下会不会挤爆或换行难看。 */
window.MALL_BANNERS = [
  {
    id: 'b1',
    kicker: '限时 3 天',
    title: '818 大促',
    sub: '全场满 300 减 50 · 会员再叠 9 折',
    cta: '立即抢购',
    hue: 12
  },
  {
    id: 'b2',
    kicker: '至高补贴 800',
    title: '以旧换新',
    sub: '旧机估值当场抵 · 上门回收免运费',
    cta: '去估价',
    hue: 205
  },
  {
    id: 'b3',
    kicker: '冷链次日达',
    title: '生鲜冰爽节 · 全场冷链包邮 · 坏果包赔',
    sub: '当季水果低至 5 折，满 99 再送尝鲜装',
    cta: '逛生鲜',
    hue: 145
  }
]

/* ---- 热门商品列表 ----
   [探] 逐条探边界：
     g1  超长商品名     → 两行截断是否够用
     g2  无原价 / 无角标 → 版式留白是否塌
     g3  双角标         → 角标放得下吗、会不会压住图
     g4  销量 0         → 「0 人付款」这种空值怎么显示才不难看
     g5  销量「万+」级  → 大数字会不会挤掉角标
     g6  价格三位整数 + 两位小数 → 价格三层字号的宽度
     g7  价格仅个位数   → 「¥9」这种短价格会不会显得空
     g8  长名 + 双角标 + 大销量 → 最坏情况叠加
     g9 / g10 常规款    → 对照组 */
window.MALL_GOODS = [
  {
    id: 'g1',
    name: 'Apple iPhone 15 Pro Max 256GB 原色钛金属 全网通5G智能手机 官方正品',
    imgLabel: '手机',
    price: 8999,
    originPrice: 9999,
    salesText: '2.3万+',
    tags: ['直降'],
    hue: 15
  },
  {
    id: 'g2',
    name: '轻薄羽绒服 90 白鸭绒',
    imgLabel: '羽绒服',
    price: 399,
    originPrice: null,
    salesText: '126',
    tags: [],
    hue: 330
  },
  {
    id: 'g3',
    name: '降噪蓝牙耳机 无线入耳式',
    imgLabel: '耳机',
    price: 249,
    originPrice: 349,
    salesText: '8642',
    tags: ['直降', '包邮'],
    hue: 210
  },
  {
    id: 'g4',
    name: '北欧实木餐椅（新品预售）',
    imgLabel: '餐椅',
    price: 599,
    originPrice: 799,
    salesText: '0',
    tags: ['新品'],
    hue: 40
  },
  {
    id: 'g5',
    name: '一级能效变频空调 1.5 匹',
    imgLabel: '空调',
    price: 2299,
    originPrice: 2899,
    salesText: '10万+',
    tags: ['包邮'],
    hue: 190
  },
  {
    id: 'g6',
    name: '全自动咖啡机 家用研磨一体',
    imgLabel: '咖啡机',
    price: 1288.5,
    originPrice: 1599,
    salesText: '3120',
    tags: ['直降'],
    hue: 25
  },
  {
    id: 'g7',
    name: '纯棉基础款短袖 T 恤',
    imgLabel: 'T恤',
    price: 9.9,
    originPrice: 19.9,
    salesText: '5.6万+',
    tags: ['包邮'],
    hue: 140
  },
  {
    id: 'g8',
    name: '人体工学电脑椅 可躺午休 护腰头枕 全网透气办公椅 家用书房转椅',
    imgLabel: '办公椅',
    price: 1099,
    originPrice: 1699,
    salesText: '3.4万+',
    tags: ['直降', '包邮'],
    hue: 260
  },
  {
    id: 'g9',
    name: '当季红富士苹果 5 斤装',
    imgLabel: '苹果',
    price: 39.9,
    originPrice: 59.9,
    salesText: '9021',
    tags: ['次日达'],
    hue: 350
  },
  {
    id: 'g10',
    name: '专业跑步鞋 减震回弹',
    imgLabel: '跑步鞋',
    price: 459,
    originPrice: 599,
    salesText: '1874',
    tags: ['直降'],
    hue: 90
  }
]
