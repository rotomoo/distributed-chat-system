import { useEffect, useRef, useState } from 'react'
import { messageNotification } from '../notify.js'
import { enablePush } from '../push.js'

export const NOTIFY_BAR_DISMISSED_KEY = 'chat.notifyBarDismissed'

const notificationApi = () => (typeof window !== 'undefined' ? window.Notification : undefined)

function readDismissed() {
  try {
    return localStorage.getItem(NOTIFY_BAR_DISMISSED_KEY) === '1'
  } catch {
    return false
  }
}

// 탭이 열려 있을 때의 알림 (서버 푸시는 오프라인인 사람에게만 가므로 겹치지 않는다).
// 남이 보낸 메시지가 열려 있지 않은 채널(또는 탭이 숨겨진 상태)에 오면, 알림을 끈 채널이 아니면 브라우저 알림.
// 누르면 탭을 앞으로 가져와 그 채널을 연다.
//  findChannel(channelId) → { channelId, kind, name, muted } | null  (지금 팀 목록에서)
//  openedId() → 열린 채널 id
export function useMessageNotifications({ subscribe, myUserId, findChannel, openedId, nameOf, onOpen }) {
  const api = notificationApi()
  const [permission, setPermission] = useState(api ? api.permission : 'unsupported')
  const [dismissed, setDismissed] = useState(readDismissed)
  const latest = useRef({})
  latest.current = { findChannel, openedId, nameOf, onOpen }

  // 이미 허락했으면 웹 푸시 구독을 서버에 다시 알린다 (기기 / 키가 바뀌었을 수 있다)
  useEffect(() => {
    if (permission === 'granted') enablePush()
  }, [permission])

  useEffect(
    () =>
      subscribe((event) => {
        const Notification = notificationApi()
        if (event.type !== 'message' || event.sendUserId === myUserId || Notification?.permission !== 'granted') return
        const { findChannel: find, openedId: opened, nameOf: name, onOpen: open } = latest.current
        const channel = find(event.channelId)
        if (!channel || channel.muted) return
        if (event.channelId === opened() && document.visibilityState === 'visible') return
        const { title, body, tag } = messageNotification({ channel, senderName: name(event.sendUserId), message: event })
        const notification = new Notification(title, { body, tag })
        notification.onclick = () => {
          window.focus()
          open(event.channelId)
          notification.close()
        }
      }),
    [subscribe, myUserId],
  )

  const enable = async () => {
    const result = await notificationApi().requestPermission()
    setPermission(result)
  }

  const dismiss = () => {
    setDismissed(true)
    try {
      localStorage.setItem(NOTIFY_BAR_DISMISSED_KEY, '1')
    } catch {
      // 이번 화면에서만 숨긴다
    }
  }

  return { showBar: permission === 'default' && !dismissed, enable, dismiss }
}
