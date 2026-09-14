import type { Banner } from '../types/mall'

/* ---- ④ 大型滚动广告框 ----
   [探] 第 1 张是「短语 + 大字」的常规促销版式；
        第 3 张标题刻意拉长，看版式在长文案下会不会挤爆或换行难看。 */
export const banners: Banner[] = [
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
