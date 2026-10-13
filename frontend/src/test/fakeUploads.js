import { vi } from 'vitest'

// api.uploadFile 대역. 부를 때마다 calls 에 쌓이고, 테스트가 진행률 / 성공 / 실패를 정한다
export function controlUploads(uploadFileMock) {
  const calls = []
  uploadFileMock.mockImplementation((channelId, file, onProgress) => {
    let resolve
    let reject
    const promise = new Promise((res, rej) => {
      resolve = res
      reject = rej
    })
    const call = {
      channelId, file, onProgress, promise,
      succeed: (fileId) => resolve({ fileId, name: file.name, contentType: file.type, size: file.size }),
      fail: (message) => reject(new Error(message)),
      abort: vi.fn(() => reject(Object.assign(new Error('취소했어요'), { name: 'AbortError' }))),
    }
    calls.push(call)
    return { promise, abort: call.abort }
  })
  return calls
}

// jsdom 에는 object URL 이 없다
export function stubObjectUrls() {
  const created = vi.fn((file) => `blob:${file.name}`)
  const revoked = vi.fn()
  URL.createObjectURL = created
  URL.revokeObjectURL = revoked
  return { created, revoked }
}

export function file(name, { type = '', size } = {}) {
  const f = new File(['x'], name, { type })
  if (size !== undefined) Object.defineProperty(f, 'size', { value: size })
  return f
}
