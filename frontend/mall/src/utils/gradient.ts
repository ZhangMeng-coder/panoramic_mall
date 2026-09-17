/**
 * 由色相生成占位渐变 —— 用于**无图 / 图片加载失败**时的图位回退；数据驱动的图片由后端 URL 提供，不走这里。
 * grad(色相, 饱和度, 起始亮度, 结束亮度, 角度)
 */
export function grad(hue: number, sat: number, l1: number, l2: number, deg = 135): string {
  return (
    `linear-gradient(${deg}deg, ` +
    `hsl(${hue} ${sat}% ${l1}%), hsl(${(hue + 28) % 360} ${sat}% ${l2}%))`
  )
}
