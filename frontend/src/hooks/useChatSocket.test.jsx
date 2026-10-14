import { act, renderHook } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { FakeWebSocket } from '../test/FakeWebSocket.js'
import { SILENCE_LIMIT_MS, reconnectDelay, useChatSocket } from './useChatSocket.js'

vi.mock('../api.js', () => ({ webSocketUrl: () => 'ws://localhost/websocket' }))

const sockets = () => FakeWebSocket.instances

describe('[단위] useChatSocket', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    FakeWebSocket.instances = []
    vi.stubGlobal('WebSocket', FakeWebSocket)
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
  })

  it('success case : 버튼 없이 마운트하자마자 웹소켓을 연다', () => {
    // arrange & act
    const { result } = renderHook(() => useChatSocket())

    // assert
    expect(sockets()).toHaveLength(1)
    expect(sockets()[0].url).toBe('ws://localhost/websocket')
    expect(result.current.status).toBe('connecting')
  })

  it('fail case : 101(onopen)만으로는 연결됨이 아니다. connected 신호를 받아야 연결됨', () => {
    // arrange
    const { result } = renderHook(() => useChatSocket())

    // act
    act(() => {
      sockets()[0].readyState = FakeWebSocket.OPEN
      sockets()[0].onopen?.()
    })
    const beforeSignal = result.current.status
    act(() => sockets()[0].connected())

    // assert
    expect(beforeSignal).toBe('connecting')
    expect(result.current.status).toBe('connected')
    expect(result.current.connectionId).toBe(1)
  })

  it('success case : 끊기면 1s, 2s, 4s, 8s, 10s, 10s 간격으로 다시 붙는다', () => {
    // arrange
    const { result } = renderHook(() => useChatSocket())
    const waits = []

    // act
    for (let i = 0; i < 6; i++) {
      const count = sockets().length
      act(() => sockets().at(-1).close())
      let waited = 0
      while (sockets().length === count) {
        act(() => vi.advanceTimersByTime(500))
        waited += 500
      }
      waits.push(waited)
    }

    // assert
    expect(waits).toEqual([1000, 2000, 4000, 8000, 10000, 10000])
    expect(result.current.status).toBe('reconnecting')
  })

  it('success case : 다시 connected 를 받으면 대기 시간이 1s 부터 다시 시작한다', () => {
    // arrange
    renderHook(() => useChatSocket())
    act(() => sockets()[0].close())
    act(() => vi.advanceTimersByTime(1000))
    act(() => sockets()[1].close())
    act(() => vi.advanceTimersByTime(2000))

    // act
    act(() => sockets()[2].connected())
    act(() => sockets()[2].close())
    act(() => vi.advanceTimersByTime(999))
    const beforeOneSecond = sockets().length
    act(() => vi.advanceTimersByTime(1))

    // assert
    expect(beforeOneSecond).toBe(3)
    expect(sockets()).toHaveLength(4)
  })

  it('success case : 재연결될 때마다 connectionId 가 오르고 상태가 connected 로 돌아온다', () => {
    // arrange
    const { result } = renderHook(() => useChatSocket())
    act(() => sockets()[0].connected())

    // act
    act(() => sockets()[0].close())
    const whileDown = result.current.status
    act(() => vi.advanceTimersByTime(1000))
    act(() => sockets()[1].connected())

    // assert
    expect(whileDown).toBe('reconnecting')
    expect(result.current.status).toBe('connected')
    expect(result.current.connectionId).toBe(2)
  })

  it('success case : 언마운트(로그아웃)하면 소켓을 닫고 다시 붙지 않는다', () => {
    // arrange
    const { unmount } = renderHook(() => useChatSocket())
    act(() => sockets()[0].connected())

    // act
    unmount()
    act(() => vi.advanceTimersByTime(60_000))

    // assert
    expect(sockets()[0].readyState).toBe(FakeWebSocket.CLOSED)
    expect(sockets()).toHaveLength(1)
  })

  it('success case : 재연결 대기 중에 언마운트해도 다시 붙지 않는다', () => {
    // arrange
    const { unmount } = renderHook(() => useChatSocket())
    act(() => sockets()[0].close())

    // act
    unmount()
    act(() => vi.advanceTimersByTime(60_000))

    // assert
    expect(sockets()).toHaveLength(1)
  })

  it('success case : 구독한 함수는 message / error 를 받고, 해제하면 더 받지 않는다', () => {
    // arrange
    const { result } = renderHook(() => useChatSocket())
    const listener = vi.fn()
    let unsubscribe
    act(() => {
      unsubscribe = result.current.subscribe(listener)
    })
    act(() => sockets()[0].connected())

    // act
    act(() => sockets()[0].receive({ type: 'message', messageId: 'a', channelId: 11 }))
    act(() => sockets()[0].receive({ type: 'error', code: 3003, message: '채널 멤버가 아닙니다.' }))
    unsubscribe()
    act(() => sockets()[0].receive({ type: 'message', messageId: 'b', channelId: 11 }))

    // assert
    expect(listener.mock.calls.map(([event]) => event.type)).toEqual(['message', 'error'])
  })

  it('success case : 연결됐을 때만 보낸다', () => {
    // arrange
    const { result } = renderHook(() => useChatSocket())
    const payload = { type: 'message', channelId: 11, message: '안녕' }

    // act
    const beforeConnected = result.current.send(payload)
    act(() => sockets()[0].connected())
    const afterConnected = result.current.send(payload)

    // assert
    expect(beforeConnected).toBe(false)
    expect(afterConnected).toBe(true)
    expect(sockets()[0].sent).toEqual([payload])
  })

  it('success case : 대기 시간은 2배씩 늘다가 10초에서 멈춘다', () => {
    // arrange & act
    const delays = [0, 1, 2, 3, 4, 10].map(reconnectDelay)

    // assert
    expect(delays).toEqual([1000, 2000, 4000, 8000, 10000, 10000])
  })

  // ── heartbeat / 조용함 감시 ──────────────────────

  it('success case : ping 을 받으면 바로 pong 으로 답하고, 구독한 함수에는 넘기지 않는다', () => {
    // arrange
    const { result } = renderHook(() => useChatSocket())
    const listener = vi.fn()
    act(() => {
      result.current.subscribe(listener)
    })
    act(() => sockets()[0].connected())

    // act
    act(() => sockets()[0].ping())

    // assert
    expect(sockets()[0].sent).toEqual([{ type: 'pong' }])
    expect(listener).not.toHaveBeenCalled()
  })

  it('success case : 연결된 뒤 60초 동안 아무것도 받지 못하면 소켓을 닫고 평소처럼 다시 붙는다', () => {
    // arrange
    const { result } = renderHook(() => useChatSocket())
    act(() => sockets()[0].connected())

    // act
    act(() => vi.advanceTimersByTime(SILENCE_LIMIT_MS - 1))
    const beforeLimit = [result.current.status, sockets()[0].readyState]
    act(() => vi.advanceTimersByTime(1))
    const afterLimit = [result.current.status, sockets()[0].readyState, sockets().length]
    act(() => vi.advanceTimersByTime(1000))
    act(() => sockets()[1].connected())
    act(() => vi.advanceTimersByTime(10_000))

    // assert
    expect(SILENCE_LIMIT_MS).toBe(60_000)
    expect(beforeLimit).toEqual(['connected', FakeWebSocket.OPEN])
    expect(afterLimit).toEqual(['reconnecting', FakeWebSocket.CLOSED, 1])
    expect(sockets()).toHaveLength(2) // 닫힌 소켓의 close 이벤트로 한 번 더 붙지 않는다
    expect(result.current.connectionId).toBe(2)
  })

  it('success case : 메시지를 받을 때마다 조용함 감시를 다시 잰다', () => {
    // arrange
    const { result } = renderHook(() => useChatSocket())
    act(() => sockets()[0].connected())

    // act
    act(() => vi.advanceTimersByTime(50_000))
    act(() => sockets()[0].receive({ type: 'message', messageId: 'a', channelId: 11 }))
    act(() => vi.advanceTimersByTime(50_000))
    const stillUp = result.current.status
    act(() => vi.advanceTimersByTime(10_000))

    // assert
    expect(stillUp).toBe('connected')
    expect(result.current.status).toBe('reconnecting')
  })

  it('success case : ping 도 조용함 감시를 다시 잰다 (25초마다 ping 이 오면 끊지 않는다)', () => {
    // arrange
    const { result } = renderHook(() => useChatSocket())
    act(() => sockets()[0].connected())

    // act
    for (let i = 0; i < 6; i++) {
      act(() => vi.advanceTimersByTime(25_000))
      act(() => sockets()[0].ping())
    }

    // assert
    expect(result.current.status).toBe('connected')
    expect(sockets()).toHaveLength(1)
  })

  it('fail case : connected 를 받기 전에는 조용함 감시를 하지 않는다', () => {
    // arrange
    const { result } = renderHook(() => useChatSocket())

    // act
    act(() => sockets()[0].receive({ type: 'error', code: 5000, message: '준비 중' }))
    act(() => vi.advanceTimersByTime(SILENCE_LIMIT_MS * 2))

    // assert
    expect(result.current.status).toBe('connecting')
    expect(sockets()).toHaveLength(1)
  })

  it('success case : 로그아웃(언마운트)하면 조용함 감시도 멈춰 다시 붙지 않는다', () => {
    // arrange
    const { unmount } = renderHook(() => useChatSocket())
    act(() => sockets()[0].connected())

    // act
    unmount()
    act(() => vi.advanceTimersByTime(SILENCE_LIMIT_MS * 3))

    // assert
    expect(sockets()).toHaveLength(1)
  })

  it('success case : 서버가 4409(다른 채팅 서버에 연결됨)로 닫아도 평소처럼 다시 붙는다', () => {
    // arrange
    const { result } = renderHook(() => useChatSocket())
    act(() => sockets()[0].connected())

    // act
    act(() => sockets()[0].close(4409))
    const status = result.current.status
    act(() => vi.advanceTimersByTime(1000))

    // assert
    expect(status).toBe('reconnecting')
    expect(sockets()).toHaveLength(2)
  })

  it('success case : 서버가 4500(SESSION_NOT_RELIABLE) 이나 1011 로 닫아도 평소처럼 다시 붙는다', () => {
    // arrange
    const { result } = renderHook(() => useChatSocket())
    act(() => sockets()[0].connected())

    // act
    act(() => sockets()[0].close(4500))
    const afterNotReliable = result.current.status
    act(() => vi.advanceTimersByTime(1000))
    act(() => sockets()[1].connected())
    act(() => sockets()[1].close(1011))
    act(() => vi.advanceTimersByTime(1000))

    // assert
    expect(afterNotReliable).toBe('reconnecting')
    expect(sockets()).toHaveLength(3)
  })
})
