import type { Account } from '@/types'

/**
 * 当前用户的默认账户 id：新建交易时用它自动选中账户。
 *
 * 没有默认账户、或默认账户已停用时返回 null —— 调用方据此保持「未指定账户」，
 * 不做任何兜底猜测。
 */
export function resolveDefaultAccountId(accounts: Account[]): number | null {
  return accounts.find(account => account.default && account.active)?.id ?? null
}
