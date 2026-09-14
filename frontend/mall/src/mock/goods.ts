import type { Goods } from '../types/mall'

/* ---- ⑥ 热门商品列表 ----
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
export const goods: Goods[] = [
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
