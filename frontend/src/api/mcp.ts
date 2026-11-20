import { get, post } from '@/api/http'

export interface McpConnection {
  id: string
  name: string
  tokenPrefix: string
  scopes: string[]
  ledgerIds: number[]
  defaultLedgerId: number | null
  writable: boolean
  createdAt: string
  expiresAt: string
  lastUsedAt: string | null
  status: 'ACTIVE' | 'REVOKED' | 'EXPIRED'
}

export interface McpIssued { connection: McpConnection; token: string }
export interface McpCall {
  id: string
  toolName: string
  ledgerId: number | null
  transactionId: number | null
  outcome: string
  errorCode: string | null
  createdAt: string
  durationMs: number
}

export const mcpApi = {
  list: () => get<McpConnection[]>('/api/mcp/connections', undefined, { force: true }),
  create: (body: { name: string; ledgerIds: number[]; defaultLedgerId: number | null; writable: boolean; expiresInDays: number }) =>
    post<McpIssued>('/api/mcp/connections', body),
  revoke: (id: string) => post<void>(`/api/mcp/connections/${id}/revoke`),
  rotate: (id: string) => post<McpIssued>(`/api/mcp/connections/${id}/rotate`),
  calls: (id: string) => get<McpCall[]>(`/api/mcp/connections/${id}/calls`, undefined, { force: true })
}
