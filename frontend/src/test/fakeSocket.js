import { act } from '@testing-library/react'
import { vi } from 'vitest'

// useChatSocket 결과 대역. 테스트가 서버 이벤트(emit)와 연결 상태(status / connectionId)를 직접 정한다
export function fakeSocket({ status = 'connected', connectionId = 1 } = {}) {
  const listeners = new Set()
  return {
    status,
    connectionId,
    send: vi.fn(() => true),
    subscribe: (listener) => {
      listeners.add(listener)
      return () => listeners.delete(listener)
    },
    emit: (event) => act(() => listeners.forEach((listener) => listener(event))),
  }
}

// 대기 중인 Promise 들을 처리하고 화면 갱신까지 반영
export async function flush() {
  await act(async () => {
    for (let i = 0; i < 10; i++) await Promise.resolve()
  })
}
