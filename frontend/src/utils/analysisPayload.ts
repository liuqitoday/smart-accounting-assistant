import type {
  AnalysisAverageByPeriodResult,
  AnalysisAggregateResult,
  AnalysisBreakdownResult,
  AnalysisNetCashFlowResult,
  AnalysisPayloadV2,
  AnalysisPeriodCompareResult,
  AnalysisQueryResult,
  AnalysisResultBase,
  AnalysisResultV2,
  AnalysisTransactionsResult,
  AnalysisTrendResult,
  AnalysisUnit,
  AnalysisUnknownResult,
  ParsedAnalysisPayload
} from '@/types'
import { formatMoney, toNumber } from '@/utils/format'

const KNOWN_STATUSES: ReadonlySet<AnalysisPayloadV2['status']> = new Set([
  'OK',
  'CLARIFICATION_REQUIRED',
  'INVALID_PLAN',
  'NO_DATA',
  'PARTIAL_RESULT',
  'AI_UNAVAILABLE',
  'QUERY_FAILED',
  'RATE_LIMITED'
])

export function parseAnalysisPayload(payload?: string | null): ParsedAnalysisPayload {
  if (payload == null || payload.trim() === '') {
    return { version: 1, results: [], warnings: [], followUps: [] }
  }

  let parsed: unknown
  try {
    parsed = JSON.parse(payload)
  } catch {
    return { version: 'invalid', warnings: ['结果无法解析'] }
  }

  if (!isRecord(parsed)) {
    return { version: 'invalid', warnings: ['结果无法解析'] }
  }

  if (parsed.schemaVersion === 2) {
    return { version: 2, payload: mapPayloadV2(parsed) }
  }

  const queries = isRecord(parsed.results) ? parsed.results.queries : undefined
  if (Array.isArray(queries)) {
    return {
      version: 1,
      results: queries.map(mapQueryResultV1),
      warnings: toStringList(parsed.warnings),
      followUps: toStringList(parsed.followUps)
    }
  }

  return { version: 'invalid', warnings: ['结果无法解析'] }
}

export function formatAnalysisValue(value: number | string, unit: AnalysisUnit): string {
  if (unit === 'COUNT') {
    return `${toNumber(value)} 笔`
  }
  if (unit === 'PERCENT') {
    return `${toNumber(value)}%`
  }
  return formatMoney(value)
}

function mapPayloadV2(raw: Record<string, unknown>): AnalysisPayloadV2 {
  return {
    schemaVersion: 2,
    rawPlan: raw.rawPlan ?? null,
    normalizedPlan: raw.normalizedPlan ?? null,
    results: Array.isArray(raw.results) ? raw.results.map(mapResultV2) : [],
    status: isStatus(raw.status) ? raw.status : 'OK',
    warnings: mapWarnings(raw.warnings),
    followUps: mapFollowUps(raw.followUps)
  }
}

function mapResultV2(raw: unknown): AnalysisResultV2 {
  if (!isRecord(raw)) {
    return unknownResult(undefined, raw)
  }
  const kind = raw.kind
  if (kind === 'AGGREGATE') return mapAggregate(raw)
  if (kind === 'PERIOD_COMPARE') return mapPeriodCompare(raw)
  if (kind === 'NET_CASH_FLOW') return mapNetCashFlow(raw)
  if (kind === 'BREAKDOWN') return mapBreakdown(raw)
  if (kind === 'TREND') return mapTrend(raw)
  if (kind === 'AVERAGE_BY_PERIOD') return mapAverageByPeriod(raw)
  if (kind === 'TRANSACTIONS') return mapTransactions(raw)
  if (kind === 'UNKNOWN') return unknownResult(asOptionalString(raw.originalKind), raw)
  return unknownResult(typeof kind === 'string' ? kind : undefined, raw)
}

function mapAggregate(raw: Record<string, unknown>): AnalysisAggregateResult {
  const values = isRecord(raw.values) ? raw.values : {}
  return {
    ...mapBase(raw),
    kind: 'AGGREGATE',
    values: {
      value: asNumberOrString(values.value, 0),
      count: asNumber(values.count, 0)
    }
  }
}

function mapPeriodCompare(raw: Record<string, unknown>): AnalysisPeriodCompareResult {
  const values = isRecord(raw.values) ? raw.values : {}
  return {
    ...mapBase(raw),
    kind: 'PERIOD_COMPARE',
    values: {
      current: asNumberOrString(values.current, 0),
      previous: asNumberOrString(values.previous, 0),
      difference: asNumberOrString(values.difference, 0),
      changeRate: values.changeRate == null ? null : asNumberOrString(values.changeRate, 0)
    }
  }
}

function mapNetCashFlow(raw: Record<string, unknown>): AnalysisNetCashFlowResult {
  const values = isRecord(raw.values) ? raw.values : {}
  return {
    ...mapBase(raw),
    kind: 'NET_CASH_FLOW',
    values: {
      income: asNumberOrString(values.income, 0),
      expense: asNumberOrString(values.expense, 0),
      net: asNumberOrString(values.net, 0)
    }
  }
}

function mapBreakdown(raw: Record<string, unknown>): AnalysisBreakdownResult {
  const rows = Array.isArray(raw.rows) ? raw.rows : []
  return {
    ...mapBase(raw),
    kind: 'BREAKDOWN',
    associationShare: Boolean(raw.associationShare),
    rows: rows.filter(isRecord).map(row => ({
      id: typeof row.id === 'number' ? row.id : row.id == null ? null : undefined,
      label: typeof row.label === 'string' ? row.label : '未知',
      parentLabel: typeof row.parentLabel === 'string' ? row.parentLabel : row.parentLabel == null ? null : undefined,
      value: asNumberOrString(row.value, 0),
      count: asNumber(row.count, 0),
      percentage: asNumberOrString(row.percentage, 0)
    }))
  }
}

function mapTrend(raw: Record<string, unknown>): AnalysisTrendResult {
  return {
    ...mapBase(raw),
    kind: 'TREND',
    points: mapPoints(raw.points)
  }
}

function mapAverageByPeriod(raw: Record<string, unknown>): AnalysisAverageByPeriodResult {
  const values = isRecord(raw.values) ? raw.values : {}
  return {
    ...mapBase(raw),
    kind: 'AVERAGE_BY_PERIOD',
    points: mapPoints(raw.points),
    values: { average: asNumberOrString(values.average, 0) }
  }
}

function mapTransactions(raw: Record<string, unknown>): AnalysisTransactionsResult {
  const values = isRecord(raw.values) ? raw.values : {}
  const transactions = Array.isArray(raw.transactions) ? raw.transactions : []
  return {
    ...mapBase(raw),
    kind: 'TRANSACTIONS',
    values: {
      totalCount: asNumber(values.totalCount, 0),
      displayedCount: asNumber(values.displayedCount, transactions.length),
      detailsOmitted: Boolean(values.detailsOmitted)
    },
    transactions: transactions.filter(isRecord).map(item => ({
      id: asNumber(item.id, 0),
      amount: asNumberOrString(item.amount, 0),
      type: item.type === 'INCOME' ? 'INCOME' : 'EXPENSE',
      date: String(item.date ?? ''),
      description: String(item.description ?? ''),
      category: item.category == null ? null : String(item.category),
      parentCategory: item.parentCategory == null ? null : String(item.parentCategory),
      account: item.account == null ? null : String(item.account),
      merchant: item.merchant == null ? null : String(item.merchant)
    }))
  }
}

function unknownResult(originalKind: string | undefined, raw: unknown): AnalysisUnknownResult {
  const base = isRecord(raw) ? mapBase(raw) : mapBase({})
  return {
    ...base,
    kind: 'UNKNOWN',
    originalKind,
    raw,
    warnings: base.warnings.length
      ? base.warnings
      : [{ code: 'UNKNOWN_KIND', message: '无法展示该结果类型' }]
  }
}

function mapBase(raw: Record<string, unknown>): AnalysisResultBase {
  return {
    title: typeof raw.title === 'string' ? raw.title : '',
    metric: raw.metric === 'COUNT' || raw.metric === 'AVG' ? raw.metric : 'SUM',
    unit: raw.unit === 'COUNT' || raw.unit === 'PERCENT' ? raw.unit : 'CNY',
    transactionType: raw.transactionType === 'INCOME' || raw.transactionType === 'EXPENSE'
      ? raw.transactionType
      : null,
    period: mapPeriod(raw.period),
    filters: isRecord(raw.filters) ? raw.filters : {},
    warnings: mapWarnings(raw.warnings)
  }
}

function mapPeriod(raw: unknown): AnalysisResultBase['period'] {
  if (!isRecord(raw) || !isRecord(raw.current)) {
    return { current: { start: '', end: '' } }
  }
  const current = {
    start: String(raw.current.start ?? ''),
    end: String(raw.current.end ?? '')
  }
  if (!isRecord(raw.previous)) {
    return { current }
  }
  return {
    current,
    previous: {
      start: String(raw.previous.start ?? ''),
      end: String(raw.previous.end ?? '')
    }
  }
}

function mapPoints(raw: unknown): AnalysisTrendResult['points'] {
  if (!Array.isArray(raw)) return []
  return raw.filter(isRecord).map(point => ({
    period: String(point.period ?? ''),
    income: asNumberOrString(point.income, 0),
    expense: asNumberOrString(point.expense, 0),
    value: asNumberOrString(point.value, 0),
    count: asNumber(point.count, 0)
  }))
}

function mapQueryResultV1(raw: unknown): AnalysisQueryResult {
  const rec = isRecord(raw) ? raw : {}
  const rows = Array.isArray(rec.rows) ? rec.rows as unknown[][] : []
  return {
    queryType: typeof rec.queryType === 'string' ? rec.queryType : '',
    title: typeof rec.title === 'string' ? rec.title : null,
    metric: typeof rec.metric === 'string' ? rec.metric : null,
    rows,
    rowCount: typeof rec.rowCount === 'number' ? rec.rowCount : rows.length
  }
}

function mapWarnings(raw: unknown): Array<{ code: string; message: string }> {
  if (!Array.isArray(raw)) return []
  return raw.map(item => {
    if (!isRecord(item)) {
      return { code: '', message: String(item ?? '') }
    }
    return {
      code: String(item.code ?? ''),
      message: String(item.message ?? '')
    }
  })
}

function mapFollowUps(raw: unknown): Array<{ label: string; question: string }> {
  if (!Array.isArray(raw)) return []
  return raw.map(item => {
    if (!isRecord(item)) {
      const text = String(item ?? '')
      return { label: text, question: text }
    }
    return {
      label: String(item.label ?? ''),
      question: String(item.question ?? '')
    }
  })
}

function toStringList(raw: unknown): string[] {
  if (!Array.isArray(raw)) return []
  return raw.map(item => String(item))
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value)
}

function isStatus(value: unknown): value is AnalysisPayloadV2['status'] {
  return typeof value === 'string' && KNOWN_STATUSES.has(value as AnalysisPayloadV2['status'])
}

function asNumber(value: unknown, fallback: number): number {
  return typeof value === 'number' && Number.isFinite(value) ? value : fallback
}

function asNumberOrString(value: unknown, fallback: number): number | string {
  if (typeof value === 'number' && Number.isFinite(value)) return value
  if (typeof value === 'string') return value
  return fallback
}

function asOptionalString(value: unknown): string | undefined {
  return typeof value === 'string' ? value : undefined
}
