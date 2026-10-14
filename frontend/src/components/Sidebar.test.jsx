import { fireEvent, render, screen, within } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flush } from '../test/fakeSocket.js'
import Sidebar from './Sidebar.jsx'

const ME = { userId: 1, userName: '철수', account: 'chulsoo' }
const TEAM = { teamId: 1, teamName: '60hz' }
const CHANNELS = [
  { channelId: 10, channelName: 'general', joined: true, unreadCount: 0 },
  { channelId: 11, channelName: '점심메뉴', joined: true, unreadCount: 3 },
  { channelId: 12, channelName: '운동', joined: false, unreadCount: 0 },
]
const DMS = [
  { channelId: 13, userId: 2, userName: '영희', unreadCount: 1 },
  { channelId: 14, userId: 3, userName: '민수', unreadCount: 0 },
]

let handlers
function renderSidebar(props = {}) {
  handlers = {
    onSelect: vi.fn(),
    onCreateChannel: vi.fn().mockResolvedValue(),
    onJoinChannel: vi.fn().mockResolvedValue(),
    onOpenDmPicker: vi.fn(),
    onOpenTeamModal: vi.fn(),
    onOpenProfile: vi.fn(),
    onLogout: vi.fn(),
  }
  return render(
    <Sidebar
      team={TEAM}
      channels={CHANNELS}
      dms={DMS}
      selectedId={10}
      me={ME}
      status="connected"
      presenceOf={(userId) => ({ 2: 'online', 3: 'offline' })[userId]}
      {...handlers}
      {...props}
    />,
  )
}

const navLabels = () => [...document.querySelectorAll('.nav-item .nav-label')].map((n) => n.textContent)
const navItem = (label) => screen.getByText(label, { selector: '.nav-item .nav-label' }).closest('.nav-item')

describe('[단위] Sidebar', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  it('success case : 맨 위에 팀 이름, 참여한 채널과 DM 을 보여 주고 지금 채널을 강조한다', () => {
    // arrange & act
    renderSidebar()

    // assert
    expect(document.querySelector('.team-header').textContent).toContain('60hz')
    expect(navLabels()).toEqual(['general', '점심메뉴', '영희', '민수'])
    expect(navItem('general').classList.contains('active')).toBe(true)
    expect(navItem('점심메뉴').classList.contains('active')).toBe(false)
  })

  it('success case : 안 읽은 메시지가 있으면 굵게 + 숫자 배지, 없으면 배지가 없다', () => {
    // arrange & act
    renderSidebar()

    // assert
    expect(navItem('점심메뉴').classList.contains('unread')).toBe(true)
    expect(within(navItem('점심메뉴')).getByText('3')).toBeTruthy()
    expect(within(navItem('영희')).getByText('1')).toBeTruthy()
    expect(navItem('general').classList.contains('unread')).toBe(false)
    expect(navItem('general').querySelector('.badge')).toBeNull()
  })

  it('success case : 채널이나 DM 을 누르면 그 채널을 연다', () => {
    // arrange
    renderSidebar()

    // act
    fireEvent.click(navItem('점심메뉴'))
    fireEvent.click(navItem('영희'))

    // assert
    expect(handlers.onSelect.mock.calls).toEqual([[11], [13]])
  })

  it('success case : DM 에는 상대 접속 상태를 온라인 / 자리 비움 / 오프라인 점으로 보여 준다', () => {
    // arrange & act
    renderSidebar({ presenceOf: (userId) => ({ 2: 'online', 3: 'offline', 4: 'away' })[userId], dms: [...DMS, { channelId: 15, userId: 4, userName: '지수', unreadCount: 0 }] })

    // assert
    expect(navItem('영희').querySelector('.presence-dot.online')).not.toBeNull()
    expect(navItem('민수').querySelector('.presence-dot.offline')).not.toBeNull()
    expect(navItem('지수').querySelector('.presence-dot.away')).not.toBeNull()
  })

  it('success case : 대화 찾기에 입력하면 이름이 맞는 채널 / DM 만 남는다', () => {
    // arrange
    renderSidebar()

    // act
    fireEvent.change(screen.getByLabelText('대화 찾기'), { target: { value: '영' } })

    // assert
    expect(navLabels()).toEqual(['영희'])
  })

  it('success case : 대화 찾기는 대소문자를 가리지 않고 채널 둘러보기에도 적용된다', () => {
    // arrange
    renderSidebar()

    // act
    fireEvent.change(screen.getByLabelText('대화 찾기'), { target: { value: 'GEN' } })

    // assert
    expect(navLabels()).toEqual(['general'])
    expect(screen.queryByText(/채널 둘러보기/)).toBeNull()
  })

  it('success case : 채널 + 를 누르고 이름을 입력해 Enter 하면 채널을 만들고 입력창을 닫는다', async () => {
    // arrange
    renderSidebar()
    fireEvent.click(screen.getByLabelText('채널 만들기'))
    const input = screen.getByLabelText('새 채널 이름')

    // act
    fireEvent.change(input, { target: { value: ' 회식 ' } })
    fireEvent.submit(input.closest('form'))
    await flush()

    // assert
    expect(handlers.onCreateChannel).toHaveBeenCalledWith('회식')
    expect(screen.queryByLabelText('새 채널 이름')).toBeNull()
  })

  it('fail case : 채널 만들기가 실패하면 이유를 보여 주고 입력창은 그대로 둔다', async () => {
    // arrange
    renderSidebar()
    handlers.onCreateChannel.mockRejectedValue(new Error('이미 있는 채널 이름입니다.'))
    fireEvent.click(screen.getByLabelText('채널 만들기'))
    const input = screen.getByLabelText('새 채널 이름')

    // act
    fireEvent.change(input, { target: { value: 'general' } })
    fireEvent.submit(input.closest('form'))
    await flush()

    // assert
    expect(screen.getByRole('alert').textContent).toBe('이미 있는 채널 이름입니다.')
    expect(screen.getByLabelText('새 채널 이름')).toBeTruthy()
  })

  it('fail case : 빈 이름으로는 채널을 만들지 않는다', async () => {
    // arrange
    renderSidebar()
    fireEvent.click(screen.getByLabelText('채널 만들기'))

    // act
    fireEvent.submit(screen.getByLabelText('새 채널 이름').closest('form'))
    await flush()

    // assert
    expect(handlers.onCreateChannel).not.toHaveBeenCalled()
  })

  it('success case : 참여하지 않은 채널은 채널 둘러보기를 펼쳐 참여할 수 있다', async () => {
    // arrange
    renderSidebar()
    expect(screen.queryByText('운동')).toBeNull()

    // act
    fireEvent.click(screen.getByText(/채널 둘러보기/))
    fireEvent.click(screen.getByText('참여'))
    await flush()

    // assert
    expect(screen.getByText('운동')).toBeTruthy()
    expect(handlers.onJoinChannel).toHaveBeenCalledWith(CHANNELS[2])
  })

  it('fail case : 채널 참여가 실패하면 이유를 보여 준다', async () => {
    // arrange
    renderSidebar()
    handlers.onJoinChannel.mockRejectedValue(new Error('존재하지 않는 채널입니다.'))
    fireEvent.click(screen.getByText(/채널 둘러보기/))

    // act
    fireEvent.click(screen.getByText('참여'))
    await flush()

    // assert
    expect(screen.getByRole('alert').textContent).toBe('존재하지 않는 채널입니다.')
  })

  it('success case : 팀 이름을 누르면 팀 창을, DM + 를 누르면 멤버 고르기를 연다', () => {
    // arrange
    renderSidebar()

    // act
    fireEvent.click(document.querySelector('.team-header'))
    fireEvent.click(screen.getByLabelText('새 메시지'))

    // assert
    expect(handlers.onOpenTeamModal).toHaveBeenCalledTimes(1)
    expect(handlers.onOpenDmPicker).toHaveBeenCalledTimes(1)
  })

  it('success case : 아래 내 프로필 줄에 이름과 연결 상태를 보여 주고, 메뉴에서 로그아웃한다', () => {
    // arrange
    renderSidebar({ status: 'reconnecting' })
    const meRow = document.querySelector('.me-row')

    // act
    fireEvent.click(within(meRow).getByText('철수'))
    fireEvent.click(screen.getByRole('menuitem', { name: '로그아웃' }))

    // assert
    expect(meRow.querySelector('.status-dot.reconnecting')).not.toBeNull()
    expect(handlers.onLogout).toHaveBeenCalledTimes(1)
  })

  it('success case : 메뉴의 프로필 보기는 내 프로필을 연다', () => {
    // arrange
    renderSidebar()

    // act
    fireEvent.click(within(document.querySelector('.me-row')).getByText('철수'))
    fireEvent.click(screen.getByRole('menuitem', { name: '프로필 보기' }))

    // assert
    expect(handlers.onOpenProfile).toHaveBeenCalledWith(1)
    expect(screen.queryByRole('menu')).toBeNull()
  })
})
