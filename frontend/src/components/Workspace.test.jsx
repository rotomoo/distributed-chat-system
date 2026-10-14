import { act, fireEvent, render, screen, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  createChannel, getCatchUp, getChannelMessages, getChannels, getDms, getMyTeams, getPresence, getTeamMembers, getTeams, setChannelMuted,
  joinChannel, joinTeam, markRead, openDm,
} from '../api.js'
import { FakeWebSocket } from '../test/FakeWebSocket.js'
import { flush } from '../test/fakeSocket.js'
import Workspace, { TEAM_STORAGE_KEY } from './Workspace.jsx'
import { page } from '../test/page.js'

vi.mock('../api.js', () => ({
  getMyTeams: vi.fn(),
  getChannels: vi.fn(),
  getDms: vi.fn(),
  getTeamMembers: vi.fn(),
  getPresence: vi.fn(),
  setChannelMuted: vi.fn(),
  getChannelMessages: vi.fn(),
  getCatchUp: vi.fn(),
  markRead: vi.fn(),
  createChannel: vi.fn(),
  joinChannel: vi.fn(),
  openDm: vi.fn(),
  getTeams: vi.fn(),
  joinTeam: vi.fn(),
  createTeam: vi.fn(),
  webSocketUrl: () => 'ws://localhost/websocket',
}))

const ME = { userId: 1, userName: '철수', account: 'chulsoo' }
const TEAMS = [{ teamId: 1, teamName: '60hz' }, { teamId: 2, teamName: 'Design Lab' }]
const CHANNELS = [
  { channelId: 10, channelName: 'general', joined: true, unreadCount: 0 },
  { channelId: 11, channelName: '점심메뉴', joined: true, unreadCount: 3 },
  { channelId: 12, channelName: '운동', joined: false, unreadCount: 0 },
]
const DMS = [{ channelId: 13, userId: 2, userName: '영희', unreadCount: 0 }]
const MEMBERS = [
  { userId: 1, userName: '철수' },
  { userId: 2, userName: '영희' },
  { userId: 3, userName: '민수' },
]
const PRESENCE = [
  { userId: 1, status: 'online' },
  { userId: 2, status: 'offline' },
  { userId: 3, status: 'online' },
]

const id = (n) => n.toString(16).padStart(24, '0')
const chat = (n, channelId, sendUserId = 2) => ({
  type: 'message', messageId: id(n), channelId, sendUserId, message: `msg ${n}`, publishDt: Date.now(),
})
const socket = () => FakeWebSocket.last()
const navItem = (label) => screen.getByText(label, { selector: '.nav-item .nav-label' }).closest('.nav-item')
const badgeOf = (label) => navItem(label).querySelector('.badge')?.textContent ?? null
const title = () => (document.querySelector('.channel-title .channel-name') ?? document.querySelector('.channel-title')).textContent

async function renderWorkspace(props = {}) {
  const onLogout = vi.fn()
  const utils = render(<Workspace me={ME} onLogout={onLogout} {...props} />)
  await flush()
  return { ...utils, onLogout }
}

async function connect() {
  act(() => socket().connected())
  await flush()
}

describe('[단위] Workspace', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.resetAllMocks()
    localStorage.clear()
    FakeWebSocket.instances = []
    vi.stubGlobal('WebSocket', FakeWebSocket)
    getMyTeams.mockResolvedValue(TEAMS)
    getChannels.mockResolvedValue(CHANNELS)
    getDms.mockResolvedValue(DMS)
    getTeamMembers.mockResolvedValue(MEMBERS)
    getPresence.mockResolvedValue(PRESENCE)
    getChannelMessages.mockResolvedValue(page([]))
    getCatchUp.mockResolvedValue({ unreadCount: 0, firstUnreadMessageId: null })
    markRead.mockResolvedValue(null)
    getTeams.mockResolvedValue({ list: TEAMS, paging: null })
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  // ── 팀 ──────────────────────────────────────────

  it('success case : 첫 번째 팀을 열고 레일 / 사이드바 맨 위에 팀을, 첫 참여 채널을 연다', async () => {
    // arrange & act
    await renderWorkspace()

    // assert
    expect(getChannels).toHaveBeenCalledWith(1)
    expect(getDms).toHaveBeenCalledWith(1)
    expect([...document.querySelectorAll('.rail-team')].map((b) => b.textContent)).toEqual(['60', 'DL'])
    expect(document.querySelector('.rail-team.active').textContent).toBe('60')
    expect(document.querySelector('.team-header').textContent).toContain('60hz')
    expect(title()).toBe('# general')
    expect(localStorage.getItem(TEAM_STORAGE_KEY)).toBe('1')
  })

  it('success case : 지난번에 고른 팀(localStorage)을 기억해서 연다', async () => {
    // arrange
    localStorage.setItem(TEAM_STORAGE_KEY, '2')

    // act
    await renderWorkspace()

    // assert
    expect(getChannels).toHaveBeenCalledWith(2)
    expect(document.querySelector('.rail-team.active').textContent).toBe('DL')
  })

  it('fail case : 기억한 팀이 내 팀 목록에 없으면 첫 번째 팀을 연다', async () => {
    // arrange
    localStorage.setItem(TEAM_STORAGE_KEY, '99')

    // act
    await renderWorkspace()

    // assert
    expect(getChannels).toHaveBeenCalledWith(1)
  })

  it('fail case : localStorage 를 쓸 수 없어도(사생활 보호 모드) 첫 번째 팀으로 동작한다', async () => {
    // arrange
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => { throw new Error('SecurityError') })
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new Error('SecurityError') })

    // act
    await renderWorkspace()

    // assert
    expect(getChannels).toHaveBeenCalledWith(1)
    expect(title()).toBe('# general')
  })

  it('success case : 레일에서 다른 팀을 누르면 그 팀 채널 / 멤버를 불러오고 기억한다', async () => {
    // arrange
    await renderWorkspace()

    // act
    fireEvent.click(screen.getByLabelText('Design Lab'))
    await flush()

    // assert
    expect(getChannels).toHaveBeenLastCalledWith(2)
    expect(getTeamMembers).toHaveBeenLastCalledWith(2)
    expect(localStorage.getItem(TEAM_STORAGE_KEY)).toBe('2')
    expect(document.querySelector('.team-header').textContent).toContain('Design Lab')
  })

  it('fail case : 참여한 팀이 없으면 팀 찾기 / 만들기를 안내한다', async () => {
    // arrange
    getMyTeams.mockResolvedValue([])

    // act
    await renderWorkspace()

    // assert
    expect(screen.getByText('아직 참여한 팀이 없어요')).toBeTruthy()
    fireEvent.click(screen.getByText('팀 찾기 / 만들기'))
    await flush()
    expect(screen.getByRole('dialog', { name: '팀' })).toBeTruthy()
  })

  it('success case : 레일 + 로 팀 창을 열어 가입하면 팀 목록을 다시 불러와 그 팀을 연다', async () => {
    // arrange
    getTeams.mockResolvedValue({ list: [{ teamId: 3, teamName: '새팀' }], paging: null })
    joinTeam.mockResolvedValue({ teamId: 3, teamName: '새팀' })
    await renderWorkspace()
    getMyTeams.mockResolvedValue([...TEAMS, { teamId: 3, teamName: '새팀' }])

    // act
    fireEvent.click(screen.getByLabelText('팀 추가'))
    await flush()
    fireEvent.click(screen.getByText('가입'))
    await flush()

    // assert
    expect(joinTeam).toHaveBeenCalledWith(3)
    expect(screen.queryByRole('dialog')).toBeNull()
    expect(getChannels).toHaveBeenLastCalledWith(3)
    expect(document.querySelector('.rail-team.active').textContent).toBe('새팀')
  })

  // ── 웹소켓 ──────────────────────────────────────

  it('success case : 로그인 화면에 들어오자마자 버튼 없이 웹소켓을 연다', async () => {
    // arrange & act
    await renderWorkspace()

    // assert
    expect(FakeWebSocket.instances).toHaveLength(1)
    expect(screen.queryByText('Connect')).toBeNull()
  })

  it('success case : 연결이 끊기면 "다시 연결하는 중" 띠를 보여 주고, 다시 연결되면 내린다', async () => {
    // arrange
    await renderWorkspace()
    await connect()
    const bannerWhileConnected = screen.queryByRole('status')

    // act
    act(() => socket().close())
    const bannerWhileDown = screen.getByRole('status').textContent
    act(() => vi.advanceTimersByTime(1000))
    await connect()

    // assert
    expect(bannerWhileConnected).toBeNull()
    expect(bannerWhileDown).toBe('연결이 끊겼어요. 다시 연결하는 중…')
    expect(screen.queryByRole('status')).toBeNull()
  })

  it('success case : 화면이 내려가면(로그아웃) 소켓을 닫고 다시 붙지 않는다', async () => {
    // arrange
    const { unmount } = await renderWorkspace()
    await connect()

    // act
    unmount()
    act(() => vi.advanceTimersByTime(60_000))

    // assert
    expect(FakeWebSocket.instances).toHaveLength(1)
    expect(socket().readyState).toBe(FakeWebSocket.CLOSED)
  })

  it('success case : 다시 연결되면 열린 채널의 끊긴 사이 메시지를 after 로 채운다', async () => {
    // arrange
    getChannelMessages.mockResolvedValueOnce(page([{ ...chat(1, 10), type: undefined }])).mockResolvedValue(page([]))
    await renderWorkspace()
    await connect()

    // act
    act(() => socket().close())
    act(() => vi.advanceTimersByTime(1000))
    await connect()

    // assert
    expect(getChannelMessages).toHaveBeenLastCalledWith(10, { after: id(1), size: 100 })
  })

  it('success case : 다시 연결되자마자 실시간 메시지가 먼저 와도 끊기기 전 가장 최근 메시지 이후로 채운다', async () => {
    // arrange
    getChannelMessages
      .mockResolvedValueOnce(page([1, 2].map((n) => ({ ...chat(n, 10), type: undefined }))))
      .mockResolvedValueOnce(page([]))
      .mockResolvedValue(page([3, 4, 5].map((n) => ({ ...chat(n, 10), type: undefined }))))
    await renderWorkspace()
    await connect()
    act(() => socket().close())
    act(() => vi.advanceTimersByTime(1000))

    // act
    act(() => {
      socket().connected()
      socket().receive(chat(6, 10))
    })
    await flush()

    // assert
    expect(getChannelMessages).toHaveBeenLastCalledWith(10, { after: id(2), size: 100 })
    expect([...document.querySelectorAll('.message-text')].map((n) => n.textContent)).toEqual(['msg 1', 'msg 2', 'msg 3', 'msg 4', 'msg 5', 'msg 6'])
  })

  it('success case : 다시 연결되면 채널 / DM 목록을 다시 불러와 끊긴 동안의 안 읽은 수를 보여 주고, 열린 채널은 0 으로 둔다', async () => {
    // arrange
    await renderWorkspace()
    await connect()
    const callsAfterFirstConnect = [getChannels.mock.calls.length, getDms.mock.calls.length]
    getChannels.mockResolvedValue(CHANNELS.map((c) => ({ ...c, unreadCount: c.channelId === 10 ? 4 : c.channelId === 11 ? 7 : 0 })))
    getDms.mockResolvedValue(DMS.map((d) => ({ ...d, unreadCount: 2 })))

    // act
    act(() => socket().close())
    act(() => vi.advanceTimersByTime(1000))
    await connect()

    // assert
    expect(callsAfterFirstConnect).toEqual([1, 1])
    expect(getChannels).toHaveBeenCalledTimes(2)
    expect(getChannels).toHaveBeenLastCalledWith(1)
    expect(badgeOf('점심메뉴')).toBe('7')
    expect(badgeOf('영희')).toBe('2')
    expect(badgeOf('general')).toBeNull()
  })

  it('success case : DM 을 열어 둔 채 다시 연결되면 그 DM 배지는 0, 다른 채널은 서버 값으로 보여 준다', async () => {
    // arrange
    await renderWorkspace()
    await connect()
    fireEvent.click(navItem('영희'))
    await flush()
    getChannels.mockResolvedValue(CHANNELS.map((c) => ({ ...c, unreadCount: c.channelId === 10 ? 4 : 0 })))
    getDms.mockResolvedValue(DMS.map((d) => ({ ...d, unreadCount: 2 })))

    // act
    act(() => socket().close())
    act(() => vi.advanceTimersByTime(1000))
    await connect()

    // assert
    expect(badgeOf('영희')).toBeNull()
    expect(badgeOf('general')).toBe('4')
  })

  it('fail case : 첫 연결에서는 목록을 다시 불러오지 않는다', async () => {
    // arrange
    await renderWorkspace()

    // act
    await connect()

    // assert
    expect(getChannels).toHaveBeenCalledTimes(1)
    expect(getDms).toHaveBeenCalledTimes(1)
  })

  it('success case : 읽음 처리 전이라도 열린 DM 은 목록을 다시 불러와도 배지를 0 으로 둔다', async () => {
    // arrange
    await renderWorkspace()
    await connect()
    fireEvent.click(navItem('영희'))
    await flush()
    getDms.mockResolvedValue([{ ...DMS[0], unreadCount: 2 }, { channelId: 20, userId: 3, userName: '민수', unreadCount: 1 }])

    // act
    act(() => socket().receive(chat(1, 20, 3)))
    await flush()

    // assert
    expect(badgeOf('민수')).toBe('1')
    expect(badgeOf('영희')).toBeNull()
  })

  it('success case : 60초 동안 조용하면 끊김 띠를 보여 주고 다시 붙어서 끊기기 전 최신 메시지 이후를 채운다', async () => {
    // arrange
    getChannelMessages
      .mockResolvedValueOnce(page([1, 2].map((n) => ({ ...chat(n, 10), type: undefined }))))
      .mockResolvedValueOnce(page([]))
      .mockResolvedValue(page([3].map((n) => ({ ...chat(n, 10), type: undefined }))))
    await renderWorkspace()
    await connect()

    // act
    act(() => vi.advanceTimersByTime(60_000))
    const banner = screen.getByRole('status').textContent
    act(() => vi.advanceTimersByTime(1000))
    await connect()

    // assert
    expect(banner).toBe('연결이 끊겼어요. 다시 연결하는 중…')
    expect(FakeWebSocket.instances).toHaveLength(2)
    expect(getChannelMessages).toHaveBeenLastCalledWith(10, { after: id(2), size: 100 })
    expect(screen.getByText('msg 3')).toBeTruthy()
  })

  it('fail case : ping 은 화면에 그리지 않고 안 읽은 배지에도 영향이 없다', async () => {
    // arrange
    await renderWorkspace()
    await connect()
    const textsBefore = document.querySelector('.message-list').textContent

    // act
    act(() => socket().ping())
    await flush()

    // assert
    expect(document.querySelector('.message-list').textContent).toBe(textsBefore)
    expect(badgeOf('점심메뉴')).toBe('3')
    expect(badgeOf('general')).toBeNull()
    expect(socket().sent.at(-1)).toEqual({ type: 'pong' })
  })

  // ── 안 읽은 배지 ─────────────────────────────────

  it('success case : 열려 있지 않은 채널 / DM 에 남이 보낸 메시지가 오면 배지를 올린다', async () => {
    // arrange
    await renderWorkspace()
    await connect()

    // act
    act(() => socket().receive(chat(1, 11)))
    act(() => socket().receive(chat(2, 11)))
    act(() => socket().receive(chat(3, 13)))

    // assert
    expect(badgeOf('점심메뉴')).toBe('5')
    expect(badgeOf('영희')).toBe('1')
  })

  it('fail case : 다른 채널의 리액션 이벤트는 안 읽은 배지를 올리지 않는다', async () => {
    // arrange
    await renderWorkspace()
    await connect()

    // act
    act(() => socket().receive({ type: 'reaction', channelId: 11, messageId: id(1), reactions: [{ emoji: '👍', userIds: [2] }] }))
    act(() => socket().receive({ type: 'reaction', channelId: 13, messageId: id(2), reactions: [{ emoji: '👍', userIds: [2] }] }))
    await flush()

    // assert
    expect(badgeOf('점심메뉴')).toBe('3')
    expect(badgeOf('영희')).toBeNull()
    expect(getDms).toHaveBeenCalledTimes(1)
  })

  it('fail case : 열린 채널 메시지와 내가 보낸 메시지는 배지를 올리지 않는다', async () => {
    // arrange
    await renderWorkspace()
    await connect()

    // act
    act(() => socket().receive(chat(1, 10)))
    act(() => socket().receive(chat(2, 13, ME.userId)))

    // assert
    expect(badgeOf('general')).toBeNull()
    expect(badgeOf('영희')).toBeNull()
    expect(screen.getByText('msg 1')).toBeTruthy()
  })

  it('success case : 채널을 열면 그 채널 배지를 지운다', async () => {
    // arrange
    await renderWorkspace()
    expect(badgeOf('점심메뉴')).toBe('3')

    // act
    fireEvent.click(navItem('점심메뉴'))
    await flush()

    // assert
    expect(badgeOf('점심메뉴')).toBeNull()
    expect(title()).toBe('# 점심메뉴')
  })

  it('success case : 다른 채널을 열면 그 채널 메시지만 보여 준다', async () => {
    // arrange
    getChannelMessages.mockImplementation(async (channelId) => page([{ ...chat(channelId, channelId), type: undefined }]))
    await renderWorkspace()
    const before = [...document.querySelectorAll('.message-text')].map((n) => n.textContent)

    // act
    fireEvent.click(navItem('점심메뉴'))
    await flush()

    // assert
    expect(before).toEqual(['msg 10'])
    expect([...document.querySelectorAll('.message-text')].map((n) => n.textContent)).toEqual(['msg 11'])
  })

  it('success case : 목록에 없는 채널 메시지가 오면(상대가 새 DM 을 염) DM 목록을 한 번만 다시 불러온다', async () => {
    // arrange
    await renderWorkspace()
    await connect()
    getDms.mockResolvedValue([...DMS, { channelId: 20, userId: 3, userName: '민수', unreadCount: 1 }])

    // act
    act(() => socket().receive(chat(1, 20, 3)))
    await flush()
    act(() => socket().receive(chat(2, 99, 3)))
    await flush()
    act(() => socket().receive(chat(3, 99, 3)))
    await flush()

    // assert
    expect(getDms).toHaveBeenCalledTimes(3)
    expect(badgeOf('민수')).toBe('1')
  })

  // ── 접속 여부 / 프로필 / DM ─────────────────────────

  it('success case : 접속 상태를 15초마다 다시 불러와 DM 점에 반영한다 (멤버 목록은 다시 부르지 않는다)', async () => {
    // arrange
    await renderWorkspace()
    const dotBefore = navItem('영희').querySelector('.presence-dot').className
    getPresence.mockResolvedValue(PRESENCE.map((p) => (p.userId === 2 ? { ...p, status: 'away' } : p)))

    // act
    await act(async () => {
      await vi.advanceTimersByTimeAsync(14_999)
    })
    const callsBefore15s = getPresence.mock.calls.length
    await act(async () => {
      await vi.advanceTimersByTimeAsync(1)
    })

    // assert
    expect(getPresence).toHaveBeenCalledWith(1)
    expect(dotBefore).toContain('offline')
    expect(callsBefore15s).toBe(1)
    expect(getPresence).toHaveBeenCalledTimes(2)
    expect(navItem('영희').querySelector('.presence-dot').className).toContain('away')
    expect(getTeamMembers).toHaveBeenCalledTimes(1)
  })

  it('success case : 다시 연결되면 접속 상태를 바로 다시 불러온다', async () => {
    // arrange
    await renderWorkspace()
    await connect()
    const callsAfterFirstConnect = getPresence.mock.calls.length

    // act
    act(() => socket().close())
    act(() => vi.advanceTimersByTime(1000))
    await connect()

    // assert
    expect(getPresence.mock.calls.length).toBe(callsAfterFirstConnect + 1)
  })

  it('fail case : 접속 상태 조회가 실패하면 모두 오프라인으로 보이고 화면은 그대로 쓴다', async () => {
    // arrange
    getPresence.mockRejectedValue(new Error('404'))

    // act
    await renderWorkspace()

    // assert
    expect(navItem('영희').querySelector('.presence-dot').className).toContain('offline')
    expect(title()).toBe('# general')
  })

  it('success case : 프로필과 DM 머리글에 온라인 / 자리 비움 / 오프라인을 보여 준다', async () => {
    // arrange
    getPresence.mockResolvedValue([{ userId: 2, status: 'away' }, { userId: 3, status: 'online' }])
    getChannelMessages.mockResolvedValue(page([{ ...chat(1, 10, 3), type: undefined }, { ...chat(2, 10, 2), type: undefined }]))
    await renderWorkspace()

    // act
    fireEvent.click(screen.getByLabelText('민수 프로필'))
    const minsu = screen.getByRole('complementary', { name: '프로필' }).querySelector('.profile-presence').textContent
    fireEvent.click(screen.getByLabelText('영희 프로필'))
    const younghee = screen.getByRole('complementary', { name: '프로필' }).querySelector('.profile-presence').textContent
    fireEvent.click(navItem('영희'))
    await flush()

    // assert
    expect(minsu).toBe('● 온라인')
    expect(younghee).toBe('◐ 자리 비움')
    expect(document.querySelector('.channel-header .presence-dot').className).toContain('away')
  })

  it('success case : 메시지의 아바타를 누르면 프로필을 열고, 메시지 보내기로 DM 을 만들어 연다', async () => {
    // arrange
    getChannelMessages.mockResolvedValue(page([{ ...chat(1, 10, 3), type: undefined }]))
    openDm.mockResolvedValue({ channelId: 20, userId: 3, userName: '민수' })
    await renderWorkspace()
    getDms.mockResolvedValue([...DMS, { channelId: 20, userId: 3, userName: '민수', unreadCount: 0 }])

    // act
    fireEvent.click(screen.getByLabelText('민수 프로필'))
    const panel = screen.getByRole('complementary', { name: '프로필' })
    const presence = within(panel).getByText('● 온라인').textContent
    fireEvent.click(within(panel).getByText('메시지 보내기'))
    await flush()

    // assert
    expect(presence).toBe('● 온라인')
    expect(openDm).toHaveBeenCalledWith(1, 3)
    expect(title()).toBe('민수')
    expect(navItem('민수').classList.contains('active')).toBe(true)
  })

  it('fail case : 프로필에서 DM 열기가 실패하면 프로필에 이유를 보여 준다', async () => {
    // arrange
    getChannelMessages.mockResolvedValue(page([{ ...chat(1, 10, 3), type: undefined }]))
    openDm.mockRejectedValue(new Error('팀 멤버가 아닙니다.'))
    await renderWorkspace()

    // act
    fireEvent.click(screen.getByLabelText('민수 프로필'))
    fireEvent.click(screen.getByText('메시지 보내기'))
    await flush()

    // assert
    expect(within(screen.getByRole('complementary', { name: '프로필' })).getByRole('alert').textContent).toBe('팀 멤버가 아닙니다.')
  })

  it('success case : DM + 로 멤버를 골라 DM 을 열고 창을 닫는다', async () => {
    // arrange
    openDm.mockResolvedValue({ channelId: 13, userId: 2, userName: '영희' })
    await renderWorkspace()

    // act
    fireEvent.click(screen.getByLabelText('새 메시지'))
    fireEvent.click(within(screen.getByRole('dialog', { name: '새 메시지' })).getByText('영희'))
    await flush()

    // assert
    expect(openDm).toHaveBeenCalledWith(1, 2)
    expect(screen.queryByRole('dialog')).toBeNull()
    expect(title()).toBe('영희')
  })

  it('success case : 내 프로필에는 메시지 보내기가 없다', async () => {
    // arrange
    await renderWorkspace()
    await connect()

    // act
    fireEvent.click(within(document.querySelector('.me-row')).getByText('철수'))
    fireEvent.click(screen.getByRole('menuitem', { name: '프로필 보기' }))

    // assert
    const panel = screen.getByRole('complementary', { name: '프로필' })
    expect(within(panel).getByText('@chulsoo')).toBeTruthy()
    expect(within(panel).queryByText('메시지 보내기')).toBeNull()
  })

  it('success case : 메시지 보낸 사람 이름은 팀 멤버 이름으로, 모르는 사람은 User {id} 로 보여 준다', async () => {
    // arrange
    getChannelMessages.mockResolvedValue(page([
      { ...chat(1, 10, 3), type: undefined, publishDt: 0 },
      { ...chat(2, 10, 42), type: undefined, publishDt: 0 },
    ]))

    // act
    await renderWorkspace()

    // assert
    expect([...document.querySelectorAll('.sender-name')].map((n) => n.textContent)).toEqual(['민수', 'User 42'])
  })

  // ── 채널 만들기 / 참여 / 로그아웃 ─────────────────────

  it('success case : 채널을 만들면 목록을 다시 불러오고 새 채널을 연다', async () => {
    // arrange
    createChannel.mockResolvedValue({ channelId: 30, channelName: '회식', joined: true })
    await renderWorkspace()
    getChannels.mockResolvedValue([...CHANNELS, { channelId: 30, channelName: '회식', joined: true, unreadCount: 0 }])

    // act
    fireEvent.click(screen.getByLabelText('채널 만들기'))
    const input = screen.getByLabelText('새 채널 이름')
    fireEvent.change(input, { target: { value: '회식' } })
    fireEvent.submit(input.closest('form'))
    await flush()

    // assert
    expect(createChannel).toHaveBeenCalledWith(1, '회식')
    expect(title()).toBe('# 회식')
  })

  it('success case : 채널 둘러보기에서 참여하면 그 채널을 연다', async () => {
    // arrange
    joinChannel.mockResolvedValue({ channelId: 12, channelName: '운동', joined: true })
    await renderWorkspace()
    getChannels.mockResolvedValue(CHANNELS.map((c) => ({ ...c, joined: true })))

    // act
    fireEvent.click(screen.getByText(/채널 둘러보기/))
    fireEvent.click(screen.getByText('참여'))
    await flush()

    // assert
    expect(joinChannel).toHaveBeenCalledWith(12)
    expect(title()).toBe('# 운동')
  })

  it('success case : 내 메뉴의 로그아웃을 누르면 로그아웃한다', async () => {
    // arrange
    const { onLogout } = await renderWorkspace()

    // act
    fireEvent.click(within(document.querySelector('.me-row')).getByText('철수'))
    fireEvent.click(screen.getByRole('menuitem', { name: '로그아웃' }))

    // assert
    expect(onLogout).toHaveBeenCalledTimes(1)
  })
})
