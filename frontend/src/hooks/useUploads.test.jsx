import { act, renderHook } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { uploadFile } from '../api.js'
import { flush } from '../test/fakeSocket.js'
import { controlUploads, file, stubObjectUrls } from '../test/fakeUploads.js'
import { MAX_FILE_BYTES, useUploads } from './useUploads.js'

vi.mock('../api.js', () => ({
  uploadFile: vi.fn(),
  UPLOAD_TOO_LARGE: '20MB 이하만 올릴 수 있어요',
}))

describe('[단위] useUploads', () => {
  let calls
  let urls

  beforeEach(() => {
    vi.resetAllMocks()
    calls = controlUploads(uploadFile)
    urls = stubObjectUrls()
  })

  it('success case : 붙이자마자 그 채널로 올리고, 이미지만 미리보기 URL 을 만든다', () => {
    // arrange
    const { result } = renderHook(() => useUploads(11))

    // act
    act(() => result.current.add([file('메뉴.png', { type: 'image/png' }), file('견적.pdf', { type: 'application/pdf' })]))

    // assert
    expect(calls.map((c) => [c.channelId, c.file.name])).toEqual([[11, '메뉴.png'], [11, '견적.pdf']])
    expect(result.current.items.map((i) => [i.status, i.previewUrl])).toEqual([['uploading', 'blob:메뉴.png'], ['uploading', null]])
    expect(result.current.uploading).toBe(true)
  })

  it('success case : 진행률을 반영하고, 다 올라가면 fileId 를 모은다', async () => {
    // arrange
    const { result } = renderHook(() => useUploads(11))
    act(() => result.current.add([file('a.txt')]))

    // act
    act(() => calls[0].onProgress(0.4))
    const progress = result.current.items[0].progress
    await act(async () => calls[0].succeed('f1'))

    // assert
    expect(progress).toBe(0.4)
    expect(result.current.items[0].status).toBe('done')
    expect(result.current.fileIds).toEqual(['f1'])
    expect(result.current.uploading).toBe(false)
  })

  it('fail case : 실패하면 칩에 이유를 남기고, 다시를 누르면 같은 파일을 다시 올린다', async () => {
    // arrange
    const { result } = renderHook(() => useUploads(11))
    act(() => result.current.add([file('a.txt')]))
    await act(async () => calls[0].fail('채널 멤버가 아닙니다.'))
    const failed = { ...result.current.items[0], anyFailed: result.current.failed }

    // act
    act(() => result.current.retry(result.current.items[0].id))
    await act(async () => calls[1].succeed('f2'))

    // assert
    expect(failed).toMatchObject({ status: 'error', error: '채널 멤버가 아닙니다.', anyFailed: true })
    expect(calls[1].file).toBe(calls[0].file)
    expect(result.current.fileIds).toEqual(['f2'])
    expect(result.current.failed).toBe(false)
  })

  it('success case : 올리는 중에 빼면 업로드를 취소하고 미리보기 URL 을 돌려준다', async () => {
    // arrange
    const { result } = renderHook(() => useUploads(11))
    act(() => result.current.add([file('a.png', { type: 'image/png' })]))

    // act
    act(() => result.current.remove(result.current.items[0].id))
    await flush()

    // assert
    expect(calls[0].abort).toHaveBeenCalled()
    expect(urls.revoked).toHaveBeenCalledWith('blob:a.png')
    expect(result.current.items).toEqual([])
  })

  it('fail case : 20MB 를 넘는 파일은 붙이지 않고 안내한다', () => {
    // arrange
    const { result } = renderHook(() => useUploads(11))

    // act
    act(() => result.current.add([file('big.mov', { size: MAX_FILE_BYTES + 1 }), file('ok.txt', { size: MAX_FILE_BYTES })]))

    // assert
    expect(result.current.notice).toBe('20MB 이하만 올릴 수 있어요')
    expect(result.current.items.map((i) => i.name)).toEqual(['ok.txt'])
    expect(calls).toHaveLength(1)
  })

  it('fail case : 한 메시지에 10개까지만 붙인다', () => {
    // arrange
    const { result } = renderHook(() => useUploads(11))
    act(() => result.current.add(Array.from({ length: 8 }, (_, i) => file(`a${i}.txt`))))

    // act
    act(() => result.current.add([file('b1.txt'), file('b2.txt'), file('b3.txt')]))

    // assert
    expect(result.current.items).toHaveLength(10)
    expect(result.current.notice).toBe('한 메시지에 10개까지 올릴 수 있어요')
  })

  it('success case : 보낸 뒤 비우면 미리보기 URL 을 모두 돌려준다', async () => {
    // arrange
    const { result } = renderHook(() => useUploads(11))
    act(() => result.current.add([file('a.png', { type: 'image/png' }), file('b.png', { type: 'image/png' })]))
    await act(async () => {
      calls[0].succeed('f1')
      calls[1].succeed('f2')
    })

    // act
    act(() => result.current.clear())

    // assert
    expect(result.current.items).toEqual([])
    expect(urls.revoked.mock.calls).toEqual([['blob:a.png'], ['blob:b.png']])
  })

  it('success case : 화면을 떠나면 올리던 것은 취소하고 미리보기를 돌려준다', () => {
    // arrange
    const { result, unmount } = renderHook(() => useUploads(11))
    act(() => result.current.add([file('a.png', { type: 'image/png' })]))

    // act
    unmount()

    // assert
    expect(calls[0].abort).toHaveBeenCalled()
    expect(urls.revoked).toHaveBeenCalledWith('blob:a.png')
  })
})
