import { act, renderHook } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { getPresence } from '../api.js'
import { flush } from '../test/fakeSocket.js'
import { PRESENCE_POLL_MS, usePresence } from './usePresence.js'

vi.mock('../api.js', () => ({ getPresence: vi.fn() }))

describe('[단위] usePresence', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.resetAllMocks()
    getPresence.mockResolvedValue([{ userId: 2, status: 'online' }, { userId: 3, status: 'away' }])
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('success case : 팀 접속 상태를 userId → 상태 Map 으로 돌려준다', async () => {
    // arrange & act
    const { result } = renderHook(() => usePresence(1, 1))
    await flush()

    // assert
    expect(getPresence).toHaveBeenCalledWith(1)
    expect([...result.current]).toEqual([[2, 'online'], [3, 'away']])
  })

  it('success case : 15초마다 다시 부른다', async () => {
    // arrange
    renderHook(() => usePresence(1, 1))
    await flush()

    // act
    act(() => vi.advanceTimersByTime(PRESENCE_POLL_MS - 1))
    const before = getPresence.mock.calls.length
    act(() => vi.advanceTimersByTime(1))

    // assert
    expect(PRESENCE_POLL_MS).toBe(15_000)
    expect(before).toBe(1)
    expect(getPresence).toHaveBeenCalledTimes(2)
  })

  it('success case : 다시 연결되면(connectionId 변경) 바로 다시 부른다', async () => {
    // arrange
    const { rerender } = renderHook(({ id }) => usePresence(1, id), { initialProps: { id: 1 } })
    await flush()

    // act
    rerender({ id: 2 })
    await flush()

    // assert
    expect(getPresence).toHaveBeenCalledTimes(2)
  })

  it('fail case : 조회가 실패하면 비운다 (모두 오프라인)', async () => {
    // arrange
    const { result } = renderHook(() => usePresence(1, 1))
    await flush()
    getPresence.mockRejectedValue(new Error('404'))

    // act
    act(() => vi.advanceTimersByTime(PRESENCE_POLL_MS))
    await flush()

    // assert
    expect(result.current.size).toBe(0)
  })

  it('success case : 팀을 바꾸면 이전 팀 상태를 지우고 늦게 온 이전 팀 응답은 버린다', async () => {
    // arrange
    let resolveOld
    getPresence.mockReturnValueOnce(new Promise((resolve) => { resolveOld = resolve })).mockResolvedValueOnce([{ userId: 9, status: 'online' }])
    const { result, rerender } = renderHook(({ teamId }) => usePresence(teamId, 1), { initialProps: { teamId: 1 } })

    // act
    rerender({ teamId: 2 })
    await flush()
    await act(async () => resolveOld([{ userId: 2, status: 'online' }]))

    // assert
    expect([...result.current]).toEqual([[9, 'online']])
  })

  it('fail case : 팀이 없으면 부르지 않는다', async () => {
    // arrange & act
    renderHook(() => usePresence(null, 1))
    await flush()

    // assert
    expect(getPresence).not.toHaveBeenCalled()
  })
})
