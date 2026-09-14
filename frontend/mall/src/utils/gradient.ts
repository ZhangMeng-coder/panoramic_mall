/**
 * 由色相生成占位渐变 —— 页面不引任何外部图片，所有图位一律用 CSS 渐变占位。
 * grad(色相, 饱和度, 起始亮度, 结束亮度, 角度)
 */
export function grad(hue: number, sat: number, l1: number, l2: number, deg = 135): string {
  return (
    `linear-gradient(${deg}deg, ` +
    `hsl(${hue} ${sat}% ${l1}%), hsl(${(hue + 28) % 360} ${sat}% ${l2}%))`
  )
}
