import type { LocationQuery, LocationQueryRaw } from 'vue-router'
import type { AnalysisResultV2, TransactionFilters, TransactionType } from '@/types'

export interface TransactionDeepLinkFilters {
  startDate?: string
  endDate?: string
  type?: TransactionType | ''
  categoryId?: number
  accountId?: number
  tagIds?: number[]
  keyword?: string
  merchant?: string
  minAmount?: number | string
  maxAmount?: number | string
}

const DEFAULT_EXAMPLES = [
  '本月花了多少钱',
  '今年最大的 10 笔支出',
  '最近半年支出趋势',
  '哪个分类花钱最多'
]

export function defaultAnalysisExamples(): string[] {
  return [...DEFAULT_EXAMPLES]
}

export function toTransactionQuery(filters: TransactionDeepLinkFilters): LocationQueryRaw {
  const query: LocationQueryRaw = {}
  if (filters.startDate) query.startDate = filters.startDate
  if (filters.endDate) query.endDate = filters.endDate
  if (filters.type) query.type = filters.type
  if (isPositiveInt(filters.categoryId)) query.categoryId = String(filters.categoryId)
  if (isPositiveInt(filters.accountId)) query.accountId = String(filters.accountId)
  if (filters.keyword) query.keyword = filters.keyword
  if (filters.merchant) query.merchant = filters.merchant
  if (filters.minAmount != null && filters.minAmount !== '') query.minAmount = String(filters.minAmount)
  if (filters.maxAmount != null && filters.maxAmount !== '') query.maxAmount = String(filters.maxAmount)
  const tagIds = (filters.tagIds ?? []).filter(isPositiveInt).map(String)
  if (tagIds.length) query.tagIds = tagIds
  return query
}

export function filtersFromAnalysisResult(card: AnalysisResultV2): TransactionDeepLinkFilters {
  const filters = (card.filters ?? {}) as Record<string, unknown>
  return {
    startDate: card.period?.current?.start || undefined,
    endDate: card.period?.current?.end || undefined,
    type: card.transactionType ?? '',
    categoryId: asPositiveInt(filters.categoryId),
    accountId: asPositiveInt(filters.accountId),
    tagIds: asPositiveIntList(filters.tagIds),
    keyword: asOptionalString(filters.keyword),
    merchant: asOptionalString(filters.merchant),
    minAmount: asAmountString(filters.minAmount),
    maxAmount: asAmountString(filters.maxAmount)
  }
}

export function hydrateFiltersFromQuery(query: LocationQuery): TransactionFilters {
  return {
    type: asTransactionType(firstQueryValue(query.type)),
    startDate: firstQueryValue(query.startDate) || '',
    endDate: firstQueryValue(query.endDate) || '',
    tagIds: asPositiveIntList(query.tagIds),
    categoryId: asPositiveInt(firstQueryValue(query.categoryId)),
    accountId: asPositiveInt(firstQueryValue(query.accountId)),
    createdBy: firstQueryValue(query.createdBy) || '',
    keyword: firstQueryValue(query.keyword) || undefined,
    merchant: firstQueryValue(query.merchant) || undefined,
    minAmount: asAmountString(firstQueryValue(query.minAmount)),
    maxAmount: asAmountString(firstQueryValue(query.maxAmount)),
    size: 20
  }
}

export function normalizedQueryKey(query: LocationQuery): string {
  const filters = hydrateFiltersFromQuery(query)
  return JSON.stringify({
    type: filters.type || '',
    startDate: filters.startDate || '',
    endDate: filters.endDate || '',
    tagIds: [...(filters.tagIds ?? [])].sort((a, b) => a - b),
    categoryId: filters.categoryId ?? null,
    accountId: filters.accountId ?? null,
    createdBy: filters.createdBy || '',
    keyword: filters.keyword || '',
    merchant: filters.merchant || '',
    minAmount: filters.minAmount == null ? '' : String(filters.minAmount),
    maxAmount: filters.maxAmount == null ? '' : String(filters.maxAmount)
  })
}

function firstQueryValue(value: unknown): string {
  if (Array.isArray(value)) return String(value[0] ?? '')
  return value == null ? '' : String(value)
}

function asOptionalString(value: unknown): string | undefined {
  if (typeof value !== 'string') return undefined
  const trimmed = value.trim()
  return trimmed || undefined
}

function asAmountString(value: unknown): string | undefined {
  if (value == null || value === '') return undefined
  if (typeof value === 'number' && Number.isFinite(value)) return String(value)
  if (typeof value === 'string' && value.trim() !== '' && !Number.isNaN(Number(value))) {
    return value.trim()
  }
  return undefined
}

function asTransactionType(value: string): TransactionType | '' {
  if (value === 'EXPENSE' || value === 'INCOME' || value === 'TRANSFER') return value
  return ''
}

function asPositiveInt(value: unknown): number | undefined {
  const numeric = typeof value === 'number' ? value : Number(value)
  return isPositiveInt(numeric) ? numeric : undefined
}

function asPositiveIntList(value: unknown): number[] {
  if (value == null || value === '') return []
  const items = Array.isArray(value) ? value : [value]
  return items.map(item => asPositiveInt(item)).filter((item): item is number => item != null)
}

function isPositiveInt(value: unknown): value is number {
  return typeof value === 'number' && Number.isInteger(value) && value > 0
}
