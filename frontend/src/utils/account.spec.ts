import { describe, expect, it } from 'vitest'
import { resolveDefaultAccountId } from './account'
import type { Account } from '@/types'

describe('resolveDefaultAccountId', () => {
  it('returns the id of the account flagged as default', () => {
    expect(resolveDefaultAccountId([account(1), account(2, { default: true })])).toBe(2)
  })

  it('returns null when no account is flagged, so nothing gets auto-selected', () => {
    expect(resolveDefaultAccountId([account(1), account(2)])).toBeNull()
  })

  it('ignores a default account that has been deactivated', () => {
    expect(resolveDefaultAccountId([account(1, { default: true, active: false })])).toBeNull()
  })

  it('returns null for an empty account list', () => {
    expect(resolveDefaultAccountId([])).toBeNull()
  })
})

function account(id: number, overrides: Partial<Account> = {}): Account {
  return {
    id,
    name: `账户${id}`,
    type: 'CASH',
    initialBalance: 0,
    currentBalance: 0,
    active: true,
    transactionCount: 0,
    ...overrides
  }
}
