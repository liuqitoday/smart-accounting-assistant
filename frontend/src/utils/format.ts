import type { AccountType, LedgerRole, TransactionType } from '@/types'

export const presetColors = [
  '#EB5E28',
  '#F2A33C',
  '#D85A8A',
  '#A47148',
  '#2F9C63',
  '#7CB342',
  '#3BA8A0',
  '#4A90D9',
  '#8E7CC3',
  '#95876F'
]

export const accountTypeOptions: Array<{ value: AccountType; label: string; icon: string }> = [
  { value: 'CASH', label: '现金', icon: '💵' },
  { value: 'DEBIT_CARD', label: '银行卡', icon: '💳' },
  { value: 'VIRTUAL', label: '网络支付', icon: '📱' },
  { value: 'INVESTMENT', label: '投资理财', icon: '📈' },
  { value: 'PREPAID', label: '储值卡', icon: '🎫' },
  { value: 'OTHER', label: '其他', icon: '🏦' }
]

export function toNumber(value: number | string | null | undefined): number {
  if (value === null || value === undefined || value === '') {
    return 0
  }
  return Number(value)
}

export function formatMoney(value: number | string | null | undefined): string {
  return toNumber(value).toLocaleString('zh-CN', {
    style: 'currency',
    currency: 'CNY',
    minimumFractionDigits: 2
  })
}

export function formatPlainMoney(value: number | string | null | undefined): string {
  return toNumber(value).toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  })
}

export function formatPercent(value: number | string | null | undefined, digits = 1): string {
  return toNumber(value).toFixed(digits)
}

export function transactionTypeLabel(type?: TransactionType | ''): string {
  if (type === 'INCOME') return '收入'
  if (type === 'EXPENSE') return '支出'
  if (type === 'TRANSFER') return '转账'
  return '全部'
}

export function roleLabel(role?: LedgerRole): string {
  const labels: Record<LedgerRole, string> = {
    OWNER: '所有者',
    EDITOR: '可编辑',
    VIEWER: '仅查看'
  }
  return role ? labels[role] : ''
}

export function accountTypeLabel(type?: AccountType): string {
  return accountTypeOptions.find(option => option.value === type)?.label || '其他'
}

export function defaultAccountIcon(type?: AccountType): string {
  return accountTypeOptions.find(option => option.value === type)?.icon || '🏦'
}

export function formatDate(value?: string | null): string {
  if (!value) return '-'
  return value.slice(0, 10)
}

export function today(): string {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
}

export function downloadBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = filename
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  URL.revokeObjectURL(url)
}
