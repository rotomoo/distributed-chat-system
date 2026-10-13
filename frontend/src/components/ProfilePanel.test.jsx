import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import ProfilePanel from './ProfilePanel.jsx'
import DmPicker from './DmPicker.jsx'

describe('[단위] ProfilePanel', () => {
  it('success case : 다른 사람 프로필은 이름 / 접속 상태 / 메시지 보내기를 보여 준다', () => {
    // arrange
    const onMessage = vi.fn()
    render(<ProfilePanel person={{ userId: 2, name: '영희', online: true }} isMe={false} onClose={vi.fn()} onMessage={onMessage} />)

    // act
    fireEvent.click(screen.getByText('메시지 보내기'))

    // assert
    expect(screen.getByRole('heading').textContent).toBe('영희')
    expect(screen.getByText('● 온라인')).toBeTruthy()
    expect(onMessage).toHaveBeenCalledWith(2)
  })

  it('success case : 내 프로필은 @아이디를 보여 주고 메시지 보내기는 없다', () => {
    // arrange & act
    render(<ProfilePanel person={{ userId: 1, name: '철수', account: 'chulsoo', online: false }} isMe onClose={vi.fn()} onMessage={vi.fn()} />)

    // assert
    expect(screen.getByText('@chulsoo')).toBeTruthy()
    expect(screen.getByText('○ 오프라인')).toBeTruthy()
    expect(screen.queryByText('메시지 보내기')).toBeNull()
  })

  it('success case : 닫기를 누르면 닫는다', () => {
    // arrange
    const onClose = vi.fn()
    render(<ProfilePanel person={{ userId: 2, name: '영희', online: true }} isMe={false} onClose={onClose} onMessage={vi.fn()} />)

    // act
    fireEvent.click(screen.getByLabelText('프로필 닫기'))

    // assert
    expect(onClose).toHaveBeenCalledTimes(1)
  })
})

describe('[단위] DmPicker', () => {
  const MEMBERS = [
    { userId: 1, userName: '철수', online: true },
    { userId: 2, userName: '영희', online: true },
    { userId: 3, userName: '민수', online: false },
  ]

  it('success case : 나를 뺀 멤버를 접속 상태와 함께 보여 주고, 고르면 그 사람을 넘긴다', () => {
    // arrange
    const onPick = vi.fn()
    render(<DmPicker members={MEMBERS} myUserId={1} onPick={onPick} onClose={vi.fn()} />)

    // act
    fireEvent.click(screen.getByText('민수'))

    // assert
    const names = [...document.querySelectorAll('.pick-name')].map((n) => n.textContent)
    expect(names).toEqual(['영희', '민수'])
    expect(screen.getAllByText('오프라인')).toHaveLength(1)
    expect(onPick).toHaveBeenCalledWith(3)
  })

  it('success case : 이름으로 거른다', () => {
    // arrange
    render(<DmPicker members={MEMBERS} myUserId={1} onPick={vi.fn()} onClose={vi.fn()} />)

    // act
    fireEvent.change(screen.getByLabelText('이름으로 찾기'), { target: { value: '민' } })

    // assert
    expect([...document.querySelectorAll('.pick-name')].map((n) => n.textContent)).toEqual(['민수'])
  })
})
