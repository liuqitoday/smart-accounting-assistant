import { describe, expect, it } from 'vitest'
import { formatAnalysisValue, parseAnalysisPayload } from './analysisPayload'

describe('analysisPayload', () => {
  it('normalizes V1 rows without changing their legacy meaning', () => {
    const parsed = parseAnalysisPayload(JSON.stringify({ results: { queries: [
      { queryType: 'AGGREGATE', metric: 'SUM', rows: [[123.45]], rowCount: 1 }
    ] } }))
    if (parsed.version !== 1) throw new Error('expected V1')
    expect(parsed.results[0].rows[0][0]).toBe(123.45)
  })
  it('parses V2 named results and rejects invalid JSON visibly', () => {
    expect(parseAnalysisPayload('{"schemaVersion":2,"results":[],"status":"NO_DATA"}').version).toBe(2)
    expect(parseAnalysisPayload('{not-json}').version).toBe('invalid')
  })
  it('formats COUNT, AVG, CNY and percent with different units', () => {
    expect(formatAnalysisValue(12, 'COUNT')).toBe('12 笔')
    expect(formatAnalysisValue('3280.5', 'CNY')).toBe('¥3,280.50')
    expect(formatAnalysisValue(21.05, 'PERCENT')).toBe('21.05%')
  })
  it('renders empty, unknown kind and association-share warning states', () => {
    const parsed = parseAnalysisPayload(JSON.stringify({ schemaVersion: 2,
      results: [{ kind: 'UNKNOWN_KIND', values: {} }], warnings: [{ code: 'ASSOCIATION_SHARE', message: '标签关联占比' }] }))
    if (parsed.version !== 2) throw new Error('expected V2')
    expect(parsed.payload.warnings[0].code).toBe('ASSOCIATION_SHARE')
  })
})
