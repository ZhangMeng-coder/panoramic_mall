/* ============================================================================
   首页数据类型 —— 全部对应静态 mock 数据，不接接口。
   ========================================================================== */

/** 轮播广告（④ 大型滚动广告框） */
export interface Banner {
  id: string
  /** 短语标签，如「限时 3 天」 */
  kicker: string
  title: string
  sub: string
  cta: string
  /** 色相，决定该张背景渐变的颜色 */
  hue: number
}

/** 商品角标。已知四种各有样式，其余落中性样式（见 utils/format.ts 的 tagClass） */
export type GoodsTag = '直降' | '包邮' | '次日达' | '新品'

/** 热门商品（⑥ 热门商品列表） */
export interface Goods {
  id: string
  name: string
  /** 占位图上的文字（页面不引外部图片，图位用 CSS 渐变占位） */
  imgLabel: string
  price: number
  /** 无原价时为 null —— 探「版式留白会不会塌」 */
  originPrice: number | null
  /** 销量文案，'0' 表示无成交（渲染为「暂无成交」） */
  salesText: string
  tags: GoodsTag[]
  /** 色相，决定占位图与文字色的颜色 */
  hue: number
}
