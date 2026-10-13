import { fireEvent, render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createTeam, getTeams, joinTeam } from '../api.js'
import { flush } from '../test/fakeSocket.js'
import TeamModal from './TeamModal.jsx'

vi.mock('../api.js', () => ({
  getTeams: vi.fn(),
  joinTeam: vi.fn(),
  createTeam: vi.fn(),
}))

const PAGE = {
  list: [{ teamId: 1, teamName: '60hz' }, { teamId: 2, teamName: 'Design Lab' }],
  paging: { totalPageCount: 2 },
}

async function openModal() {
  const onDone = vi.fn()
  const onClose = vi.fn()
  render(<TeamModal myTeamIds={[1]} onDone={onDone} onClose={onClose} />)
  await flush()
  return { onDone, onClose }
}

describe('[단위] TeamModal', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    getTeams.mockResolvedValue(PAGE)
  })

  it('success case : 열면 팀 목록을 보여 주고, 이미 참여한 팀은 "참여 중" 으로 표시한다', async () => {
    // arrange & act
    await openModal()

    // assert
    expect(getTeams).toHaveBeenCalledWith({ teamName: '', pageNumber: 1 })
    const rows = [...document.querySelectorAll('.pick-item')].map((row) => row.textContent)
    expect(rows).toEqual(['6060hz참여 중', 'DLDesign Lab가입'])
  })

  it('success case : 이름으로 찾으면 그 이름으로 첫 페이지를 다시 조회한다', async () => {
    // arrange
    await openModal()
    const input = screen.getByLabelText('팀 이름으로 찾기')

    // act
    fireEvent.change(input, { target: { value: 'Design' } })
    fireEvent.submit(input.closest('form'))
    await flush()

    // assert
    expect(getTeams).toHaveBeenLastCalledWith({ teamName: 'Design', pageNumber: 1 })
  })

  it('success case : 다음을 누르면 다음 페이지를 조회한다', async () => {
    // arrange
    await openModal()

    // act
    fireEvent.click(screen.getByText('다음'))
    await flush()

    // assert
    expect(getTeams).toHaveBeenLastCalledWith({ teamName: '', pageNumber: 2 })
  })

  it('success case : 가입을 누르면 가입하고 그 팀으로 간다', async () => {
    // arrange
    joinTeam.mockResolvedValue({ teamId: 2, teamName: 'Design Lab' })
    const { onDone } = await openModal()

    // act
    fireEvent.click(screen.getByText('가입'))
    await flush()

    // assert
    expect(joinTeam).toHaveBeenCalledWith(2)
    expect(onDone).toHaveBeenCalledWith(2)
  })

  it('fail case : 가입이 실패하면 이유를 보여 주고 창은 그대로 둔다', async () => {
    // arrange
    joinTeam.mockRejectedValue(new Error('존재하지 않는 팀입니다.'))
    const { onDone } = await openModal()

    // act
    fireEvent.click(screen.getByText('가입'))
    await flush()

    // assert
    expect(screen.getByRole('alert').textContent).toBe('존재하지 않는 팀입니다.')
    expect(onDone).not.toHaveBeenCalled()
  })

  it('success case : 새 팀 만들기에서 이름을 넣고 만들면 그 팀으로 간다', async () => {
    // arrange
    createTeam.mockResolvedValue({ teamId: 7, teamName: '새팀', defaultChannelId: 70 })
    const { onDone } = await openModal()
    fireEvent.click(screen.getByRole('tab', { name: '새 팀 만들기' }))

    // act
    fireEvent.change(screen.getByLabelText('팀 이름'), { target: { value: ' 새팀 ' } })
    fireEvent.click(screen.getByText('만들기'))
    await flush()

    // assert
    expect(createTeam).toHaveBeenCalledWith('새팀')
    expect(onDone).toHaveBeenCalledWith(7)
  })

  it('fail case : 팀 만들기가 실패하면 이유를 보여 준다', async () => {
    // arrange
    createTeam.mockRejectedValue(new Error('이미 있는 팀 이름입니다.'))
    const { onDone } = await openModal()
    fireEvent.click(screen.getByRole('tab', { name: '새 팀 만들기' }))

    // act
    fireEvent.change(screen.getByLabelText('팀 이름'), { target: { value: '60hz' } })
    fireEvent.click(screen.getByText('만들기'))
    await flush()

    // assert
    expect(screen.getByRole('alert').textContent).toBe('이미 있는 팀 이름입니다.')
    expect(onDone).not.toHaveBeenCalled()
  })

  it('success case : Esc 나 닫기를 누르면 닫는다', async () => {
    // arrange
    const { onClose } = await openModal()

    // act
    fireEvent.keyDown(window, { key: 'Escape' })
    fireEvent.click(screen.getByLabelText('닫기'))

    // assert
    expect(onClose).toHaveBeenCalledTimes(2)
  })
})
