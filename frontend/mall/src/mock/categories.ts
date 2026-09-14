import type { Category } from '../types/mall'

/* ---- ③ 全分类展示 ----
   [探] 数量刚好铺满一行（10 项）；hue 决定图标圆的渐变色相。
   增删分类时看宫格是否还成行、换行后好不好看。 */
export const categories: Category[] = [
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
