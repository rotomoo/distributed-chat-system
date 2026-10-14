import { act, renderHook } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AWAY_AFTER_MS, useAwayState } from './useAwayState.js'

const MINUTE = 60_000
let visibility = 'visible'

function setVisibility(next) {
  visibility = next
  document.dispatchEvent(new Event('visibilitychange'))
}

function render(connectionId = 1) {
  const send = vi.fn(() => true)
  const utils = renderHook(({ id }) => useAwayState({ connectionId: id, send }), { initialProps: { id: connectionId } })
  send.mockClear() // 연결 직후 지금 상태 알림은 따로 확인한다
  return { ...utils, send }
}

const presenceSent = (send) => send.mock.calls.map(([payload]) => payload.state)

describe('[단위] useAwayState', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    visibility = 'visible'
    Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => visibility })
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('success case : 10분 동안 입력이 없으면 away 를 한 번 보낸다', () => {
    // arrange
    const { result, send } = render()

    // act
    act(() => vi.advanceTimersByTime(AWAY_AFTER_MS - 1))
    const before = presenceSent(send)
    act(() => vi.advanceTimersByTime(1))
    act(() => vi.advanceTimersByTime(30 * MINUTE))

    // assert
    expect(AWAY_AFTER_MS).toBe(10 * MINUTE)
    expect(before).toEqual([])
    expect(send.mock.calls).toEqual([[{ type: 'presence', state: 'away' }]])
    expect(result.current).toBe('away')
  })

  it('success case : 입력이 있으면 그때부터 다시 10분을 잰다', () => {
    // arrange
    const { send } = render()

    // act
    act(() => vi.advanceTimersByTime(9 * MINUTE))
    act(() => window.dispatchEvent(new Event('mousemove')))
    act(() => vi.advanceTimersByTime(10 * MINUTE - 1))
    const before = presenceSent(send)
    act(() => vi.advanceTimersByTime(1))

    // assert
    expect(before).toEqual([])
    expect(presenceSent(send)).toEqual(['away'])
  })

  it('success case : away 에서 키보드 / 터치 / 스크롤 입력이 오면 active 를 보내고, 바뀔 때만 보낸다', () => {
    // arrange
    const { result, send } = render()
    act(() => vi.advanceTimersByTime(AWAY_AFTER_MS))

    // act
    act(() => window.dispatchEvent(new Event('keydown')))
    act(() => window.dispatchEvent(new Event('touchstart')))
    act(() => document.dispatchEvent(new Event('scroll')))

    // assert
    expect(presenceSent(send)).toEqual(['away', 'active'])
    expect(result.current).toBe('active')
  })

  it('fail case : active 인 동안의 입력은 아무것도 보내지 않는다', () => {
    // arrange
    const { send } = render()

    // act
    act(() => window.dispatchEvent(new Event('mousemove')))
    act(() => window.dispatchEvent(new Event('mousedown')))

    // assert
    expect(send).not.toHaveBeenCalled()
  })

  it('success case : 탭이 10분 동안 숨겨져 있으면 away, 다시 보이면 active', () => {
    // arrange
    const { send } = render()

    // act
    act(() => setVisibility('hidden'))
    act(() => vi.advanceTimersByTime(AWAY_AFTER_MS))
    act(() => setVisibility('visible'))

    // assert
    expect(presenceSent(send)).toEqual(['away', 'active'])
  })

  it('fail case : 탭을 숨기는 것은 입력이 아니다 (마지막 입력부터 10분이면 away)', () => {
    // arrange
    const { send } = render()
    act(() => vi.advanceTimersByTime(5 * MINUTE))

    // act
    act(() => setVisibility('hidden'))
    act(() => vi.advanceTimersByTime(5 * MINUTE))

    // assert
    expect(presenceSent(send)).toEqual(['away'])
  })

  it('success case : (재)연결될 때마다 지금 상태를 보낸다', () => {
    // arrange
    const { rerender, send } = render(1)

    // act
    rerender({ id: 2 })
    act(() => vi.advanceTimersByTime(AWAY_AFTER_MS))
    send.mockClear()
    rerender({ id: 3 })

    // assert
    expect(send.mock.calls).toEqual([[{ type: 'presence', state: 'away' }]])
  })

  it('success case : 첫 연결에서도 지금 상태(active)를 보내고, 연결 전(0)에는 보내지 않는다', () => {
    // arrange
    const send = vi.fn(() => true)
    const { rerender } = renderHook(({ id }) => useAwayState({ connectionId: id, send }), { initialProps: { id: 0 } })
    const beforeConnected = send.mock.calls.length

    // act
    rerender({ id: 1 })

    // assert
    expect(beforeConnected).toBe(0)
    expect(send.mock.calls).toEqual([[{ type: 'presence', state: 'active' }]])
  })

  it('success case : 화면이 내려가면(로그아웃) 감시를 멈춘다', () => {
    // arrange
    const { unmount, send } = render()

    // act
    unmount()
    act(() => vi.advanceTimersByTime(AWAY_AFTER_MS * 2))
    window.dispatchEvent(new Event('keydown'))

    // assert
    expect(send).not.toHaveBeenCalled()
  })
})
