import { act, fireEvent, render, screen, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { getCatchUp, getChannelMessages, getChannels, getDms, getMyInfo, getMyTeams, getTeamMembers, login, logout } from './api.js'
import App from './App.jsx'
import { disablePush } from './push.js'
import { FakeWebSocket } from './test/FakeWebSocket.js'
import { flush } from './test/fakeSocket.js'
import { page } from './test/page.js'

vi.mock('./api.js', () => ({
  getMyInfo: vi.fn(),
  login: vi.fn(),
  signup: vi.fn(),
  logout: vi.fn(),
  getMyTeams: vi.fn(),
  getChannels: vi.fn(),
  getDms: vi.fn(),
  getTeamMembers: vi.fn(),
  getPresence: vi.fn(async () => []),
  getChannelMessages: vi.fn(),
  getCatchUp: vi.fn(),
  markRead: vi.fn(),
  webSocketUrl: () => 'ws://localhost/websocket',
}))

vi.mock('./push.js', () => ({
  enablePush: vi.fn(async () => null),
  disablePush: vi.fn(async () => {}),
  listenServiceWorker: vi.fn(() => () => {}),
}))

const ME = { userId: 1, userName: '철수', account: 'chulsoo' }

describe('[단위] App', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.resetAllMocks()
    FakeWebSocket.instances = []
    vi.stubGlobal('WebSocket', FakeWebSocket)
    getMyTeams.mockResolvedValue([{ teamId: 1, teamName: '60hz' }])
    getChannels.mockResolvedValue([{ channelId: 10, channelName: 'general', joined: true, unreadCount: 0 }])
    getDms.mockResolvedValue([])
    getTeamMembers.mockResolvedValue([])
    getChannelMessages.mockResolvedValue(page([]))
    getCatchUp.mockResolvedValue({ unreadCount: 0, firstUnreadMessageId: null })
    logout.mockResolvedValue()
    disablePush.mockResolvedValue()
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
  })

  it('fail case : 로그인하지 않았으면 로그인 화면을 보여 주고 웹소켓을 열지 않는다', async () => {
    // arrange
    getMyInfo.mockResolvedValue(null)

    // act
    render(<App />)
    await flush()

    // assert
    expect(screen.getByRole('heading', { name: '로그인' })).toBeTruthy()
    expect(FakeWebSocket.instances).toHaveLength(0)
  })

  it('success case : 로그인하면 내 정보를 다시 불러와 채팅 화면을 열고 웹소켓을 자동으로 연다', async () => {
    // arrange
    getMyInfo.mockResolvedValueOnce(null).mockResolvedValueOnce(ME)
    login.mockResolvedValue()
    render(<App />)
    await flush()

    // act
    fireEvent.change(screen.getByLabelText('아이디'), { target: { value: 'chulsoo' } })
    fireEvent.change(screen.getByLabelText('비밀번호'), { target: { value: 'secret' } })
    fireEvent.click(screen.getByRole('button', { name: '로그인' }))
    await flush()

    // assert
    expect(login).toHaveBeenCalledWith('chulsoo', 'secret')
    expect(document.querySelector('.team-header').textContent).toContain('60hz')
    expect(FakeWebSocket.instances).toHaveLength(1)
  })

  it('fail case : 로그인이 실패하면 이유를 보여 준다', async () => {
    // arrange
    getMyInfo.mockResolvedValue(null)
    login.mockRejectedValue(new Error('로그인에 실패했습니다. 아이디 비밀번호를 확인해주세요.'))
    render(<App />)
    await flush()

    // act
    fireEvent.change(screen.getByLabelText('아이디'), { target: { value: 'chulsoo' } })
    fireEvent.change(screen.getByLabelText('비밀번호'), { target: { value: 'wrong' } })
    fireEvent.click(screen.getByRole('button', { name: '로그인' }))
    await flush()

    // assert
    expect(screen.getByRole('status').textContent).toBe('로그인에 실패했습니다. 아이디 비밀번호를 확인해주세요.')
  })

  it('success case : 로그아웃하면 세션을 끊고 로그인 화면으로 가며, 웹소켓은 닫힌 채 다시 붙지 않는다', async () => {
    // arrange
    getMyInfo.mockResolvedValue(ME)
    render(<App />)
    await flush()
    act(() => FakeWebSocket.last().connected())

    // act
    fireEvent.click(within(document.querySelector('.me-row')).getByText('철수'))
    fireEvent.click(screen.getByRole('menuitem', { name: '로그아웃' }))
    await flush()
    act(() => vi.advanceTimersByTime(60_000))

    // assert
    expect(logout).toHaveBeenCalledTimes(1)
    expect(disablePush.mock.invocationCallOrder[0]).toBeLessThan(logout.mock.invocationCallOrder[0])
    expect(screen.getByRole('heading', { name: '로그인' })).toBeTruthy()
    expect(FakeWebSocket.instances).toHaveLength(1)
    expect(FakeWebSocket.last().readyState).toBe(FakeWebSocket.CLOSED)
  })

  it('fail case : 웹 푸시 구독 해제가 실패해도 로그아웃한다', async () => {
    // arrange
    getMyInfo.mockResolvedValue(ME)
    disablePush.mockRejectedValue(new Error('network'))
    render(<App />)
    await flush()

    // act
    fireEvent.click(within(document.querySelector('.me-row')).getByText('철수'))
    fireEvent.click(screen.getByRole('menuitem', { name: '로그아웃' }))
    await flush()

    // assert
    expect(logout).toHaveBeenCalledTimes(1)
    expect(screen.getByRole('heading', { name: '로그인' })).toBeTruthy()
  })
})
