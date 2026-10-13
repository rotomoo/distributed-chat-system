import { act, fireEvent, render, screen, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { getCatchUp, getChannelMessages, markRead, uploadFile } from '../api.js'
import { fakeSocket, flush } from '../test/fakeSocket.js'
import { controlUploads, file, stubObjectUrls } from '../test/fakeUploads.js'
import ChannelView, { REACTION_GUARD_MS } from './ChannelView.jsx'
import { page } from '../test/page.js'

vi.mock('../api.js', () => ({
  getChannelMessages: vi.fn(),
  getCatchUp: vi.fn(),
  markRead: vi.fn(),
  uploadFile: vi.fn(),
  UPLOAD_TOO_LARGE: '20MB 이하만 올릴 수 있어요',
  fileUrl: (fileId) => `/v1/private/api/file/${fileId}`,
}))

const ME = 1
const LUNCH = { channelId: 11, kind: 'channel', name: '점심메뉴' }
const DM = { channelId: 13, kind: 'dm', name: '영희', userId: 2 }
const NAMES = { 1: '철수', 2: '영희', 3: '민수' }
const nameOf = (userId) => NAMES[userId] ?? `User ${userId}`
const id = (n) => n.toString(16).padStart(24, '0')
const msg = (n, extra = {}) => ({
  messageId: id(n), channelId: 11, sendUserId: 2, message: `msg ${n}`, publishDt: new Date(2026, 9, 13, 9, n).getTime(), ...extra,
})
const PNG = { fileId: 'f1', name: '메뉴판.png', contentType: 'image/png', size: 1_258_291 }
const PDF = { fileId: 'f2', name: '견적서.pdf', contentType: 'application/pdf', size: 2048 }

function view({ channel = LUNCH, socket }) {
  return (
    <ChannelView
      key={channel.channelId}
      channel={channel}
      socket={socket}
      myUserId={ME}
      nameOf={nameOf}
      presenceOf={() => true}
      onOpenProfile={() => {}}
      onToggleSidebar={() => {}}
    />
  )
}

async function open({ messages = [], channel, socket = fakeSocket() } = {}) {
  getChannelMessages.mockResolvedValue(page(messages))
  const utils = render(view({ channel, socket }))
  await flush()
  return { ...utils, socket }
}

const row = (n) => document.querySelector(`[data-message-id="${id(n)}"]`)
const chip = (n, emoji) => within(row(n)).getByText(emoji, { selector: '.reaction-emoji' }).closest('button')
const dragEvent = (files) => ({ dataTransfer: { types: ['Files'], files } })

describe('[단위] ChannelView 첨부 / 리액션', () => {
  let uploads

  beforeEach(() => {
    vi.useFakeTimers()
    vi.resetAllMocks()
    localStorage.clear()
    getCatchUp.mockResolvedValue({ unreadCount: 0, firstUnreadMessageId: null })
    markRead.mockResolvedValue(null)
    uploads = controlUploads(uploadFile)
    stubObjectUrls()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  // ── 끌어다 놓기 / 보내기 ─────────────────────────

  it('success case : 파일을 끌고 들어오면 "여기에 놓으면 #점심메뉴에 올려요" 를 띄우고, 나가면 내린다', async () => {
    // arrange
    await open()
    const section = document.querySelector('.channel-view')

    // act
    fireEvent.dragEnter(section, dragEvent([]))
    const shown = screen.getByText('여기에 놓으면 #점심메뉴에 올려요')
    fireEvent.dragEnter(document.querySelector('.message-scroll'), dragEvent([]))
    fireEvent.dragLeave(section, dragEvent([]))
    const stillShown = screen.queryByText('여기에 놓으면 #점심메뉴에 올려요')
    fireEvent.dragLeave(document.querySelector('.message-scroll'), dragEvent([]))

    // assert
    expect(shown).toBeTruthy()
    expect(stillShown).toBeTruthy()
    expect(screen.queryByText('여기에 놓으면 #점심메뉴에 올려요')).toBeNull()
  })

  it('success case : DM 에서는 "영희에게 올려요" 로 안내한다', async () => {
    // arrange
    await open({ channel: DM })

    // act
    fireEvent.dragEnter(document.querySelector('.channel-view'), dragEvent([]))

    // assert
    expect(screen.getByText('여기에 놓으면 영희에게 올려요')).toBeTruthy()
  })

  it('fail case : 파일이 아닌 것(글 조각)을 끌면 안내를 띄우지 않는다', async () => {
    // arrange
    await open()

    // act
    fireEvent.dragEnter(document.querySelector('.channel-view'), { dataTransfer: { types: ['text/plain'], files: [] } })

    // assert
    expect(screen.queryByText(/여기에 놓으면/)).toBeNull()
  })

  it('success case : 놓으면 이 채널로 올리고 안내를 내린다', async () => {
    // arrange
    await open()
    const section = document.querySelector('.channel-view')
    const dropped = file('메뉴판.png', { type: 'image/png' })
    fireEvent.dragEnter(section, dragEvent([dropped]))

    // act
    fireEvent.drop(section, dragEvent([dropped]))

    // assert
    expect(uploads.map((u) => [u.channelId, u.file])).toEqual([[11, dropped]])
    expect(screen.queryByText(/여기에 놓으면/)).toBeNull()
    expect(document.querySelectorAll('.upload-chip')).toHaveLength(1)
  })

  it('success case : 올린 파일과 함께 보내면 attachments:[{fileId}] 를 붙여 보낸다', async () => {
    // arrange
    const { socket } = await open()
    fireEvent.drop(document.querySelector('.channel-view'), dragEvent([file('a.png', { type: 'image/png' }), file('b.pdf')]))
    await act(async () => {
      uploads[0].succeed('f1')
      uploads[1].succeed('f2')
    })
    fireEvent.change(screen.getByRole('textbox', { name: '#점심메뉴에 메시지 보내기' }), { target: { value: ' 메뉴판 ' } })

    // act
    fireEvent.click(screen.getByLabelText('보내기'))

    // assert
    expect(socket.send).toHaveBeenCalledWith({
      type: 'message', channelId: 11, message: '메뉴판', attachments: [{ fileId: 'f1' }, { fileId: 'f2' }],
    })
    expect(document.querySelectorAll('.upload-chip')).toHaveLength(0)
  })

  it('success case : 파일만 보내면 message 는 빈 글로 보낸다', async () => {
    // arrange
    const { socket } = await open()
    fireEvent.drop(document.querySelector('.channel-view'), dragEvent([file('a.png', { type: 'image/png' })]))
    await act(async () => uploads[0].succeed('f1'))

    // act
    fireEvent.click(screen.getByLabelText('보내기'))

    // assert
    expect(socket.send).toHaveBeenCalledWith({ type: 'message', channelId: 11, message: '', attachments: [{ fileId: 'f1' }] })
  })

  // ── 첨부 그리기 ─────────────────────────────────

  it('success case : 이미지 첨부는 썸네일로, 그 외는 이름 / 크기 / 종류가 있는 파일 카드로 그린다', async () => {
    // arrange & act
    await open({ messages: [msg(1, { attachments: [PNG, PDF] })] })

    // assert
    const img = row(1).querySelector('.attachment-image img')
    expect(img.getAttribute('src')).toBe('/v1/private/api/file/f1')
    expect(img.getAttribute('loading')).toBe('lazy')
    const card = row(1).querySelector('a.file-card')
    expect(card.getAttribute('href')).toBe('/v1/private/api/file/f2')
    expect(card.getAttribute('download')).toBe('견적서.pdf')
    expect(card.textContent).toContain('견적서.pdf')
    expect(card.textContent).toContain('PDF · 2KB')
  })

  it('success case : 글 없이 파일만 있는 메시지는 빈 글 줄 없이 그린다', async () => {
    // arrange & act
    await open({ messages: [msg(1, { message: '', attachments: [PDF] })] })

    // assert
    expect(row(1).querySelector('.message-text')).toBeNull()
    expect(row(1).querySelector('.file-card')).not.toBeNull()
  })

  it('success case : attachments / reactions 가 없는 메시지도 그대로 그린다', async () => {
    // arrange & act
    await open({ messages: [msg(1)] })

    // assert
    expect(row(1).querySelector('.message-text').textContent).toBe('msg 1')
    expect(row(1).querySelector('.attachments')).toBeNull()
    expect(row(1).querySelector('.reactions')).toBeNull()
  })

  it('success case : 실시간 메시지의 첨부도 그린다', async () => {
    // arrange
    const { socket } = await open()

    // act
    socket.emit({ type: 'message', ...msg(5, { message: '', attachments: [PNG] }) })

    // assert
    expect(row(5).querySelector('.attachment-image img').getAttribute('alt')).toBe('메뉴판.png')
  })

  it('success case : 이미지를 누르면 크게 보고, 다운로드 링크가 있으며, Esc 나 바깥을 누르면 닫는다', async () => {
    // arrange
    await open({ messages: [msg(1, { attachments: [PNG] })] })

    // act
    fireEvent.click(screen.getByLabelText('메뉴판.png 크게 보기'))
    const lightbox = screen.getByRole('dialog', { name: '메뉴판.png' })
    const download = within(lightbox).getByText('다운로드')
    const image = lightbox.querySelector('img').getAttribute('src')
    fireEvent.keyDown(window, { key: 'Escape' })
    const closedByEsc = screen.queryByRole('dialog', { name: '메뉴판.png' }) === null
    fireEvent.click(screen.getByLabelText('메뉴판.png 크게 보기'))
    fireEvent.click(screen.getByRole('dialog', { name: '메뉴판.png' }))

    // assert
    expect(download.getAttribute('href')).toBe('/v1/private/api/file/f1')
    expect(download.getAttribute('download')).toBe('메뉴판.png')
    expect(image).toBe('/v1/private/api/file/f1')
    expect(lightbox.textContent).toContain('1.2MB')
    expect(closedByEsc).toBe(true)
    expect(screen.queryByRole('dialog', { name: '메뉴판.png' })).toBeNull()
  })

  // ── 리액션 ──────────────────────────────────────

  it('success case : 리액션은 "👍 2" 칩으로, 내가 누른 것은 강조하고 누른 사람 이름을 알려 준다', async () => {
    // arrange & act
    await open({ messages: [msg(1, { reactions: [{ emoji: '👍', userIds: [1, 2] }, { emoji: '🎉', userIds: [3] }] })] })

    // assert
    expect(chip(1, '👍').textContent).toBe('👍2')
    expect(chip(1, '👍').getAttribute('aria-pressed')).toBe('true')
    expect(chip(1, '👍').classList.contains('mine')).toBe(true)
    expect(chip(1, '🎉').getAttribute('aria-pressed')).toBe('false')
    expect(chip(1, '👍').title).toBe('철수, 영희님이 반응했어요')
  })

  it('success case : 남이 누른 칩을 누르면 add, 내가 누른 칩을 누르면 remove 를 보낸다 (화면은 바로 바꾸지 않는다)', async () => {
    // arrange
    const { socket } = await open({ messages: [msg(1, { reactions: [{ emoji: '👍', userIds: [1] }, { emoji: '🎉', userIds: [3] }] })] })

    // act
    fireEvent.click(chip(1, '🎉'))
    fireEvent.click(chip(1, '👍'))

    // assert
    expect(socket.send.mock.calls.map(([payload]) => payload)).toEqual([
      { type: 'reaction', channelId: 11, messageId: id(1), emoji: '🎉', action: 'add' },
      { type: 'reaction', channelId: 11, messageId: id(1), emoji: '👍', action: 'remove' },
    ])
    expect(chip(1, '🎉').textContent).toBe('🎉1')
  })

  it('success case : 서버의 reaction 이벤트가 오면 그 메시지 리액션을 통째로 바꾼다', async () => {
    // arrange
    const { socket } = await open({ messages: [msg(1, { reactions: [{ emoji: '👍', userIds: [2] }] }), msg(2)] })

    // act
    socket.emit({ type: 'reaction', channelId: 11, messageId: id(1), reactions: [{ emoji: '👍', userIds: [2, 1] }, { emoji: '🔥', userIds: [1] }] })

    // assert
    expect([...row(1).querySelectorAll('.reaction-chip:not(.reaction-add)')].map((b) => b.textContent)).toEqual(['👍2', '🔥1'])
    expect(chip(1, '🔥').classList.contains('mine')).toBe(true)
    expect(row(2).querySelector('.reactions')).toBeNull()
  })

  it('success case : 마지막 리액션이 빠지면 칩 줄을 지운다', async () => {
    // arrange
    const { socket } = await open({ messages: [msg(1, { reactions: [{ emoji: '👍', userIds: [1] }] })] })

    // act
    socket.emit({ type: 'reaction', channelId: 11, messageId: id(1), reactions: [] })

    // assert
    expect(row(1).querySelector('.reactions')).toBeNull()
  })

  it('fail case : 다른 채널 / 불러오지 않은 메시지의 reaction 이벤트는 무시한다', async () => {
    // arrange
    const { socket } = await open({ messages: [msg(1, { reactions: [{ emoji: '👍', userIds: [2] }] })] })

    // act
    socket.emit({ type: 'reaction', channelId: 99, messageId: id(1), reactions: [{ emoji: '😡', userIds: [3] }] })
    socket.emit({ type: 'reaction', channelId: 11, messageId: id(77), reactions: [{ emoji: '😡', userIds: [3] }] })

    // assert
    expect([...row(1).querySelectorAll('.reaction-emoji')].map((n) => n.textContent)).toEqual(['👍'])
    expect(document.querySelector('[data-message-id="' + id(77) + '"]')).toBeNull()
  })

  it('success case : 같은 칩을 연타하면 두 번째는 무시하고, 그 메시지의 reaction 이벤트가 오면 다시 누를 수 있다', async () => {
    // arrange
    const { socket } = await open({ messages: [msg(1, { reactions: [{ emoji: '👍', userIds: [2] }] })] })

    // act
    fireEvent.click(chip(1, '👍'))
    fireEvent.click(chip(1, '👍'))
    const afterDoubleClick = socket.send.mock.calls.length
    socket.emit({ type: 'reaction', channelId: 11, messageId: id(1), reactions: [{ emoji: '👍', userIds: [2, 1] }] })
    fireEvent.click(chip(1, '👍'))

    // assert
    expect(afterDoubleClick).toBe(1)
    expect(socket.send.mock.calls.map(([p]) => p.action)).toEqual(['add', 'remove'])
  })

  it('success case : 이벤트가 오지 않아도 3초가 지나면 다시 누를 수 있다', async () => {
    // arrange
    const { socket } = await open({ messages: [msg(1, { reactions: [{ emoji: '👍', userIds: [2] }] })] })
    fireEvent.click(chip(1, '👍'))

    // act
    act(() => vi.advanceTimersByTime(REACTION_GUARD_MS - 1))
    fireEvent.click(chip(1, '👍'))
    const beforeGuardEnds = socket.send.mock.calls.length
    act(() => vi.advanceTimersByTime(1))
    fireEvent.click(chip(1, '👍'))

    // assert
    expect(beforeGuardEnds).toBe(1)
    expect(socket.send).toHaveBeenCalledTimes(2)
  })

  it('fail case : 연타 막기는 칩마다 따로다 (다른 이모지는 바로 누를 수 있다)', async () => {
    // arrange
    const { socket } = await open({ messages: [msg(1, { reactions: [{ emoji: '👍', userIds: [2] }, { emoji: '🎉', userIds: [2] }] })] })

    // act
    fireEvent.click(chip(1, '👍'))
    fireEvent.click(chip(1, '🎉'))

    // assert
    expect(socket.send).toHaveBeenCalledTimes(2)
  })

  it('fail case : 연결이 끊겨 못 보냈으면 연타 막기를 걸지 않는다', async () => {
    // arrange
    const socket = fakeSocket()
    socket.send.mockReturnValueOnce(false)
    await open({ socket, messages: [msg(1, { reactions: [{ emoji: '👍', userIds: [2] }] })] })

    // act
    fireEvent.click(chip(1, '👍'))
    fireEvent.click(chip(1, '👍'))

    // assert
    expect(socket.send).toHaveBeenCalledTimes(2)
  })

  it('success case : 마우스를 올리면 나오는 동작 상자의 "반응 추가" 로 이모지를 골라 반응한다', async () => {
    // arrange
    const { socket } = await open({ messages: [msg(1)] })

    // act
    fireEvent.click(within(row(1).querySelector('.message-actions')).getByLabelText('반응 추가'))
    const pickingRow = row(1).classList.contains('picking')
    fireEvent.click(within(screen.getByRole('dialog', { name: '이모지 고르기' })).getByRole('button', { name: '🎉' }))

    // assert
    expect(pickingRow).toBe(true)
    expect(socket.send).toHaveBeenCalledWith({ type: 'reaction', channelId: 11, messageId: id(1), emoji: '🎉', action: 'add' })
    expect(screen.queryByRole('dialog', { name: '이모지 고르기' })).toBeNull()
  })

  it('success case : 이미 내가 단 이모지를 고르기 창에서 다시 고르면 remove 를 보낸다', async () => {
    // arrange
    const { socket } = await open({ messages: [msg(1, { reactions: [{ emoji: '👍', userIds: [1] }] })] })

    // act
    fireEvent.click(within(row(1).querySelector('.reactions')).getByLabelText('반응 추가'))
    fireEvent.click(within(screen.getByRole('dialog', { name: '이모지 고르기' })).getAllByRole('button', { name: '👍' })[0])

    // assert
    expect(socket.send).toHaveBeenCalledWith({ type: 'reaction', channelId: 11, messageId: id(1), emoji: '👍', action: 'remove' })
  })
})
