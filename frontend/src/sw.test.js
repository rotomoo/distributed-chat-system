import { afterAll, beforeAll, describe, expect, it, vi } from 'vitest'

// public/sw.js 는 서비스 워커 전역(self)에 기대므로, 등록되는 이벤트 핸들러를 가로채서 시험한다
const handlers = {}
let swCore

beforeAll(async () => {
  vi.spyOn(self, 'addEventListener').mockImplementation((type, handler) => {
    handlers[type] = handler
  })
  await import('../public/sw.js')
  swCore = self.swCore
})

afterAll(() => {
  vi.restoreAllMocks()
})

const pushData = (payload) => ({ json: () => payload, text: () => String(payload) })

function fakeClients(windows) {
  return { matchAll: vi.fn(async () => windows), openWindow: vi.fn(async () => {}) }
}

describe('[단위] sw.js', () => {
  it('success case : 푸시 본문의 title / body / tag / url 로 알림을 만든다', () => {
    // arrange & act
    const shown = swCore.notificationFromPush(pushData({ title: '#점심메뉴', body: '영희: 국밥?', url: '/?channel=11', tag: 'channel-11' }))

    // assert
    expect(shown).toEqual({ title: '#점심메뉴', options: { body: '영희: 국밥?', tag: 'channel-11', data: { url: '/?channel=11' } } })
  })

  it('fail case : JSON 이 아닌 글 본문이나 빈 푸시도 깨지지 않는다', () => {
    // arrange
    const textOnly = { json: () => { throw new SyntaxError('not json') }, text: () => '새 소식' }

    // act
    const fromText = swCore.notificationFromPush(textOnly)
    const empty = swCore.notificationFromPush(null)

    // assert
    expect(fromText).toEqual({ title: '새 메시지', options: { body: '새 소식', tag: undefined, data: { url: '/' } } })
    expect(empty.options.data.url).toBe('/')
  })

  it('success case : 알림을 누르면 같은 사이트 탭을 앞으로 가져와 주소를 보낸다', async () => {
    // arrange
    const other = { url: 'https://evil.example/', focus: vi.fn(), postMessage: vi.fn() }
    const mine = { url: 'http://localhost:5173/', focus: vi.fn(async () => {}), postMessage: vi.fn() }
    const clients = fakeClients([other, mine])

    // act
    await swCore.openFromNotification(clients, 'http://localhost:5173', '/?channel=11')

    // assert
    expect(mine.focus).toHaveBeenCalled()
    expect(mine.postMessage).toHaveBeenCalledWith({ type: 'open-url', url: '/?channel=11' })
    expect(other.focus).not.toHaveBeenCalled()
    expect(clients.openWindow).not.toHaveBeenCalled()
  })

  it('success case : 열린 탭이 없으면 그 주소로 새 창을 연다', async () => {
    // arrange
    const clients = fakeClients([])

    // act
    await swCore.openFromNotification(clients, 'http://localhost:5173', '/?channel=11')

    // assert
    expect(clients.openWindow).toHaveBeenCalledWith('/?channel=11')
  })

  it('success case : push 이벤트는 showNotification 을 waitUntil 로 기다린다', async () => {
    // arrange
    self.registration = { showNotification: vi.fn(async () => {}) }
    const waitUntil = vi.fn()

    // act
    handlers.push({ data: pushData({ title: '영희', body: '영희: 안녕', url: '/?channel=13', tag: 'channel-13' }), waitUntil })

    // assert
    expect(self.registration.showNotification).toHaveBeenCalledWith('영희', { body: '영희: 안녕', tag: 'channel-13', data: { url: '/?channel=13' } })
    expect(waitUntil).toHaveBeenCalledTimes(1)
  })

  it('success case : notificationclick 은 알림을 닫고 그 알림의 주소로 연다', async () => {
    // arrange
    const clients = fakeClients([])
    self.clients = clients
    const notification = { close: vi.fn(), data: { url: '/?channel=13' } }
    let waited

    // act
    handlers.notificationclick({ notification, waitUntil: (promise) => { waited = promise } })
    await waited

    // assert
    expect(notification.close).toHaveBeenCalled()
    expect(clients.openWindow).toHaveBeenCalledWith('/?channel=13')
  })
})
