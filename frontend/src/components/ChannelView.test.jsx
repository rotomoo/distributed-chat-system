import { act, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { getCatchUp, getChannelMessages, markRead } from '../api.js'
import { fakeSocket, flush } from '../test/fakeSocket.js'
import ChannelView, { firstPageSize } from './ChannelView.jsx'
import { page } from '../test/page.js'

vi.mock('../api.js', () => ({
  getChannelMessages: vi.fn(),
  getCatchUp: vi.fn(),
  markRead: vi.fn(),
  uploadFile: vi.fn(),
  UPLOAD_TOO_LARGE: '20MB 이하만 올릴 수 있어요',
  fileUrl: (fileId) => `/v1/private/api/file/${fileId}`,
}))

const LUNCH = { channelId: 11, kind: 'channel', name: '점심메뉴' }
const GENERAL = { channelId: 10, kind: 'channel', name: 'general' }
const DM = { channelId: 13, kind: 'dm', name: '영희', userId: 2 }
const NAMES = { 1: '철수', 2: '영희', 3: '민수' }
const nameOf = (userId) => NAMES[userId] ?? `User ${userId}`

// messageId 는 ObjectId 처럼 24자리 hex, 숫자가 클수록 나중 메시지. 메시지끼리 10분 간격 (묶이지 않게)
const BASE_TIME = new Date(2026, 9, 13, 9, 0).getTime()
const id = (n) => n.toString(16).padStart(24, '0')
const msg = (n, { channelId = LUNCH.channelId, sendUserId = 2, minutes = n * 10 } = {}) => ({
  messageId: id(n), channelId, sendUserId, message: `msg ${n}`, publishDt: BASE_TIME + minutes * 60_000,
})
const range = (from, to, options) => Array.from({ length: to - from + 1 }, (_, i) => msg(from + i, options))
const shownTexts = () => [...document.querySelectorAll('.message-text')].map((n) => n.textContent)
const textarea = () => screen.getByRole('textbox')
const NO_CATCH_UP = { unreadCount: 0, firstUnreadMessageId: null }
const scrollBox = () => document.querySelector('.message-scroll')

// jsdom 은 레이아웃을 계산하지 않으므로 스크롤 영역 높이와 구분선 위치를 정해 준다 (scrollTop 은 jsdom 이 저장한다)
function fakeLayout({ scrollHeight, clientHeight, dividerTop }) {
  vi.spyOn(Element.prototype, 'scrollHeight', 'get').mockImplementation(function () {
    return this.classList.contains('message-scroll') ? scrollHeight : 0
  })
  vi.spyOn(Element.prototype, 'clientHeight', 'get').mockImplementation(function () {
    return this.classList.contains('message-scroll') ? clientHeight : 0
  })
  vi.spyOn(HTMLElement.prototype, 'offsetTop', 'get').mockImplementation(function () {
    return this.classList.contains('unread-divider') ? dividerTop : 0
  })
}

function view({ channel = LUNCH, socket, unreadAtOpen = 0, onOpenProfile = vi.fn() }) {
  return (
    <ChannelView
      key={channel.channelId}
      channel={channel}
      unreadAtOpen={unreadAtOpen}
      socket={socket}
      nameOf={nameOf}
      presenceOf={(userId) => userId === 2}
      onOpenProfile={onOpenProfile}
      onToggleSidebar={() => {}}
    />
  )
}

// 끊겼다가(reconnecting) 다시 connected 가 되는 순서 그대로
async function reconnect(rerender, socket, connectionId = 2) {
  rerender(view({ socket: { ...socket, status: 'reconnecting' } }))
  await flush()
  rerender(view({ socket: { ...socket, status: 'connected', connectionId } }))
}

async function open(props = {}) {
  const socket = props.socket ?? fakeSocket()
  const utils = render(view({ ...props, socket }))
  await flush()
  return { ...utils, socket }
}

describe('[단위] ChannelView', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.resetAllMocks()
    getCatchUp.mockResolvedValue(NO_CATCH_UP)
    markRead.mockResolvedValue(null)
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.restoreAllMocks()
  })

  // ── 이전 메시지 ────────────────────────────────────

  it('success case : 열면 최신 20개를 조회해서 오래된 순으로 그린다', async () => {
    // arrange
    getChannelMessages.mockResolvedValue(page(range(6, 25), true))

    // act
    await open()

    // assert
    expect(getChannelMessages).toHaveBeenCalledWith(11, { size: 20 })
    expect(shownTexts()).toHaveLength(20)
    expect(shownTexts()[0]).toBe('msg 6')
    expect(shownTexts().at(-1)).toBe('msg 25')
    expect(screen.queryByText('#점심메뉴 채널의 시작이에요.')).toBeNull()
  })

  it('success case : hasNext 가 false 면 더 볼 이전 메시지가 없으므로 채널 시작 안내를 보여 준다', async () => {
    // arrange
    getChannelMessages.mockResolvedValue(page(range(1, 3)))

    // act
    await open()

    // assert
    expect(shownTexts()).toHaveLength(3)
    expect(screen.getByText('#점심메뉴 채널의 시작이에요.')).toBeTruthy()
  })

  it('success case : 맨 위 근처로 스크롤하면 맨 위 메시지 이전 20개를 조회해서 위에 붙인다', async () => {
    // arrange
    getChannelMessages.mockResolvedValueOnce(page(range(6, 25), true)).mockResolvedValueOnce(page(range(1, 5)))
    await open()
    const box = document.querySelector('.message-scroll')

    // act
    box.scrollTop = 0
    fireEvent.scroll(box)
    await flush()

    // assert
    expect(getChannelMessages).toHaveBeenLastCalledWith(11, { before: id(6), size: 20 })
    expect(shownTexts()).toHaveLength(25)
    expect(shownTexts()[0]).toBe('msg 1')
    expect(screen.getByText('#점심메뉴 채널의 시작이에요.')).toBeTruthy()
  })

  it('fail case : 첫 페이지가 정확히 20개여도 hasNext 가 false 면 채널 시작 안내를 보여 주고 이전 페이지를 조회하지 않는다', async () => {
    // arrange
    getChannelMessages.mockResolvedValue(page(range(1, 20), false))
    await open()

    // act
    fireEvent.scroll(document.querySelector('.message-scroll'))
    await flush()

    // assert
    expect(screen.getByText('#점심메뉴 채널의 시작이에요.')).toBeTruthy()
    expect(getChannelMessages).toHaveBeenCalledTimes(1)
  })

  it('success case : 첫 페이지가 20개보다 적어도 hasNext 면 이전 페이지를 조회한다', async () => {
    // arrange
    getChannelMessages.mockResolvedValueOnce(page(range(11, 13), true)).mockResolvedValueOnce(page(range(1, 10), false))
    await open()

    // act
    fireEvent.scroll(document.querySelector('.message-scroll'))
    await flush()

    // assert
    expect(getChannelMessages).toHaveBeenLastCalledWith(11, { before: id(11), size: 20 })
    expect(screen.getByText('#점심메뉴 채널의 시작이에요.')).toBeTruthy()
  })

  it('fail case : 이전 페이지가 정확히 20개여도 hasNext 가 false 면 더 조회하지 않는다', async () => {
    // arrange
    getChannelMessages.mockResolvedValueOnce(page(range(21, 40), true)).mockResolvedValueOnce(page(range(1, 20), false))
    await open()
    const box = document.querySelector('.message-scroll')
    fireEvent.scroll(box)
    await flush()

    // act
    fireEvent.scroll(box)
    await flush()

    // assert
    expect(getChannelMessages).toHaveBeenCalledTimes(2)
    expect(screen.getByText('#점심메뉴 채널의 시작이에요.')).toBeTruthy()
  })

  it('fail case : 더 볼 이전 메시지가 없으면 스크롤해도 조회하지 않는다', async () => {
    // arrange
    getChannelMessages.mockResolvedValue(page(range(1, 3)))
    await open()

    // act
    fireEvent.scroll(document.querySelector('.message-scroll'))
    await flush()

    // assert
    expect(getChannelMessages).toHaveBeenCalledTimes(1)
  })

  it('success case : 이전 메시지 조회 중에 또 스크롤해도 한 번만 조회한다', async () => {
    // arrange
    let resolveOlder
    getChannelMessages.mockResolvedValueOnce(page(range(6, 25), true)).mockReturnValueOnce(new Promise((r) => { resolveOlder = r }))
    await open()
    const box = document.querySelector('.message-scroll')

    // act
    fireEvent.scroll(box)
    fireEvent.scroll(box)
    await act(async () => resolveOlder(page(range(1, 5))))

    // assert
    expect(getChannelMessages).toHaveBeenCalledTimes(2)
  })

  it('fail case : 이전 메시지 조회가 실패하면 에러를 보여 준다', async () => {
    // arrange
    getChannelMessages.mockRejectedValue(new Error('채널 멤버가 아닙니다.'))

    // act
    await open()

    // assert
    expect(screen.getByText('채널 멤버가 아닙니다.')).toBeTruthy()
  })

  it('success case : 채널을 바꾼 뒤 늦게 온 이전 채널 응답은 버린다', async () => {
    // arrange
    let resolveLunch
    getChannelMessages
      .mockReturnValueOnce(new Promise((resolve) => { resolveLunch = resolve }))
      .mockResolvedValueOnce(page([msg(9, { channelId: GENERAL.channelId })]))
    const socket = fakeSocket()
    const { rerender } = render(view({ socket }))
    rerender(view({ socket, channel: GENERAL }))
    await flush()

    // act
    await act(async () => resolveLunch(page(range(1, 2))))

    // assert
    expect(shownTexts()).toEqual(['msg 9'])
    expect(screen.getByRole('heading', { level: 1 }).textContent).toBe('# general')
  })

  it('success case : 이전 페이지 조회 중에 채널을 바꾸면 늦게 온 응답을 버린다', async () => {
    // arrange
    let resolveOlder
    getChannelMessages
      .mockResolvedValueOnce(page(range(6, 25), true))
      .mockReturnValueOnce(new Promise((resolve) => { resolveOlder = resolve }))
      .mockResolvedValueOnce(page([msg(30, { channelId: GENERAL.channelId })]))
    const socket = fakeSocket()
    const { rerender } = render(view({ socket }))
    await flush()
    fireEvent.scroll(document.querySelector('.message-scroll'))

    // act
    rerender(view({ socket, channel: GENERAL }))
    await flush()
    await act(async () => resolveOlder(page(range(1, 5))))

    // assert
    expect(shownTexts()).toEqual(['msg 30'])
  })

  // ── 실시간 / 재연결 ─────────────────────────────────

  it('success case : 이 채널의 실시간 메시지는 붙이고, 같은 messageId 가 또 오면 한 번만 그린다', async () => {
    // arrange
    getChannelMessages.mockResolvedValue(page(range(1, 2)))
    const { socket } = await open()

    // act
    socket.emit({ type: 'message', ...msg(3) })
    socket.emit({ type: 'message', ...msg(3) })

    // assert
    expect(shownTexts()).toEqual(['msg 1', 'msg 2', 'msg 3'])
  })

  it('fail case : 다른 채널의 실시간 메시지는 그리지 않는다', async () => {
    // arrange
    getChannelMessages.mockResolvedValue(page(range(1, 2)))
    const { socket } = await open()

    // act
    socket.emit({ type: 'message', ...msg(3, { channelId: 99 }) })

    // assert
    expect(shownTexts()).toEqual(['msg 1', 'msg 2'])
  })

  it('success case : 다시 연결되면 가장 최근 메시지 이후(after)를 조회해서 끊긴 사이 메시지를 채운다', async () => {
    // arrange
    getChannelMessages.mockResolvedValueOnce(page(range(1, 3))).mockResolvedValueOnce(page(range(4, 5)))
    const socket = fakeSocket({ connectionId: 1 })
    const { rerender } = render(view({ socket }))
    await flush()

    // act
    await reconnect(rerender, socket)
    await flush()

    // assert
    expect(getChannelMessages).toHaveBeenLastCalledWith(11, { after: id(3), size: 100 })
    expect(shownTexts()).toEqual(['msg 1', 'msg 2', 'msg 3', 'msg 4', 'msg 5'])
  })

  it('success case : 빈틈 조회가 hasNext 면 그 뒤를 이어서 조회한다', async () => {
    // arrange
    getChannelMessages
      .mockResolvedValueOnce(page(range(1, 3)))
      .mockResolvedValueOnce(page(range(4, 103, { minutes: 0 }), true))
      .mockResolvedValueOnce(page(range(104, 105, { minutes: 0 })))
    const socket = fakeSocket({ connectionId: 1 })
    const { rerender } = render(view({ socket }))
    await flush()

    // act
    await reconnect(rerender, socket)
    await flush()

    // assert
    expect(getChannelMessages.mock.calls.slice(1)).toEqual([
      [11, { after: id(3), size: 100 }],
      [11, { after: id(103), size: 100 }],
    ])
    expect(shownTexts()).toHaveLength(105)
  })

  it('fail case : 빈틈 조회가 정확히 100개여도 hasNext 가 false 면 더 조회하지 않는다', async () => {
    // arrange
    getChannelMessages.mockResolvedValueOnce(page(range(1, 3))).mockResolvedValueOnce(page(range(4, 103, { minutes: 0 }), false))
    const socket = fakeSocket({ connectionId: 1 })
    const { rerender } = render(view({ socket }))
    await flush()

    // act
    await reconnect(rerender, socket)
    await flush()

    // assert
    expect(getChannelMessages).toHaveBeenCalledTimes(2)
    expect(shownTexts()).toHaveLength(103)
  })

  it('success case : 빈틈 조회가 100개보다 적어도 hasNext 면 이어서 조회한다', async () => {
    // arrange
    getChannelMessages
      .mockResolvedValueOnce(page(range(1, 3)))
      .mockResolvedValueOnce(page(range(4, 5), true))
      .mockResolvedValueOnce(page(range(6, 6), false))
    const socket = fakeSocket({ connectionId: 1 })
    const { rerender } = render(view({ socket }))
    await flush()

    // act
    await reconnect(rerender, socket)
    await flush()

    // assert
    expect(getChannelMessages.mock.calls.slice(1)).toEqual([
      [11, { after: id(3), size: 100 }],
      [11, { after: id(5), size: 100 }],
    ])
  })

  it('success case : 다시 연결됐는데 가진 메시지가 없으면 최신 페이지를 조회한다', async () => {
    // arrange
    getChannelMessages.mockResolvedValueOnce(page([])).mockResolvedValueOnce(page(range(1, 2)))
    const socket = fakeSocket({ connectionId: 0, status: 'connecting' })
    const { rerender } = render(view({ socket }))
    await flush()

    // act
    rerender(view({ socket: { ...socket, status: 'connected', connectionId: 1 } }))
    await flush()

    // assert
    expect(getChannelMessages).toHaveBeenLastCalledWith(11, { size: 20 })
    expect(shownTexts()).toEqual(['msg 1', 'msg 2'])
  })

  it('fail case : 다시 연결돼 받은 최신 페이지가 정확히 20개여도 hasNext 가 false 면 채널 시작 안내를 보여 준다', async () => {
    // arrange
    getChannelMessages.mockResolvedValueOnce(page([])).mockResolvedValueOnce(page(range(1, 20), false))
    const socket = fakeSocket({ connectionId: 0, status: 'connecting' })
    const { rerender } = render(view({ socket }))
    await flush()

    // act
    rerender(view({ socket: { ...socket, status: 'connected', connectionId: 1 } }))
    await flush()

    // assert
    expect(shownTexts()).toHaveLength(20)
    expect(screen.getByText('#점심메뉴 채널의 시작이에요.')).toBeTruthy()
  })

  it('success case : 다시 연결되자마자 실시간 메시지가 먼저 들어와도 끊기기 전 가장 최근 메시지를 기준으로 채운다', async () => {
    // arrange
    getChannelMessages.mockResolvedValueOnce(page(range(1, 2))).mockResolvedValueOnce(page(range(3, 6)))
    const socket = fakeSocket({ connectionId: 1 })
    const { rerender } = render(view({ socket }))
    await flush()
    rerender(view({ socket: { ...socket, status: 'reconnecting' } }))
    await flush()

    // act
    act(() => {
      rerender(view({ socket: { ...socket, status: 'connected', connectionId: 2 } }))
      socket.emit({ type: 'message', ...msg(6) })
    })
    await flush()

    // assert
    expect(getChannelMessages).toHaveBeenLastCalledWith(11, { after: id(2), size: 100 })
    expect(shownTexts()).toEqual(['msg 1', 'msg 2', 'msg 3', 'msg 4', 'msg 5', 'msg 6'])
  })

  it('success case : 끊겨 있는 동안 처음 불러온 메시지가 있으면 그 최신 것을 기준으로 채운다', async () => {
    // arrange
    getChannelMessages.mockResolvedValueOnce(page(range(1, 3))).mockResolvedValueOnce(page(range(4, 4)))
    const socket = fakeSocket({ connectionId: 0, status: 'connecting' })
    const { rerender } = render(view({ socket }))
    await flush()

    // act
    act(() => {
      rerender(view({ socket: { ...socket, status: 'connected', connectionId: 1 } }))
      socket.emit({ type: 'message', ...msg(9) })
    })
    await flush()

    // assert
    expect(getChannelMessages).toHaveBeenLastCalledWith(11, { after: id(3), size: 100 })
  })

  it('fail case : 연결 번호가 그대로면 빈틈 조회를 하지 않는다', async () => {
    // arrange
    getChannelMessages.mockResolvedValue(page(range(1, 3)))
    const socket = fakeSocket({ connectionId: 1 })
    const { rerender } = render(view({ socket }))
    await flush()

    // act
    rerender(view({ socket: { ...socket } }))
    await flush()

    // assert
    expect(getChannelMessages).toHaveBeenCalledTimes(1)
  })

  // ── 안 읽은 메시지 : "새 메시지" 구분선 / 알약 ─────────

  it('success case : 첫 안 읽은 메시지 바로 앞에 "새 메시지" 구분선을 그린다', async () => {
    // arrange
    getChannelMessages.mockResolvedValue(page(range(1, 4)))
    getCatchUp.mockResolvedValue({ unreadCount: 2, firstUnreadMessageId: id(3) })

    // act
    await open()

    // assert
    expect(getCatchUp).toHaveBeenCalledWith(11)
    const rows = [...document.querySelectorAll('.message-list > *')]
    const dividerIndex = rows.findIndex((row) => row.classList.contains('unread-divider'))
    expect(rows[dividerIndex].textContent).toBe('새 메시지')
    expect(rows[dividerIndex + 1].textContent).toContain('msg 3')
    expect(rows[dividerIndex - 1].textContent).toContain('msg 2')
  })

  it('fail case : 캐치업 조회가 실패해도 구분선만 없고 채널은 그대로 쓴다', async () => {
    // arrange
    getChannelMessages.mockResolvedValue(page(range(1, 3)))
    getCatchUp.mockRejectedValue(new Error('404'))

    // act
    await open()

    // assert
    expect(shownTexts()).toEqual(['msg 1', 'msg 2', 'msg 3'])
    expect(document.querySelector('.unread-divider')).toBeNull()
    expect(document.querySelector('.unread-pill')).toBeNull()
    expect(screen.queryByRole('alert')).toBeNull()
    expect(textarea().disabled).toBe(false)
  })

  it('success case : 안 읽은 메시지가 있으면 열 때 맨 아래가 아니라 "새 메시지" 구분선으로 스크롤한다', async () => {
    // arrange
    fakeLayout({ scrollHeight: 3000, clientHeight: 500, dividerTop: 800 })
    getChannelMessages.mockResolvedValue(page(range(1, 8)))
    getCatchUp.mockResolvedValue({ unreadCount: 6, firstUnreadMessageId: id(3) })

    // act
    await open()

    // assert
    expect(scrollBox().scrollTop).toBe(752)
  })

  it('success case : 안 읽은 메시지가 없으면 열 때 맨 아래로 스크롤한다', async () => {
    // arrange
    fakeLayout({ scrollHeight: 3000, clientHeight: 500, dividerTop: 800 })
    getChannelMessages.mockResolvedValue(page(range(1, 8)))

    // act
    await open()

    // assert
    expect(scrollBox().scrollTop).toBe(3000)
  })

  it('success case : 구분선이 화면 위로 지나가면 "↑ 안 읽은 메시지 N개" 알약을 띄우고, 누르면 구분선으로 돌아가며 알약이 사라진다', async () => {
    // arrange
    fakeLayout({ scrollHeight: 3000, clientHeight: 500, dividerTop: 800 })
    getChannelMessages.mockResolvedValue(page(range(1, 8)))
    getCatchUp.mockResolvedValue({ unreadCount: 6, firstUnreadMessageId: id(3) })
    await open()
    const pillWhileDividerVisible = document.querySelector('.unread-pill')
    scrollBox().scrollTop = 1500
    fireEvent.scroll(scrollBox())
    const pill = screen.getByText('↑ 안 읽은 메시지 6개')

    // act
    fireEvent.click(pill)
    await flush()

    // assert
    expect(pillWhileDividerVisible).toBeNull()
    expect(scrollBox().scrollTop).toBe(752)
    expect(document.querySelector('.unread-pill')).toBeNull()
  })

  it('success case : 맨 아래까지 읽어 읽음 처리되면 알약이 사라진다', async () => {
    // arrange
    fakeLayout({ scrollHeight: 3000, clientHeight: 500, dividerTop: 800 })
    getChannelMessages.mockResolvedValue(page(range(1, 8)))
    getCatchUp.mockResolvedValue({ unreadCount: 6, firstUnreadMessageId: id(3) })
    await open()
    scrollBox().scrollTop = 2500
    fireEvent.scroll(scrollBox())
    const pillBeforeRead = document.querySelector('.unread-pill')

    // act
    act(() => vi.advanceTimersByTime(500))

    // assert
    expect(pillBeforeRead).not.toBeNull()
    expect(markRead).toHaveBeenCalledWith(11, id(8))
    expect(document.querySelector('.unread-pill')).toBeNull()
  })

  it('success case : 첫 안 읽은 메시지가 아직 안 불러온 범위면 알약을 띄우고, 누르면 이전 페이지를 불러 가며 찾는다', async () => {
    // arrange
    getChannelMessages
      .mockResolvedValueOnce(page(range(41, 60), true))
      .mockResolvedValueOnce(page(range(21, 40), true))
      .mockResolvedValueOnce(page(range(1, 20)))
    getCatchUp.mockResolvedValue({ unreadCount: 30, firstUnreadMessageId: id(31) })
    await open()

    // act
    fireEvent.click(screen.getByText('↑ 안 읽은 메시지 30개'))
    await flush()

    // assert
    expect(getChannelMessages.mock.calls.slice(1)).toEqual([[11, { before: id(41), size: 20 }]])
    expect(document.querySelector('.unread-divider').nextElementSibling.textContent).toContain('msg 31')
  })

  it('fail case : 알약으로 구분선을 찾다가 이전 페이지가 정확히 20개여도 hasNext 가 false 면 거기서 멈춘다', async () => {
    // arrange
    getChannelMessages
      .mockResolvedValueOnce(page(range(41, 60), true))
      .mockResolvedValueOnce(page(range(21, 40), false))
      .mockResolvedValue(page([]))
    getCatchUp.mockResolvedValue({ unreadCount: 50, firstUnreadMessageId: id(5) })
    await open()

    // act
    fireEvent.click(screen.getByText('↑ 안 읽은 메시지 50개'))
    await flush()

    // assert
    expect(getChannelMessages).toHaveBeenCalledTimes(2)
  })

  it('fail case : 안 읽은 메시지가 없으면 알약을 띄우지 않는다', async () => {
    // arrange
    fakeLayout({ scrollHeight: 3000, clientHeight: 500, dividerTop: 800 })
    getChannelMessages.mockResolvedValue(page(range(1, 8)))
    getCatchUp.mockResolvedValue({ unreadCount: 0, firstUnreadMessageId: null })
    await open()

    // act
    scrollBox().scrollTop = 1500
    fireEvent.scroll(scrollBox())

    // assert
    expect(document.querySelector('.unread-pill')).toBeNull()
  })

  it('success case : 사이드바 배지 수만큼 안 읽은 메시지가 다 들어오게 첫 페이지를 넉넉히 조회한다 (최소 20, 최대 100)', async () => {
    // arrange
    getChannelMessages.mockResolvedValue(page([]))

    // act
    await open({ unreadAtOpen: 30 })

    // assert
    expect(getChannelMessages).toHaveBeenCalledWith(11, { size: 35 })
    expect([firstPageSize(0), firstPageSize(15), firstPageSize(16), firstPageSize(300)]).toEqual([20, 20, 21, 100])
  })

  // ── 읽음 처리 ──────────────────────────────────────

  it('success case : 메시지를 보여 주고 500ms 뒤 가장 최근 messageId 로 읽음 처리한다', async () => {
    // arrange
    getChannelMessages.mockResolvedValue(page(range(1, 3)))
    await open()

    // act
    act(() => vi.advanceTimersByTime(499))
    const before = markRead.mock.calls.length
    act(() => vi.advanceTimersByTime(1))

    // assert
    expect(before).toBe(0)
    expect(markRead).toHaveBeenCalledTimes(1)
    expect(markRead).toHaveBeenCalledWith(11, id(3))
  })

  it('success case : 새 메시지가 연달아 오면 마지막 것으로 한 번만 읽음 처리한다', async () => {
    // arrange
    getChannelMessages.mockResolvedValue(page(range(1, 3)))
    const { socket } = await open()
    act(() => vi.advanceTimersByTime(500))
    markRead.mockClear()

    // act
    socket.emit({ type: 'message', ...msg(4) })
    act(() => vi.advanceTimersByTime(300))
    socket.emit({ type: 'message', ...msg(5) })
    act(() => vi.advanceTimersByTime(500))

    // assert
    expect(markRead.mock.calls).toEqual([[11, id(5)]])
  })

  it('fail case : 가장 최근 메시지가 그대로면(이전 페이지만 붙음) 다시 읽음 처리하지 않는다', async () => {
    // arrange
    getChannelMessages.mockResolvedValueOnce(page(range(6, 25), true)).mockResolvedValueOnce(page(range(1, 5)))
    await open()
    act(() => vi.advanceTimersByTime(500))

    // act
    fireEvent.scroll(document.querySelector('.message-scroll'))
    await flush()
    act(() => vi.advanceTimersByTime(1000))

    // assert
    expect(shownTexts()).toHaveLength(25)
    expect(markRead).toHaveBeenCalledTimes(1)
  })

  it('fail case : 캐치업 응답 전에는 읽음 처리하지 않는다 (먼저 읽으면 서버가 안 읽은 수를 0 으로 센다)', async () => {
    // arrange
    let resolveCatchUp
    getChannelMessages.mockResolvedValue(page(range(1, 3)))
    getCatchUp.mockReturnValue(new Promise((resolve) => { resolveCatchUp = resolve }))
    await open()

    // act
    act(() => vi.advanceTimersByTime(2000))
    const whileLoading = markRead.mock.calls.length
    await act(async () => resolveCatchUp(NO_CATCH_UP))
    act(() => vi.advanceTimersByTime(500))

    // assert
    expect(whileLoading).toBe(0)
    expect(markRead).toHaveBeenCalledWith(11, id(3))
  })

  it('fail case : 위로 스크롤해 가장 최근 메시지가 안 보이면 읽음 처리하지 않고, 맨 아래로 오면 한다', async () => {
    // arrange
    fakeLayout({ scrollHeight: 3000, clientHeight: 500, dividerTop: 0 })
    getChannelMessages.mockResolvedValue(page(range(1, 3)))
    await open()
    scrollBox().scrollTop = 1000
    fireEvent.scroll(scrollBox())

    // act
    act(() => vi.advanceTimersByTime(1000))
    const whileScrolledUp = markRead.mock.calls.length
    scrollBox().scrollTop = 2500
    fireEvent.scroll(scrollBox())
    act(() => vi.advanceTimersByTime(500))

    // assert
    expect(whileScrolledUp).toBe(0)
    expect(markRead).toHaveBeenCalledWith(11, id(3))
  })

  it('fail case : 위로 올렸다가 다시 맨 아래로 와도 가장 최근 메시지가 그대로면 다시 읽음 처리하지 않는다', async () => {
    // arrange
    fakeLayout({ scrollHeight: 3000, clientHeight: 500, dividerTop: 0 })
    getChannelMessages.mockResolvedValue(page(range(1, 3)))
    await open()
    act(() => vi.advanceTimersByTime(500))

    // act
    scrollBox().scrollTop = 1000
    fireEvent.scroll(scrollBox())
    scrollBox().scrollTop = 2500
    fireEvent.scroll(scrollBox())
    act(() => vi.advanceTimersByTime(1000))

    // assert
    expect(markRead.mock.calls).toEqual([[11, id(3)]])
  })

  it('fail case : 메시지가 없으면 읽음 처리하지 않는다', async () => {
    // arrange
    getChannelMessages.mockResolvedValue(page([]))
    await open()

    // act
    act(() => vi.advanceTimersByTime(1000))

    // assert
    expect(markRead).not.toHaveBeenCalled()
  })

  // ── 입력창 ────────────────────────────────────────

  it('success case : Enter 를 누르면 {type, channelId, message} 로 보내고 입력창을 비운다', async () => {
    // arrange
    getChannelMessages.mockResolvedValue(page([]))
    const { socket } = await open()
    fireEvent.change(textarea(), { target: { value: '점심 뭐 먹을까요?' } })

    // act
    fireEvent.keyDown(textarea(), { key: 'Enter' })

    // assert
    expect(socket.send).toHaveBeenCalledWith({ type: 'message', channelId: 11, message: '점심 뭐 먹을까요?' })
    expect(textarea().value).toBe('')
  })

  it('fail case : Shift+Enter 는 줄바꿈이라 보내지 않는다', async () => {
    // arrange
    getChannelMessages.mockResolvedValue(page([]))
    const { socket } = await open()
    fireEvent.change(textarea(), { target: { value: '첫 줄' } })

    // act
    fireEvent.keyDown(textarea(), { key: 'Enter', shiftKey: true })

    // assert
    expect(socket.send).not.toHaveBeenCalled()
    expect(textarea().value).toBe('첫 줄')
  })

  it('fail case : 한글 조합 중 Enter 는 조합 확정이라 보내지 않는다', async () => {
    // arrange
    getChannelMessages.mockResolvedValue(page([]))
    const { socket } = await open()
    fireEvent.change(textarea(), { target: { value: '안녕' } })

    // act
    fireEvent.keyDown(textarea(), { key: 'Enter', isComposing: true })

    // assert
    expect(socket.send).not.toHaveBeenCalled()
  })

  it('fail case : 빈 메시지는 보내지 않는다', async () => {
    // arrange
    getChannelMessages.mockResolvedValue(page([]))
    const { socket } = await open()
    fireEvent.change(textarea(), { target: { value: '   ' } })

    // act
    fireEvent.keyDown(textarea(), { key: 'Enter' })

    // assert
    expect(socket.send).not.toHaveBeenCalled()
  })

  it('fail case : 연결되기 전에는 입력창이 잠기고 안내를 보여 준다', async () => {
    // arrange
    getChannelMessages.mockResolvedValue(page([]))

    // act
    await open({ socket: fakeSocket({ status: 'reconnecting' }) })

    // assert
    expect(textarea().disabled).toBe(true)
    expect(textarea().placeholder).toBe('연결되면 메시지를 보낼 수 있어요')
  })

  it('success case : 채널은 "#이름에", DM 은 "이름에게" 보내기 안내를 보여 준다', async () => {
    // arrange
    getChannelMessages.mockResolvedValue(page([]))

    // act
    const { unmount } = await open()
    const channelPlaceholder = textarea().placeholder
    unmount()
    await open({ channel: DM })

    // assert
    expect(channelPlaceholder).toBe('#점심메뉴에 메시지 보내기')
    expect(textarea().placeholder).toBe('영희에게 메시지 보내기')
  })

  it('fail case : 채팅 서버가 error 를 보내면 입력창 위에 보여 주고, 다음에 보내면 지운다', async () => {
    // arrange
    getChannelMessages.mockResolvedValue(page([]))
    const { socket } = await open()

    // act
    socket.emit({ type: 'error', code: 3003, message: '채널 멤버가 아닙니다.' })
    const shown = screen.getByRole('alert').textContent
    fireEvent.change(textarea(), { target: { value: '다시' } })
    fireEvent.keyDown(textarea(), { key: 'Enter' })

    // assert
    expect(shown).toBe('채널 멤버가 아닙니다.')
    expect(screen.queryByRole('alert')).toBeNull()
  })

  // ── 메시지 모양 / 프로필 ───────────────────────────

  it('success case : 이름은 멤버 이름으로 그리고, 같은 사람이 5분 안에 이어 보낸 메시지는 이름을 한 번만 보여 준다', async () => {
    // arrange
    getChannelMessages.mockResolvedValue(page([
      msg(1, { sendUserId: 2, minutes: 0 }),
      msg(2, { sendUserId: 2, minutes: 1 }),
      msg(3, { sendUserId: 1, minutes: 2 }),
      msg(4, { sendUserId: 9, minutes: 3 }),
    ]))

    // act
    await open()

    // assert
    const names = [...document.querySelectorAll('.sender-name')].map((n) => n.textContent)
    expect(names).toEqual(['영희', '철수', 'User 9'])
    expect(document.querySelectorAll('.message-row.continued')).toHaveLength(1)
    expect(document.querySelectorAll('.day-divider')).toHaveLength(1)
  })

  it('success case : 아바타나 이름을 누르면 그 사람 프로필을 연다', async () => {
    // arrange
    getChannelMessages.mockResolvedValue(page([msg(1, { sendUserId: 3 })]))
    const onOpenProfile = vi.fn()
    await open({ onOpenProfile })

    // act
    fireEvent.click(screen.getByLabelText('민수 프로필'))
    fireEvent.click(screen.getByText('민수'))

    // assert
    expect(onOpenProfile.mock.calls).toEqual([[3], [3]])
  })

  it('success case : DM 머리글에는 상대 이름과 접속 상태를 보여 주고, 누르면 상대 프로필을 연다', async () => {
    // arrange
    getChannelMessages.mockResolvedValue(page([]))
    const onOpenProfile = vi.fn()
    await open({ channel: DM, onOpenProfile })

    // act
    const header = document.querySelector('.channel-header')
    fireEvent.click(header.querySelector('.channel-title'))

    // assert
    expect(header.textContent).toContain('영희')
    expect(header.querySelector('.presence-dot.online')).not.toBeNull()
    expect(onOpenProfile).toHaveBeenCalledWith(2)
  })
})
