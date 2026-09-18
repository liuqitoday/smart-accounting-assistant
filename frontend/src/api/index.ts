import { del, get, getOptional, post, put, queryString, rawFetch } from '@/api/http'
import type {
  Account,
  AccountType,
  AnalysisChatResponse,
  AnalysisMessage,
  AuthResponse,
  Category,
  CategoryStatistic,
  GenerateRecurringBillsResult,
  ImportResult,
  Ledger,
  LedgerMember,
  LedgerRole,
  PageResponse,
  RecurringBill,
  SaveTransactionRequest,
  SaveRecurringBillRequest,
  StatisticsSummary,
  Tag,
  Transaction,
  TransactionFilters,
  TransactionParseResponse,
  TransactionType,
  UpdateTransactionRequest,
  TrendData
} from '@/types'

export const authApi = {
  login(username: string, password: string) {
    return post<AuthResponse>('/api/auth/login', { username, password })
  },
  register(username: string, password: string) {
    return post<AuthResponse>('/api/auth/register', { username, password })
  },
  me() {
    return getOptional<AuthResponse>('/api/auth/me')
  },
  logout() {
    return post<void>('/api/auth/logout')
  },
  changePassword(payload: { currentPassword: string; newPassword: string }) {
    return put<void>('/api/auth/password', payload)
  }
}

export const ledgerApi = {
  list() {
    return get<Ledger[]>('/api/ledgers')
  },
  create(payload: Pick<Ledger, 'name' | 'description' | 'icon' | 'color'>) {
    return post<Ledger>('/api/ledgers', payload)
  },
  update(id: number, payload: Partial<Pick<Ledger, 'name' | 'description' | 'icon' | 'color'>>) {
    return put<Ledger>(`/api/ledgers/${id}`, payload)
  },
  delete(id: number) {
    return del<void>(`/api/ledgers/${id}`)
  },
  setDefault(id: number) {
    return put<void>(`/api/ledgers/${id}/default`)
  },
  members(id: number) {
    return get<LedgerMember[]>(`/api/ledgers/${id}/members`)
  },
  inviteMember(id: number, username: string, role: LedgerRole) {
    return post<LedgerMember>(`/api/ledgers/${id}/members`, { username, role })
  },
  updateMemberRole(id: number, memberUsername: string, role: LedgerRole) {
    return put<LedgerMember>(`/api/ledgers/${id}/members/${encodeURIComponent(memberUsername)}`, { role })
  },
  removeMember(id: number, memberUsername: string) {
    return del<void>(`/api/ledgers/${id}/members/${encodeURIComponent(memberUsername)}`)
  },
  leave(id: number) {
    return post<void>(`/api/ledgers/${id}/leave`)
  },
  transfer(id: number, username: string) {
    return post<void>(`/api/ledgers/${id}/transfer`, { username })
  }
}

export const categoriesApi = {
  list(type?: TransactionType | '') {
    return get<Category[]>('/api/categories', { type })
  }
}

export const transactionsApi = {
  parseOnly(text: string) {
    return post<TransactionParseResponse>('/api/transactions/parse-only', { text })
  },
  save(payload: SaveTransactionRequest) {
    return post<TransactionParseResponse>('/api/transactions/save', payload)
  },
  list(filters: TransactionFilters = {}) {
    return get<PageResponse<Transaction>>('/api/transactions', filters as Record<string, unknown>)
  },
  get(id: number) {
    return get<Transaction>(`/api/transactions/${id}`)
  },
  update(id: number, payload: UpdateTransactionRequest) {
    return put<Transaction>(`/api/transactions/${id}`, payload)
  },
  delete(id: number) {
    return del<void>(`/api/transactions/${id}`)
  },
  restore(id: number) {
    return post<void>(`/api/transactions/${id}/restore`)
  },
  updateTags(id: number, tagIds: number[]) {
    return post<void>(`/api/transactions/${id}/tags`, { tagIds })
  },
  async export(filters: TransactionFilters = {}) {
    const response = await rawFetch(`/api/transactions/export${queryString(filters as Record<string, unknown>)}`)
    return response.blob()
  },
  import(file: File) {
    const data = new FormData()
    data.append('file', file)
    return post<ImportResult>('/api/transactions/import', data)
  }
}

export const recurringBillsApi = {
  list() {
    return get<RecurringBill[]>('/api/recurring-bills')
  },
  create(payload: SaveRecurringBillRequest) {
    return post<RecurringBill>('/api/recurring-bills', payload)
  },
  update(id: number, payload: SaveRecurringBillRequest) {
    return put<RecurringBill>(`/api/recurring-bills/${id}`, payload)
  },
  setEnabled(id: number, enabled: boolean) {
    return put<RecurringBill>(`/api/recurring-bills/${id}/enabled`, { enabled })
  },
  delete(id: number) {
    return del<void>(`/api/recurring-bills/${id}`)
  },
  generateDue() {
    return post<GenerateRecurringBillsResult>('/api/recurring-bills/generate-due')
  },
  generateDueForBill(id: number) {
    return post<GenerateRecurringBillsResult>(`/api/recurring-bills/${id}/generate-due`)
  }
}

export const statisticsApi = {
  summary(period: string) {
    return get<StatisticsSummary>('/api/statistics/summary', { period })
  },
  byCategory(period: string, type: TransactionType = 'EXPENSE') {
    return get<CategoryStatistic[]>('/api/statistics/by-category', { period, type })
  },
  trend(months = 6) {
    return get<TrendData[]>('/api/statistics/trend', { months })
  },
  dailyTrend(period: string) {
    return get<TrendData[]>('/api/statistics/trend/daily', { period })
  },
  topExpenses(period: string) {
    return get<Transaction[]>('/api/statistics/top-expenses', { period })
  },
  recent() {
    return get<Transaction[]>('/api/statistics/recent')
  }
}

export const accountsApi = {
  list() {
    return get<Account[]>('/api/accounts')
  },
  create(payload: {
    name: string
    type: AccountType
    initialBalance?: number | string
    icon?: string
    color?: string
  }) {
    return post<Account>('/api/accounts', payload)
  },
  update(id: number, payload: Partial<Pick<Account, 'name' | 'type' | 'initialBalance' | 'icon' | 'color' | 'active'>>) {
    return put<Account>(`/api/accounts/${id}`, payload)
  },
  /** 设置当前用户在本账本的默认账户；传 null 清除（默认账户是可选设置） */
  setDefault(accountId: number | null) {
    return put<void>('/api/accounts/default', { accountId })
  },
  delete(id: number) {
    return del<void>(`/api/accounts/${id}`)
  }
}

export const tagsApi = {
  list() {
    return get<Tag[]>('/api/tags')
  },
  create(payload: Pick<Tag, 'name' | 'color'>) {
    return post<Tag>('/api/tags', payload)
  },
  update(id: number, payload: Partial<Pick<Tag, 'name' | 'color'>>) {
    return put<Tag>(`/api/tags/${id}`, payload)
  },
  delete(id: number) {
    return del<void>(`/api/tags/${id}`)
  }
}

export const transfersApi = {
  create(payload: {
    fromAccountId: number
    toAccountId: number
    amount: number | string
    transferDate?: string
    note?: string | null
  }) {
    return post<Transaction>('/api/transfers', payload)
  }
}

export const analysisApi = {
  chat(question: string) {
    return post<AnalysisChatResponse>('/api/analysis/chat', { question })
  },
  messages(page = 0, size = 20) {
    return get<PageResponse<AnalysisMessage>>('/api/analysis/messages', { page, size })
  },
  clearMessages() {
    return del<void>('/api/analysis/messages')
  }
}
