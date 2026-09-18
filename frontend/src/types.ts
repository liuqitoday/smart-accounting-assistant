export type TransactionType = 'INCOME' | 'EXPENSE' | 'TRANSFER'
export type RegularTransactionType = Exclude<TransactionType, 'TRANSFER'>
export type RecurringFrequency = 'MONTHLY'
export type LedgerRole = 'OWNER' | 'EDITOR' | 'VIEWER'
export type AccountType = 'CASH' | 'DEBIT_CARD' | 'VIRTUAL' | 'INVESTMENT' | 'PREPAID' | 'OTHER'

export interface ApiResponse<T> {
  success: boolean
  message: string
  data: T
  errorCode?: string
}

export interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  size: number
  number: number
  first: boolean
  last: boolean
}

export interface AuthResponse {
  username: string
}

export interface Category {
  id: number
  name: string
  parentId?: number | null
  parentName?: string | null
  level: number
  type: TransactionType
  description?: string | null
  children?: Category[]
  confidence?: number
  reason?: string
}

export interface Tag {
  id: number
  name: string
  color: string
  system: boolean
  createdAt?: string
}

export interface Account {
  id: number
  name: string
  type: AccountType
  initialBalance: number | string
  currentBalance: number | string
  icon?: string | null
  color?: string | null
  active: boolean
  /** 是否为当前用户在当前账本的默认账户（个人偏好，同一账本不同用户结果可能不同） */
  default?: boolean
  transactionCount: number
  createdAt?: string
}

export interface Ledger {
  id: number
  name: string
  description?: string | null
  icon?: string | null
  color?: string | null
  myRole: LedgerRole
  memberCount: number
  owner: boolean
  default: boolean
  createdAt?: string
}

export interface LedgerMember {
  username: string
  role: LedgerRole
  joinedAt?: string
}

export interface Transaction {
  id: number
  amount: number | string
  type: TransactionType
  description: string
  originalText: string
  categoryId: number
  category?: string | null
  parentCategoryId?: number | null
  parentCategoryName?: string | null
  transactionDate: string
  note?: string | null
  relatedUser?: string | null
  parsedMerchant?: string | null
  parsedAmount?: number | string | null
  confidenceScore?: number | string | null
  aiModelUsed?: string | null
  accountId?: number | null
  counterAccountId?: number | null
  recurringBillId?: number | null
  recurringOccurrenceDate?: string | null
  tags?: Tag[]
  createdAt?: string
  updatedAt?: string
}

export interface RecurringBill {
  id: number
  name: string
  enabled: boolean
  amount: number | string
  type: TransactionType
  description: string
  categoryId?: number | null
  accountId?: number | null
  counterAccountId?: number | null
  parsedMerchant?: string | null
  relatedUser?: string | null
  note?: string | null
  frequency: RecurringFrequency
  dayOfMonth: number
  startDate: string
  endDate?: string | null
  nextRunDate: string
  createdBy?: string
  createdAt?: string
  updatedAt?: string
  tags?: Tag[]
}

export interface SaveRecurringBillRequest {
  name: string
  enabled?: boolean
  amount: number | string
  type: TransactionType
  description: string
  categoryId?: number | null
  accountId?: number | null
  counterAccountId?: number | null
  parsedMerchant?: string | null
  relatedUser?: string | null
  note?: string | null
  frequency: RecurringFrequency
  dayOfMonth: number
  startDate: string
  endDate?: string | null
  tagIds?: number[]
}

export interface GenerateRecurringBillsResult {
  processedRuleCount: number
  generatedCount: number
  skippedCount: number
  truncatedRuleCount: number
  truncated: boolean
  generatedTransactionIds: number[]
}

export interface TransactionParseResponse {
  id?: number
  amount: number | string
  type: TransactionType
  description: string
  originalText: string
  categoryId: number
  categoryName?: string | null
  parentCategoryId?: number | null
  parentCategoryName?: string | null
  transactionDate: string
  parsedMerchant?: string | null
  confidenceScore?: number | string | null
  aiModelUsed?: string | null
  note?: string | null
  relatedUser?: string | null
  aiSuggestedCategoryId?: number | null
  learnedFromHistory?: boolean | null
  fallbackUsed?: boolean
  tags?: Tag[]
  accountId?: number | null
}

export interface SaveTransactionRequest {
  amount: number | string
  type: RegularTransactionType
  description: string
  originalText: string
  categoryId: number
  transactionDate: string
  parsedMerchant?: string | null
  confidenceScore?: number | string | null
  aiModelUsed?: string | null
  note?: string | null
  relatedUser?: string | null
  aiSuggestedCategoryId?: number | null
  accountId?: number | null
}

export interface UpdateTransactionRequest {
  amount: number | string
  type: RegularTransactionType
  description: string
  categoryId: number
  transactionDate: string
  parsedMerchant?: string | null
  note?: string | null
  relatedUser?: string | null
  accountId?: number | null
}

export interface TransactionFilters {
  keyword?: string
  type?: TransactionType | ''
  startDate?: string
  endDate?: string
  tagIds?: number[]
  categoryId?: number
  accountId?: number
  merchant?: string
  minAmount?: number | string
  maxAmount?: number | string
  createdBy?: string
  page?: number
  size?: number
  sort?: string
}

export interface StatisticsSummary {
  totalIncome: number | string
  totalExpense: number | string
  balance: number | string
  transactionCount: number
  periodStart: string
  periodEnd: string
  /** 上一同期收入（环比用） */
  prevTotalIncome: number | string
  /** 上一同期支出 */
  prevTotalExpense: number | string
  /** 收入环比百分比（正=增、负=减；上期为 0 时为 null） */
  incomeChangePct: number | null
  /** 支出环比百分比 */
  expenseChangePct: number | null
}

export interface CategoryStatistic {
  categoryId: number
  categoryName: string
  parentCategoryName?: string | null
  amount: number | string
  percentage: number | string
  transactionCount: number
}

export interface TrendData {
  month: string
  income: number | string
  expense: number | string
  balance: number | string
}

export interface ImportResult {
  totalRows: number
  createdCount: number
  updatedCount: number
  errorCount: number
  errors: Array<{
    row: number
    field: string
    message: string
  }>
}

export interface AnalysisMessage {
  id: number
  role: 'USER' | 'ASSISTANT'
  content: string
  payload?: string | null  // JSON string
  status: 'OK' | 'FAILED'
  createdAt: string
}

export interface AnalysisChatResponse {
  userMessage: AnalysisMessage
  assistantMessage: AnalysisMessage
}

export interface AnalysisQueryResult {
  queryType: string
  title?: string | null
  metric?: string | null
  rows: unknown[][]
  rowCount: number
}

export type AnalysisUnit = 'CNY' | 'COUNT' | 'PERCENT'
export type AnalysisResultKind =
  | 'AGGREGATE' | 'PERIOD_COMPARE' | 'NET_CASH_FLOW'
  | 'BREAKDOWN' | 'TREND' | 'AVERAGE_BY_PERIOD' | 'TRANSACTIONS' | 'UNKNOWN'

export interface AnalysisResultBase {
  title: string
  metric: 'SUM' | 'COUNT' | 'AVG'
  unit: AnalysisUnit
  transactionType: 'INCOME' | 'EXPENSE' | null
  period: { current: { start: string; end: string }; previous?: { start: string; end: string } }
  filters: Record<string, unknown>
  warnings: Array<{ code: string; message: string }>
}
export interface AnalysisAggregateResult extends AnalysisResultBase {
  kind: 'AGGREGATE'
  values: { value: number | string; count: number }
}
export interface AnalysisPeriodCompareResult extends AnalysisResultBase {
  kind: 'PERIOD_COMPARE'
  values: { current: number | string; previous: number | string; difference: number | string; changeRate: number | string | null }
}
export interface AnalysisNetCashFlowResult extends AnalysisResultBase {
  kind: 'NET_CASH_FLOW'
  values: { income: number | string; expense: number | string; net: number | string }
}
export interface AnalysisBreakdownResult extends AnalysisResultBase {
  kind: 'BREAKDOWN'
  rows: Array<{ id?: number | null; label: string; parentLabel?: string | null; value: number | string; count: number; percentage: number | string }>
  associationShare: boolean
}
export interface AnalysisTrendResult extends AnalysisResultBase {
  kind: 'TREND'
  points: Array<{ period: string; income: number | string; expense: number | string; value: number | string; count: number }>
}
export interface AnalysisAverageByPeriodResult extends AnalysisResultBase {
  kind: 'AVERAGE_BY_PERIOD'
  points: Array<{ period: string; income: number | string; expense: number | string; value: number | string; count: number }>
  values: { average: number | string }
}
export interface AnalysisTransactionsResult extends AnalysisResultBase {
  kind: 'TRANSACTIONS'
  values: { totalCount: number; displayedCount: number; detailsOmitted: boolean }
  transactions: Array<{ id: number; amount: number | string; type: 'INCOME' | 'EXPENSE'; date: string; description: string; category: string | null; parentCategory: string | null; account: string | null; merchant: string | null }>
}
export interface AnalysisUnknownResult extends AnalysisResultBase {
  kind: 'UNKNOWN'
  originalKind?: string
  raw: unknown
}
export type AnalysisResultV2 = AnalysisAggregateResult | AnalysisPeriodCompareResult | AnalysisNetCashFlowResult
  | AnalysisBreakdownResult | AnalysisTrendResult | AnalysisAverageByPeriodResult
  | AnalysisTransactionsResult | AnalysisUnknownResult

export interface AnalysisFollowUp {
  label: string
  question: string
}

export interface AnalysisPayloadV2 {
  schemaVersion: 2
  rawPlan: unknown
  normalizedPlan: unknown
  results: AnalysisResultV2[]
  status: 'OK' | 'CLARIFICATION_REQUIRED' | 'INVALID_PLAN' | 'NO_DATA'
    | 'PARTIAL_RESULT' | 'AI_UNAVAILABLE' | 'QUERY_FAILED' | 'RATE_LIMITED'
  warnings: Array<{ code: string; message: string }>
  followUps: AnalysisFollowUp[]
}

export type ParsedAnalysisPayload =
  | { version: 1; results: AnalysisQueryResult[]; warnings: string[]; followUps: string[] }
  | { version: 2; payload: AnalysisPayloadV2 }
  | { version: 'invalid'; warnings: string[] }
