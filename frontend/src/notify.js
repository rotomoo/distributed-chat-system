// 알림 문구 (브라우저 Notification 과 서비스 워커 푸시가 같은 모양이 되게)

const PREVIEW_MAX = 100

// 제목 : 채널이면 "#채널명", DM 이면 상대 이름 / 본문 : "보낸사람: 미리보기", 파일만이면 "파일 N개"
export function messageNotification({ channel, senderName, message }) {
  const text = (message.message ?? '').trim()
  const files = message.attachments?.length ?? 0
  const preview = text ? text.slice(0, PREVIEW_MAX) : `파일 ${files}개`
  return {
    title: channel.kind === 'dm' ? channel.name : `#${channel.name}`,
    body: `${senderName}: ${preview}`,
    tag: `channel-${channel.channelId}`,
  }
}

// 안 읽은 수가 있으면 "(3) 팀이름"
export function documentTitle(teamName, totalUnread) {
  const base = teamName ?? 'Distributed Chat'
  return totalUnread > 0 ? `(${totalUnread}) ${base}` : base
}

// "/?channel=12" 같은 주소에서 열 채널 id. 없거나 숫자가 아니면 null
export function channelFromUrl(url) {
  try {
    const value = new URL(url, 'http://localhost').searchParams.get('channel')
    return value && /^\d+$/.test(value) ? Number(value) : null
  } catch {
    return null
  }
}

// VAPID 공개키(base64url) → pushManager.subscribe 의 applicationServerKey
export function base64UrlToBytes(base64Url) {
  const base64 = (base64Url + '='.repeat((4 - (base64Url.length % 4)) % 4)).replace(/-/g, '+').replace(/_/g, '/')
  return Uint8Array.from(atob(base64), (ch) => ch.charCodeAt(0))
}

export const PRESENCE_LABEL = { online: '● 온라인', away: '◐ 자리 비움', offline: '○ 오프라인' }
