import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ChatMessage from './ChatMessage.vue'
import type { AnalysisMessage } from '@/types'

function assistantMessage(content: string, payload: string | null): AnalysisMessage {
  return {
    id: 2,
    role: 'ASSISTANT',
    content,
    payload,
    status: 'OK',
    createdAt: '2026-08-25T12:00:01',
  }
}

export function userMessage(content: string): AnalysisMessage {
  return { id: 1, role: 'USER', content, payload: null, status: 'OK', createdAt: '2026-08-25T12:00:00' }
}

export function assistantMessageWithFollowUps(questions: string[]): AnalysisMessage {
  return {
    id: 2,
    role: 'ASSISTANT',
    content: '已完成分析',
    payload: JSON.stringify({
      schemaVersion: 2,
      rawPlan: {},
      normalizedPlan: {},
      results: [],
      status: 'OK',
      warnings: [],
      followUps: questions.map(question => ({ label: question, question })),
    }),
    status: 'OK',
    createdAt: '2026-08-25T12:00:01',
  }
}

describe('ChatMessage', () => {
  it('shows 结果无法解析 for invalid JSON while keeping assistant text', () => {
    const wrapper = mount(ChatMessage, {
      props: {
        message: assistantMessage('本月支出如下', '{not-json}'),
      },
    })
    expect(wrapper.text()).toContain('本月支出如下')
    expect(wrapper.text()).toContain('结果无法解析')
  })

  it('still renders V1 query cards', () => {
    const wrapper = mount(ChatMessage, {
      props: {
        message: assistantMessage(
          '合计如下',
          JSON.stringify({
            results: {
              queries: [
                {
                  queryType: 'AGGREGATE',
                  title: '本月支出',
                  metric: 'SUM',
                  rows: [[123.45]],
                  rowCount: 1,
                },
              ],
            },
          }),
        ),
      },
    })
    expect(wrapper.text()).toContain('合计如下')
    expect(wrapper.text()).toContain('本月支出')
    expect(wrapper.text()).toContain('¥123.45')
  })

  it('emits follow-up when a suggested question is clicked', async () => {
    const wrapper = mount(ChatMessage, {
      props: {
        message: assistantMessageWithFollowUps(['查看明细']),
      },
    })
    expect(wrapper.text()).toContain('查看明细')
    await wrapper.find('button.follow-up').trigger('click')
    expect(wrapper.emitted('follow-up')?.[0]).toEqual(['查看明细'])
  })
})
