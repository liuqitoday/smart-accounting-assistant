/**
 * 读取 :root 上的 CSS 自定义属性并解析为毫秒数。
 *
 * 支持 `250ms` / `0.25s` / 纯数字三种写法；变量缺失或解析失败时返回 fallback。
 * 供 JS 侧动画收尾定时器与 styles.css 里的过渡时长 token 保持同步。
 */
export function cssMs(name: string, fallback: number): number {
  const value = getComputedStyle(document.documentElement).getPropertyValue(name).trim()
  const parsed = Number.parseFloat(value)
  if (!Number.isFinite(parsed)) return fallback
  if (value.endsWith('ms')) return parsed
  if (value.endsWith('s')) return parsed * 1000
  return parsed
}
