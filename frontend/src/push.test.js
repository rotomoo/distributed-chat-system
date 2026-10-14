import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { deleteSubscription, getVapidPublicKey, saveSubscription } from './api.js'
import { disablePush, enablePush, listenServiceWorker } from './push.js'

vi.mock('./api.js', () => ({
  getVapidPublicKey: vi.fn(),
  saveSubscription: vi.fn(),
  deleteSubscription: vi.fn(),
}))

// 서비스 워커 / 푸시 매니저 대역
function fakeServiceWorker({ existing = null } = {}) {
  const subscription = {
    endpoint: 'https://push.example/abc',
    toJSON: () => ({ endpoint: 'https://push.example/abc', keys: { p256dh: 'p', auth: 'a' } }),
    unsubscribe: vi.fn(async () => true),
  }
  const pushManager = {
    getSubscription: vi.fn(async () => existing),
    subscribe: vi.fn(async () => subscription),
  }
  const registration = { pushManager }
  const listeners = new Set()
  const serviceWorker = {
    register: vi.fn(async () => registration),
    getRegistration: vi.fn(async () => registration),
    addEventListener: vi.fn((type, fn) => listeners.add(fn)),
    removeEventListener: vi.fn((type, fn) => listeners.delete(fn)),
    emit: (data) => listeners.forEach((fn) => fn({ data })),
  }
  Object.defineProperty(navigator, 'serviceWorker', { configurable: true, value: serviceWorker })
  window.PushManager = function PushManager() {}
  return { serviceWorker, pushManager, subscription }
}

describe('[단위] push', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    getVapidPublicKey.mockResolvedValue('AQID') // [1, 2, 3]
    saveSubscription.mockResolvedValue(null)
    deleteSubscription.mockResolvedValue(null)
  })

  afterEach(() => {
    delete navigator.serviceWorker
    delete window.PushManager
  })

  it('success case : /sw.js 를 등록하고 VAPID 키로 구독해서 서버에 저장한다', async () => {
    // arrange
    const { serviceWorker, pushManager, subscription } = fakeServiceWorker()

    // act
    const result = await enablePush()

    // assert
    expect(serviceWorker.register).toHaveBeenCalledWith('/sw.js')
    const [options] = pushManager.subscribe.mock.calls[0]
    expect(options.userVisibleOnly).toBe(true)
    expect([...options.applicationServerKey]).toEqual([1, 2, 3])
    expect(saveSubscription).toHaveBeenCalledWith({ endpoint: 'https://push.example/abc', keys: { p256dh: 'p', auth: 'a' } })
    expect(result).toBe(subscription)
  })

  it('success case : 이미 구독이 있으면 새로 구독하지 않고 서버에 다시 알린다', async () => {
    // arrange
    const existing = { endpoint: 'https://push.example/old', toJSON: () => ({ endpoint: 'https://push.example/old', keys: {} }) }
    const { pushManager } = fakeServiceWorker({ existing })

    // act
    await enablePush()

    // assert
    expect(pushManager.subscribe).not.toHaveBeenCalled()
    expect(getVapidPublicKey).not.toHaveBeenCalled()
    expect(saveSubscription).toHaveBeenCalledWith({ endpoint: 'https://push.example/old', keys: {} })
  })

  it('fail case : 알림 서버가 없으면(키 조회 404) 조용히 null', async () => {
    // arrange
    const { pushManager } = fakeServiceWorker()
    getVapidPublicKey.mockRejectedValue(new Error('404'))

    // act
    const result = await enablePush()

    // assert
    expect(result).toBeNull()
    expect(pushManager.subscribe).not.toHaveBeenCalled()
    expect(saveSubscription).not.toHaveBeenCalled()
  })

  it('fail case : 서비스 워커 / 푸시를 지원하지 않는 브라우저면 아무것도 하지 않는다', async () => {
    // arrange & act
    const result = await enablePush()
    await disablePush()

    // assert
    expect(result).toBeNull()
    expect(getVapidPublicKey).not.toHaveBeenCalled()
    expect(deleteSubscription).not.toHaveBeenCalled()
  })

  it('success case : 로그아웃하면 서버에서 구독을 지우고 브라우저 구독도 끊는다', async () => {
    // arrange
    const existing = { endpoint: 'https://push.example/abc', unsubscribe: vi.fn(async () => true) }
    fakeServiceWorker({ existing })

    // act
    await disablePush()

    // assert
    expect(deleteSubscription).toHaveBeenCalledWith('https://push.example/abc')
    expect(existing.unsubscribe).toHaveBeenCalled()
  })

  it('fail case : 서버에서 지우기가 실패해도 브라우저 구독은 끊는다', async () => {
    // arrange
    const existing = { endpoint: 'https://push.example/abc', unsubscribe: vi.fn(async () => true) }
    fakeServiceWorker({ existing })
    deleteSubscription.mockRejectedValue(new Error('404'))

    // act
    await disablePush()

    // assert
    expect(existing.unsubscribe).toHaveBeenCalled()
  })

  it('fail case : 구독이 없으면 지울 것도 없다', async () => {
    // arrange
    fakeServiceWorker()

    // act
    await disablePush()

    // assert
    expect(deleteSubscription).not.toHaveBeenCalled()
  })

  it('success case : 서비스 워커가 보낸 open-url 만 받고, 해제하면 더 받지 않는다', () => {
    // arrange
    const { serviceWorker } = fakeServiceWorker()
    const onUrl = vi.fn()
    const stop = listenServiceWorker(onUrl)

    // act
    serviceWorker.emit({ type: 'open-url', url: '/?channel=11' })
    serviceWorker.emit({ type: 'other', url: '/?channel=12' })
    stop()
    serviceWorker.emit({ type: 'open-url', url: '/?channel=13' })

    // assert
    expect(onUrl.mock.calls).toEqual([['/?channel=11']])
  })
})
