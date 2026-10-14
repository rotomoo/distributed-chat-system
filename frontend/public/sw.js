// 웹 푸시 서비스 워커. 탭이 닫혀 있어도 notification 서버가 보낸 푸시로 알림을 띄우고,
// 알림을 누르면 열린 탭이 있으면 그 탭에서 (postMessage), 없으면 새 창으로 그 채널을 연다

// 푸시 본문 {title, body, url, tag} → showNotification 인자. 글자 본문이 와도 깨지지 않게
function notificationFromPush(data) {
  let payload = {}
  if (data) {
    try {
      payload = data.json()
    } catch {
      payload = { body: data.text() }
    }
  }
  return {
    title: payload.title || '새 메시지',
    options: { body: payload.body || '', tag: payload.tag, data: { url: payload.url || '/' } },
  }
}

// 같은 사이트의 탭이 있으면 앞으로 가져와 주소를 알려 주고, 없으면 새 창
async function openFromNotification(clientsApi, origin, url) {
  const windows = await clientsApi.matchAll({ type: 'window', includeUncontrolled: true })
  const client = windows.find((c) => new URL(c.url).origin === origin)
  if (client) {
    await client.focus()
    client.postMessage({ type: 'open-url', url })
    return
  }
  await clientsApi.openWindow(url)
}

self.addEventListener('install', () => self.skipWaiting())
self.addEventListener('activate', (event) => event.waitUntil(self.clients.claim()))

self.addEventListener('push', (event) => {
  const { title, options } = notificationFromPush(event.data)
  event.waitUntil(self.registration.showNotification(title, options))
})

self.addEventListener('notificationclick', (event) => {
  event.notification.close()
  const url = event.notification.data?.url || '/'
  event.waitUntil(openFromNotification(self.clients, self.location.origin, url))
})

// 테스트에서 꺼내 쓰려고
self.swCore = { notificationFromPush, openFromNotification }
