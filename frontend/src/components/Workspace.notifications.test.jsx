import { act, fireEvent, render, screen, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  getCatchUp, getChannelMessages, getChannels, getDms, getMyTeams, getPresence, getTeamMembers, markRead, setChannelMuted,
} from '../api.js'
import { NOTIFY_BAR_DISMISSED_KEY } from '../hooks/useMessageNotifications.js'
import { enablePush, listenServiceWorker } from '../push.js'
import { FakeWebSocket } from '../test/FakeWebSocket.js'
import { flush } from '../test/fakeSocket.js'
import { page } from '../test/page.js'
import Workspace from './Workspace.jsx'

vi.mock('../api.js', () => ({
  getMyTeams: vi.fn(),
  getChannels: vi.fn(),
  getDms: vi.fn(),
  getTeamMembers: vi.fn(),
  getPresence: vi.fn(),
  getChannelMessages: vi.fn(),
  getCatchUp: vi.fn(),
  markRead: vi.fn(),
  setChannelMuted: vi.fn(),
  webSocketUrl: () => 'ws://localhost/websocket',
}))

vi.mock('../push.js', () => ({
  enablePush: vi.fn(async () => null),
  disablePush: vi.fn(async () => {}),
  listenServiceWorker: vi.fn(() => () => {}),
}))

const ME = { userId: 1, userName: '철수', account: 'chulsoo' }
const TEAMS = [{ teamId: 1, teamName: '60hz' }, { teamId: 2, teamName: 'Design Lab' }]
const CHANNELS = [
  { channelId: 10, channelName: 'general', joined: true, unreadCount: 0, muted: false },
  { channelId: 11, channelName: '점심메뉴', joined: true, unreadCount: 3, muted: false },
  { channelId: 12, channelName: '공지', joined: true, unreadCount: 0, muted: true },
]
const DMS = [{ channelId: 13, userId: 2, userName: '영희', unreadCount: 0, muted: false }]
const MEMBERS = [{ userId: 1, userName: '철수' }, { userId: 2, userName: '영희' }, { userId: 3, userName: '민수' }]

class FakeNotification {
  static permission = 'granted'
  static answer = 'granted'
  static instances = []
  static requestPermission = vi.fn(async () => {
    FakeNotification.permission = FakeNotification.answer
    return FakeNotification.answer
  })

  constructor(title, options) {
    this.title = title
    this.options = options
    this.close = vi.fn()
    FakeNotification.instances.push(this)
  }
}

const id = (n) => n.toString(16).padStart(24, '0')
const chat = (n, channelId, sendUserId = 2, extra = {}) => ({
  type: 'message', messageId: id(n), channelId, sendUserId, message: `msg ${n}`, publishDt: Date.now(), ...extra,
})
const socket = () => FakeWebSocket.last()
const notifications = () => FakeNotification.instances.map((n) => [n.title, n.options.body, n.options.tag])
const navItem = (label) => screen.getByText(label, { selector: '.nav-item .nav-label' }).closest('.nav-item')
const title = () => (document.querySelector('.channel-title .channel-name') ?? document.querySelector('.channel-title')).textContent
let visibility = 'visible'

async function renderWorkspace() {
  render(<Workspace me={ME} onLogout={vi.fn()} />)
  await flush()
  act(() => socket().connected())
  await flush()
}

describe('[단위] Workspace 알림 / 탭 제목 / 바로가기 / 알림 끄기', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.resetAllMocks()
    localStorage.clear()
    window.history.replaceState(null, '', '/')
    FakeWebSocket.instances = []
    vi.stubGlobal('WebSocket', FakeWebSocket)
    FakeNotification.permission = 'granted'
    FakeNotification.answer = 'granted'
    FakeNotification.instances = []
    vi.stubGlobal('Notification', FakeNotification)
    visibility = 'visible'
    Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => visibility })
    getMyTeams.mockResolvedValue(TEAMS)
    getChannels.mockImplementation(async (teamId) => (teamId === 1 ? CHANNELS : [{ channelId: 50, channelName: '디자인', joined: true, unreadCount: 0, muted: false }]))
    getDms.mockImplementation(async (teamId) => (teamId === 1 ? DMS : []))
    getTeamMembers.mockResolvedValue(MEMBERS)
    getPresence.mockResolvedValue([])
    getChannelMessages.mockResolvedValue(page([]))
    getCatchUp.mockResolvedValue({ unreadCount: 0, firstUnreadMessageId: null })
    markRead.mockResolvedValue(null)
    enablePush.mockResolvedValue(null)
    listenServiceWorker.mockReturnValue(() => {})
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  // ── 탭 안 알림 ──────────────────────────────────

  it('success case : 열려 있지 않은 채널에 남이 보낸 메시지는 "#채널명" / "보낸사람: 미리보기" 알림을 띄운다', async () => {
    // arrange
    await renderWorkspace()

    // act
    act(() => socket().receive(chat(1, 11)))

    // assert
    expect(notifications()).toEqual([['#점심메뉴', '영희: msg 1', 'channel-11']])
  })

  it('success case : DM 은 상대 이름이 제목이고, 파일만 있으면 "파일 N개"', async () => {
    // arrange
    await renderWorkspace()

    // act
    act(() => socket().receive(chat(1, 13, 2, { message: '', attachments: [{ fileId: 'a' }, { fileId: 'b' }] })))

    // assert
    expect(notifications()).toEqual([['영희', '영희: 파일 2개', 'channel-13']])
  })

  it('fail case : 열려 있는 채널 메시지는 탭이 보이는 동안 알리지 않는다', async () => {
    // arrange
    await renderWorkspace()

    // act
    act(() => socket().receive(chat(1, 10)))

    // assert
    expect(notifications()).toEqual([])
  })

  it('success case : 탭이 숨겨져 있으면 열려 있는 채널 메시지도 알린다', async () => {
    // arrange
    await renderWorkspace()
    visibility = 'hidden'

    // act
    act(() => socket().receive(chat(1, 10)))

    // assert
    expect(notifications()).toEqual([['#general', '영희: msg 1', 'channel-10']])
  })

  it('fail case : 내가 보낸 메시지와 알림을 끈 채널은 알리지 않는다 (배지는 센다)', async () => {
    // arrange
    await renderWorkspace()

    // act
    act(() => socket().receive(chat(1, 11, ME.userId)))
    act(() => socket().receive(chat(2, 12)))

    // assert
    expect(notifications()).toEqual([])
    expect(navItem('공지').querySelector('.badge').textContent).toBe('1')
  })

  it('fail case : 알림을 허락하지 않았으면 띄우지 않는다', async () => {
    // arrange
    FakeNotification.permission = 'denied'
    await renderWorkspace()

    // act
    act(() => socket().receive(chat(1, 11)))

    // assert
    expect(notifications()).toEqual([])
  })

  it('success case : 알림을 누르면 탭을 앞으로 가져오고 그 채널을 연다', async () => {
    // arrange
    const focus = vi.spyOn(window, 'focus').mockImplementation(() => {})
    await renderWorkspace()
    act(() => socket().receive(chat(1, 11)))

    // act
    act(() => FakeNotification.instances[0].onclick())
    await flush()

    // assert
    expect(focus).toHaveBeenCalled()
    expect(title()).toBe('# 점심메뉴')
    expect(FakeNotification.instances[0].close).toHaveBeenCalled()
  })

  // ── 알림 켜기 안내 ───────────────────────────────

  it('success case : 아직 묻지 않았으면 안내 띠를 보여 주고, 알림 켜기로 허락받으면 웹 푸시를 구독하고 띠를 내린다', async () => {
    // arrange
    FakeNotification.permission = 'default'
    await renderWorkspace()
    const bar = screen.getByRole('region', { name: '알림 켜기 안내' })

    // act
    await act(async () => fireEvent.click(within(bar).getByText('알림 켜기')))
    await flush()

    // assert
    expect(bar.textContent).toContain('알림을 켜면 다른 채널의 새 메시지를 알려 드려요')
    expect(FakeNotification.requestPermission).toHaveBeenCalled()
    expect(enablePush).toHaveBeenCalledTimes(1)
    expect(screen.queryByRole('region', { name: '알림 켜기 안내' })).toBeNull()
  })

  it('fail case : 거절하면 띠는 내리고 웹 푸시는 구독하지 않는다', async () => {
    // arrange
    FakeNotification.permission = 'default'
    FakeNotification.answer = 'denied'
    await renderWorkspace()

    // act
    await act(async () => fireEvent.click(screen.getByText('알림 켜기')))
    await flush()

    // assert
    expect(enablePush).not.toHaveBeenCalled()
    expect(screen.queryByRole('region', { name: '알림 켜기 안내' })).toBeNull()
  })

  it('success case : ✕ 로 닫으면 기억해서 다음에도 띄우지 않는다', async () => {
    // arrange
    FakeNotification.permission = 'default'
    await renderWorkspace()

    // act
    fireEvent.click(screen.getByLabelText('알림 안내 닫기'))

    // assert
    expect(screen.queryByRole('region', { name: '알림 켜기 안내' })).toBeNull()
    expect(localStorage.getItem(NOTIFY_BAR_DISMISSED_KEY)).toBe('1')
  })

  it('fail case : 이미 닫았거나 이미 허락했거나 알림을 지원하지 않으면 띠가 없다', async () => {
    // arrange
    FakeNotification.permission = 'default'
    localStorage.setItem(NOTIFY_BAR_DISMISSED_KEY, '1')

    // act
    await renderWorkspace()

    // assert
    expect(screen.queryByRole('region', { name: '알림 켜기 안내' })).toBeNull()
  })

  it('success case : 이미 허락한 상태로 들어오면 웹 푸시 구독을 서버에 다시 알린다', async () => {
    // arrange & act
    await renderWorkspace()

    // assert
    expect(enablePush).toHaveBeenCalledTimes(1)
    expect(screen.queryByRole('region', { name: '알림 켜기 안내' })).toBeNull()
  })

  it('fail case : 브라우저가 알림을 지원하지 않으면 띠도 알림도 없다', async () => {
    // arrange
    vi.stubGlobal('Notification', undefined)
    await renderWorkspace()

    // act
    act(() => socket().receive(chat(1, 11)))

    // assert
    expect(screen.queryByRole('region', { name: '알림 켜기 안내' })).toBeNull()
    expect(enablePush).not.toHaveBeenCalled()
  })

  // ── 탭 제목 ────────────────────────────────────

  it('success case : 탭 제목에 안 읽은 메시지 합계를 "(N) 팀이름" 으로 보여 준다 (알림 끈 채널도 센다)', async () => {
    // arrange
    await renderWorkspace()
    const initial = document.title

    // act
    act(() => socket().receive(chat(1, 12)))
    const withMuted = document.title
    fireEvent.click(navItem('점심메뉴'))
    await flush()
    fireEvent.click(navItem('공지'))
    await flush()

    // assert
    expect(initial).toBe('(3) 60hz')
    expect(withMuted).toBe('(4) 60hz')
    expect(document.title).toBe('60hz')
  })

  // ── 바로가기 (?channel= / 서비스 워커) ──────────────

  it('success case : 주소에 ?channel= 이 있으면 그 채널을 열고 주소창에서는 지운다', async () => {
    // arrange
    window.history.replaceState(null, '', '/?channel=11')

    // act
    await renderWorkspace()

    // assert
    expect(title()).toBe('# 점심메뉴')
    expect(window.location.search).toBe('')
  })

  it('success case : 다른 팀의 채널이면 그 팀으로 옮겨서 연다', async () => {
    // arrange
    window.history.replaceState(null, '', '/?channel=50')

    // act
    await renderWorkspace()
    await flush()

    // assert
    expect(document.querySelector('.rail-team.active').textContent).toBe('DL')
    expect(title()).toBe('# 디자인')
  })

  it('fail case : 어느 팀에도 없는 채널이면 평소처럼 첫 채널을 연다', async () => {
    // arrange
    window.history.replaceState(null, '', '/?channel=999')

    // act
    await renderWorkspace()
    await flush()

    // assert
    expect(document.querySelector('.rail-team.active').textContent).toBe('60')
    expect(title()).toBe('# general')
  })

  it('fail case : 못 찾은 바로가기는 버려서, 나중에 팀을 바꿔도 다시 찾지 않는다', async () => {
    // arrange
    window.history.replaceState(null, '', '/?channel=999')
    await renderWorkspace()
    await flush()

    // act
    fireEvent.click(screen.getByLabelText('Design Lab'))
    await flush()

    // assert
    expect(getChannels.mock.calls.map(([teamId]) => teamId)).toEqual([1, 2, 2])
    expect(title()).toBe('# 디자인')
  })

  it('success case : 서비스 워커가 알림 클릭으로 보낸 주소의 채널을 연다', async () => {
    // arrange
    await renderWorkspace()
    const onUrl = listenServiceWorker.mock.calls[0][0]

    // act
    act(() => onUrl('/?channel=13'))
    await flush()

    // assert
    expect(title()).toBe('영희')
  })

  // ── 알림 끄기 ──────────────────────────────────

  it('success case : 채널 메뉴의 알림 끄기 / 켜기로 서버에 저장하고 🔕 를 보여 준다', async () => {
    // arrange
    setChannelMuted.mockResolvedValue(null)
    await renderWorkspace()

    // act
    fireEvent.click(screen.getByLabelText('채널 메뉴'))
    await act(async () => fireEvent.click(screen.getByRole('menuitem', { name: '알림 끄기' })))
    await flush()
    const mutedHeader = document.querySelector('.channel-header .muted-mark') !== null
    const mutedSidebar = navItem('general').querySelector('.muted-icon') !== null
    fireEvent.click(screen.getByLabelText('채널 메뉴'))
    const nextLabel = screen.getByRole('menuitem').textContent
    await act(async () => fireEvent.click(screen.getByRole('menuitem', { name: '알림 켜기' })))
    await flush()

    // assert
    expect(setChannelMuted.mock.calls).toEqual([[10, true], [10, false]])
    expect(mutedHeader).toBe(true)
    expect(mutedSidebar).toBe(true)
    expect(nextLabel).toBe('알림 켜기')
    expect(document.querySelector('.channel-header .muted-mark')).toBeNull()
  })

  it('success case : 알림을 끈 뒤에는 그 채널 메시지를 알리지 않는다', async () => {
    // arrange
    setChannelMuted.mockResolvedValue(null)
    await renderWorkspace()
    fireEvent.click(navItem('점심메뉴'))
    await flush()
    fireEvent.click(screen.getByLabelText('채널 메뉴'))
    await act(async () => fireEvent.click(screen.getByRole('menuitem', { name: '알림 끄기' })))
    await flush()
    fireEvent.click(navItem('general'))
    await flush()

    // act
    act(() => socket().receive(chat(1, 11)))

    // assert
    expect(notifications()).toEqual([])
  })

  it('fail case : 알림 설정 저장이 실패하면 머리글에 이유를 보여 주고 바꾸지 않는다', async () => {
    // arrange
    setChannelMuted.mockRejectedValue(new Error('알림 설정을 바꾸지 못했어요'))
    await renderWorkspace()

    // act
    fireEvent.click(screen.getByLabelText('채널 메뉴'))
    await act(async () => fireEvent.click(screen.getByRole('menuitem', { name: '알림 끄기' })))
    await flush()

    // assert
    expect(within(document.querySelector('.channel-header')).getByRole('alert').textContent).toBe('알림 설정을 바꾸지 못했어요')
    expect(document.querySelector('.channel-header .muted-mark')).toBeNull()
  })
})
