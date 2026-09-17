import type { Category } from '@/types'

/**
 * 递归展平分类树（含父分类自身）
 */
export function flattenCategories(items: Category[]): Category[] {
  return items.flatMap(item => [item, ...(item.children ? flattenCategories(item.children) : [])])
}
