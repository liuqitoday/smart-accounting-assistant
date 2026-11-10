import { mount } from '@vue/test-utils'
import { defineComponent } from 'vue'
import { beforeEach, describe, expect, it } from 'vitest'
import { useDraft } from './useDraft'
import { clearUserLocalData, rememberSession } from '@/utils/localState'

beforeEach(() => { localStorage.clear(); rememberSession('alice'); localStorage.setItem('activeLedgerId', '1') })

describe('本机草稿', () => {
  function open() {
    let result!: ReturnType<typeof useDraft<{ text: string }>>
    const wrapper = mount(defineComponent({ setup() { result = useDraft('entry', { text: '' }); return () => null } }))
    return { wrapper, ...result }
  }
  it('刷新后恢复，换账本和换用户均隔离', () => {
    const first = open()
    first.draft.value.text = '午饭28元'
    first.wrapper.unmount()
    const again = open()
    expect(again.draft.value.text).toBe('午饭28元')
    again.wrapper.unmount()
    localStorage.setItem('activeLedgerId', '2')
    const second = open()
    expect(second.draft.value.text).toBe('')
    second.wrapper.unmount()
    rememberSession('bob')
    localStorage.setItem('activeLedgerId', '1')
    const otherUser = open()
    expect(otherUser.draft.value.text).toBe('')
    otherUser.wrapper.unmount()
  })
  it('成功清理后不恢复已提交草稿，退出登录清除本机草稿', () => {
    const first = open()
    first.draft.value.text = '草稿'
    first.clear()
    first.wrapper.unmount()
    const second = open()
    expect(second.draft.value.text).toBe('')
    second.draft.value.text = '下一笔'
    second.wrapper.unmount()
    clearUserLocalData()
    rememberSession('alice')
    const third = open()
    expect(third.draft.value.text).toBe('')
    third.wrapper.unmount()
  })
})
