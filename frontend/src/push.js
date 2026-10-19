import { deleteSubscription, getVapidPublicKey, saveSubscription } from './api.js'
import { base64UrlToBytes } from './notify.js'

// 웹 푸시 : 탭을 닫아도 알림이 오게 서비스 워커(/sw.js)로 구독한다.
// 브라우저가 지원하지 않거나 서버가 아직 공개키를 못 주면(notification-consumer 가 키를 만들기 전) 조용히 넘어간다 (탭 안 알림은 그대로 동작)

function supported() {
  return typeof navigator !== 'undefined' && 'serviceWorker' in navigator && typeof window !== 'undefined' && 'PushManager' in window
}

// 알림을 허락받은 뒤 부른다. 이미 구독이 있으면 그대로 서버에 다시 알린다(서버는 같은 endpoint 면 갱신) → 구독 또는 null
export async function enablePush() {
  if (!supported()) return null
  try {
    const registration = await navigator.serviceWorker.register('/sw.js')
    const existing = await registration.pushManager.getSubscription()
    const subscription =
      existing ??
      (await registration.pushManager.subscribe({
        userVisibleOnly: true,
        applicationServerKey: base64UrlToBytes(await getVapidPublicKey()),
      }))
    await saveSubscription(subscription.toJSON())
    return subscription
  } catch {
    return null
  }
}

// 로그아웃 : 서버에서 지우고(세션이 살아 있을 때) 브라우저 구독도 끊는다
export async function disablePush() {
  if (!supported()) return
  try {
    const registration = await navigator.serviceWorker.getRegistration()
    const subscription = await registration?.pushManager.getSubscription()
    if (!subscription) return
    await deleteSubscription(subscription.endpoint).catch(() => {})
    await subscription.unsubscribe()
  } catch {
    // 지우지 못해도 서버가 404 / 410 이 나는 구독은 스스로 지운다
  }
}

// 서비스 워커가 알림 클릭으로 보낸 주소 { type: 'open-url', url } 을 받는다. 해제 함수를 돌려준다
export function listenServiceWorker(onUrl) {
  if (typeof navigator === 'undefined' || !('serviceWorker' in navigator)) return () => {}
  const handle = (event) => event.data?.type === 'open-url' && onUrl(event.data.url)
  navigator.serviceWorker.addEventListener('message', handle)
  return () => navigator.serviceWorker.removeEventListener('message', handle)
}
