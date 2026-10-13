import { act, fireEvent, render, screen, within } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { uploadFile } from '../api.js'
import { RECENT_EMOJI_KEY } from '../emoji.js'
import { useUploads } from '../hooks/useUploads.js'
import { flush } from '../test/fakeSocket.js'
import { controlUploads, file, stubObjectUrls } from '../test/fakeUploads.js'
import Composer from './Composer.jsx'

vi.mock('../api.js', () => ({
  uploadFile: vi.fn(),
  UPLOAD_TOO_LARGE: '20MB 이하만 올릴 수 있어요',
}))

function Harness({ onSend, disabled = false }) {
  const uploads = useUploads(11)
  return (
    <Composer placeholder="#점심메뉴에 메시지 보내기" disabled={disabled} disabledHint="연결되면 메시지를 보낼 수 있어요" uploads={uploads} onSend={onSend} />
  )
}

const textarea = () => screen.getByRole('textbox', { name: '#점심메뉴에 메시지 보내기' })
const sendButton = () => screen.getByLabelText('보내기')
const chips = () => [...document.querySelectorAll('.upload-chip')]
const pickFiles = (files) => fireEvent.change(screen.getByTestId('file-input'), { target: { files } })

describe('[단위] Composer', () => {
  let calls
  let onSend

  beforeEach(() => {
    vi.resetAllMocks()
    localStorage.clear()
    calls = controlUploads(uploadFile)
    stubObjectUrls()
    onSend = vi.fn(() => true)
  })

  // ── 파일 붙이기 ─────────────────────────────────

  it('success case : 클립 버튼으로 고른 파일은 바로 올리고, 칩에 미리보기 / 이름 / 진행률을 보여 준다', () => {
    // arrange
    render(<Harness onSend={onSend} />)

    // act
    pickFiles([file('메뉴.png', { type: 'image/png' }), file('견적.pdf', { type: 'application/pdf' })])
    act(() => calls[0].onProgress(0.42))

    // assert
    expect(chips()).toHaveLength(2)
    expect(chips()[0].querySelector('img.upload-thumb').getAttribute('src')).toBe('blob:메뉴.png')
    expect(chips()[0].textContent).toContain('올리는 중 42%')
    expect(within(chips()[0]).getByRole('progressbar').getAttribute('aria-valuenow')).toBe('42')
    expect(chips()[1].querySelector('.file-icon').textContent).toBe('PDF')
  })

  it('fail case : 올리는 중에는 보낼 수 없고 안내를 보여 준다', () => {
    // arrange
    render(<Harness onSend={onSend} />)
    pickFiles([file('a.txt')])
    fireEvent.change(textarea(), { target: { value: '파일 보내요' } })

    // act
    fireEvent.keyDown(textarea(), { key: 'Enter' })

    // assert
    expect(onSend).not.toHaveBeenCalled()
    expect(sendButton().disabled).toBe(true)
    expect(screen.getByText('파일을 다 올리면 보낼 수 있어요')).toBeTruthy()
  })

  it('success case : 다 올라가면 글 없이 파일만으로도 보낼 수 있고, 보내면 칩을 비운다', async () => {
    // arrange
    render(<Harness onSend={onSend} />)
    pickFiles([file('a.png', { type: 'image/png' }), file('b.txt')])
    await act(async () => {
      calls[0].succeed('f1')
      calls[1].succeed('f2')
    })

    // act
    fireEvent.keyDown(textarea(), { key: 'Enter' })

    // assert
    expect(onSend).toHaveBeenCalledWith('', ['f1', 'f2'])
    expect(chips()).toHaveLength(0)
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:a.png')
  })

  it('fail case : 글도 파일도 없으면 보낼 수 없다', () => {
    // arrange
    render(<Harness onSend={onSend} />)

    // act
    fireEvent.keyDown(textarea(), { key: 'Enter' })

    // assert
    expect(onSend).not.toHaveBeenCalled()
    expect(sendButton().disabled).toBe(true)
  })

  it('fail case : 실패한 파일이 있으면 칩에 이유와 다시 버튼을 보여 주고, 보낼 수 없다', async () => {
    // arrange
    render(<Harness onSend={onSend} />)
    pickFiles([file('a.txt')])
    await act(async () => calls[0].fail('채널 멤버가 아닙니다.'))
    fireEvent.change(textarea(), { target: { value: '안녕' } })

    // act
    fireEvent.keyDown(textarea(), { key: 'Enter' })

    // assert
    expect(chips()[0].textContent).toContain('채널 멤버가 아닙니다.')
    expect(screen.getByText('실패한 파일을 다시 올리거나 빼 주세요')).toBeTruthy()
    expect(onSend).not.toHaveBeenCalled()
  })

  it('success case : 다시를 누르면 다시 올리고, 성공하면 보낼 수 있다', async () => {
    // arrange
    render(<Harness onSend={onSend} />)
    pickFiles([file('a.txt')])
    await act(async () => calls[0].fail('네트워크 오류로 올리지 못했어요'))

    // act
    fireEvent.click(screen.getByText('다시'))
    await act(async () => calls[1].succeed('f9'))
    fireEvent.click(sendButton())

    // assert
    expect(calls).toHaveLength(2)
    expect(onSend).toHaveBeenCalledWith('', ['f9'])
  })

  it('success case : ✕ 를 누르면 올리던 것을 취소하고 칩을 뺀다', async () => {
    // arrange
    render(<Harness onSend={onSend} />)
    pickFiles([file('a.txt')])

    // act
    fireEvent.click(screen.getByLabelText('a.txt 빼기'))
    await flush()

    // assert
    expect(calls[0].abort).toHaveBeenCalled()
    expect(chips()).toHaveLength(0)
  })

  it('fail case : 20MB 를 넘는 파일은 붙이지 않고 안내한다', () => {
    // arrange
    render(<Harness onSend={onSend} />)

    // act
    pickFiles([file('big.mov', { size: 20 * 1024 * 1024 + 1 })])

    // assert
    expect(screen.getByRole('alert').textContent).toBe('20MB 이하만 올릴 수 있어요')
    expect(chips()).toHaveLength(0)
    expect(calls).toHaveLength(0)
  })

  it('success case : 클립보드의 이미지를 붙여넣으면 글 대신 파일로 올린다', () => {
    // arrange
    render(<Harness onSend={onSend} />)
    const pasted = file('image.png', { type: 'image/png' })

    // act
    const notPrevented = fireEvent.paste(textarea(), { clipboardData: { files: [pasted] } })

    // assert
    expect(notPrevented).toBe(false)
    expect(calls.map((c) => c.file)).toEqual([pasted])
  })

  it('fail case : 글만 붙여넣으면 파일로 올리지 않고 그대로 둔다', () => {
    // arrange
    render(<Harness onSend={onSend} />)

    // act
    const notPrevented = fireEvent.paste(textarea(), { clipboardData: { files: [] } })

    // assert
    expect(notPrevented).toBe(true)
    expect(calls).toHaveLength(0)
  })

  // ── 이모지 ─────────────────────────────────────

  it('success case : 😀 로 고른 이모지는 커서 자리에 넣고 고르기 창을 닫는다', () => {
    // arrange
    render(<Harness onSend={onSend} />)
    fireEvent.change(textarea(), { target: { value: '안녕하세요' } })
    textarea().setSelectionRange(2, 2)

    // act
    fireEvent.click(screen.getByLabelText('이모지'))
    fireEvent.click(screen.getByRole('button', { name: '👍' }))

    // assert
    expect(textarea().value).toBe('안녕👍하세요')
    expect(textarea().selectionStart).toBe(4)
    expect(screen.queryByRole('dialog', { name: '이모지 고르기' })).toBeNull()
  })

  it('success case : 글을 선택한 채 고르면 선택한 글을 이모지로 바꾼다', () => {
    // arrange
    render(<Harness onSend={onSend} />)
    fireEvent.change(textarea(), { target: { value: '점심 뭐 먹지' } })
    textarea().setSelectionRange(3, 4)

    // act
    fireEvent.click(screen.getByLabelText('이모지'))
    fireEvent.click(screen.getByRole('button', { name: '🍜' }))

    // assert
    expect(textarea().value).toBe('점심 🍜 먹지')
  })

  it('success case : 고른 이모지는 최근 사용으로 기억해서 다음에 맨 앞 묶음으로 보여 준다', () => {
    // arrange
    render(<Harness onSend={onSend} />)
    fireEvent.click(screen.getByLabelText('이모지'))
    fireEvent.click(screen.getByRole('button', { name: '🚀' }))

    // act
    fireEvent.click(screen.getByLabelText('이모지'))

    // assert
    expect(JSON.parse(localStorage.getItem(RECENT_EMOJI_KEY))).toEqual(['🚀'])
    const firstGroup = document.querySelector('.emoji-picker section')
    expect(firstGroup.getAttribute('aria-label')).toBe('최근 사용')
    expect(within(firstGroup).getAllByRole('button').map((b) => b.textContent)).toEqual(['🚀'])
  })

  it('fail case : 연결 전에는 이모지 버튼도 잠긴다 (파일은 미리 올릴 수 있다)', () => {
    // arrange & act
    render(<Harness onSend={onSend} disabled />)

    // assert
    expect(screen.getByLabelText('이모지').disabled).toBe(true)
    expect(screen.getByLabelText('파일 올리기').disabled).toBe(false)
  })
})

describe('[단위] EmojiPicker', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  async function openPicker() {
    const { default: EmojiPicker } = await import('./EmojiPicker.jsx')
    const onPick = vi.fn()
    const onClose = vi.fn()
    render(
      <div>
        <button type="button">밖</button>
        <EmojiPicker anchor={null} onPick={onPick} onClose={onClose} />
      </div>,
    )
    return { onPick, onClose }
  }

  it('success case : 한글 / 영어로 찾아 고를 수 있다', async () => {
    // arrange
    const { onPick } = await openPicker()

    // act
    fireEvent.change(screen.getByLabelText('이모지 찾기'), { target: { value: '커피' } })
    fireEvent.click(screen.getByRole('button', { name: '☕' }))
    fireEvent.change(screen.getByLabelText('이모지 찾기'), { target: { value: 'rocket' } })

    // assert
    expect(onPick).toHaveBeenCalledWith('☕')
    expect(within(document.querySelector('.emoji-scroll')).getAllByRole('button').map((b) => b.textContent)).toEqual(['🚀'])
  })

  it('fail case : 찾는 이모지가 없으면 안내한다', async () => {
    // arrange
    await openPicker()

    // act
    fireEvent.change(screen.getByLabelText('이모지 찾기'), { target: { value: 'zzzz' } })

    // assert
    expect(screen.getByText('찾는 이모지가 없어요')).toBeTruthy()
  })

  it('success case : Esc 나 바깥을 누르면 닫는다', async () => {
    // arrange
    const { onClose } = await openPicker()

    // act
    fireEvent.keyDown(window, { key: 'Escape' })
    fireEvent.mouseDown(screen.getByText('밖'))
    fireEvent.mouseDown(screen.getByLabelText('이모지 찾기'))

    // assert
    expect(onClose).toHaveBeenCalledTimes(2)
  })
})
