import type { ChartType, TooltipItem } from 'chart.js'

/**
 * 图表主题：暖橘手账 8 色序列 + 通用 tooltip / options 工厂。
 * 各图表组件从这里取色，替代散落的字面量色板。
 */
export const chartPalette: readonly string[] = [
  '#EB5E28',
  '#F2A33C',
  '#2F9C63',
  '#3BA8A0',
  '#4A90D9',
  '#8E7CC3',
  '#D85A8A',
  '#A47148'
]

/** 循环取第 index 个序列色（超出 8 项回绕，图例与图块永远一致）。 */
export function seriesColor(index: number): string {
  const size = chartPalette.length
  return chartPalette[((index % size) + size) % size]
}

/**
 * 生成 count 个序列色（循环回绕）。
 * 数据集 backgroundColor 直接传本函数结果而非原始色板，
 * 可修复条目数 > 8 时第 9 项起图例与图块颜色对不上的问题。
 */
export function seriesColors(count: number): string[] {
  return Array.from({ length: count }, (_, index) => seriesColor(index))
}

/** 金额 tooltip：¥ 前缀 + 千分位两位小数。展开进 options.plugins.tooltip。 */
export function currencyTooltip<TType extends ChartType = ChartType>(): {
  callbacks: { label: (item: TooltipItem<TType>) => string }
} {
  return {
    callbacks: {
      label(item: TooltipItem<TType>): string {
        const name = datasetLabel(item.dataset) || item.label || ''
        const amount = tooltipValue(item).toLocaleString('zh-CN', {
          minimumFractionDigits: 2,
          maximumFractionDigits: 2
        })
        return `${name ? `${name}: ` : ''}¥${amount}`
      }
    }
  }
}

export function chartFontFamily(): string {
  return cssVar('--font-sans', 'system-ui, sans-serif')
}

export function chartTextColor(): string {
  return cssVar('--color-text', '#33291d')
}

export function chartMutedColor(): string {
  return cssVar('--color-muted', '#786a51')
}

/**
 * 通用 options 基座：响应式、底部图例、设计系统字体与文字色（读 CSS 变量）。
 * 用法：{ ...baseChartOptions(), scales: {...} }；需覆写 legend 时整体替换 plugins。
 */
export function baseChartOptions(): {
  responsive: true
  maintainAspectRatio: false
  color: string
  plugins: {
    legend: {
      position: 'bottom'
      labels: { color: string; font: { family: string } }
    }
  }
} {
  const family = chartFontFamily()
  const color = chartTextColor()
  return {
    responsive: true,
    maintainAspectRatio: false,
    color,
    plugins: {
      legend: {
        position: 'bottom',
        labels: { color, font: { family } }
      }
    }
  }
}

function tooltipValue(item: { parsed: unknown }): number {
  // line/bar 的 parsed 是 {x, y}；doughnut/pie 的 parsed 是 number
  const parsed: unknown = item.parsed
  if (typeof parsed === 'number') return parsed
  if (parsed && typeof parsed === 'object' && 'y' in parsed) {
    const y = (parsed as { y: unknown }).y
    if (typeof y === 'number') return y
  }
  return 0
}

function datasetLabel(dataset: unknown): string {
  // 泛型 TType 下 dataset 是 UnionToIntersection 复合类型，label 需防御性取值
  if (dataset && typeof dataset === 'object' && 'label' in dataset) {
    const label = (dataset as { label?: unknown }).label
    if (typeof label === 'string') return label
  }
  return ''
}

function cssVar(name: string, fallback: string): string {
  const value = getComputedStyle(document.documentElement).getPropertyValue(name).trim()
  return value || fallback
}
