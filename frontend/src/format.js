// 화면에 쓰는 순수 함수 모음 (시간 / 날짜 표기, 메시지 묶기, 아바타 색)

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토']
const DAY_MS = 24 * 60 * 60 * 1000
// 같은 사람이 이 시간 안에 이어서 보낸 메시지는 이름 / 아바타 없이 한 덩어리로 그린다
export const GROUP_WINDOW_MS = 5 * 60 * 1000

// 오후 12:01
export function formatTime(ms) {
  const date = new Date(ms)
  const hours = date.getHours()
  const hour12 = hours % 12 === 0 ? 12 : hours % 12
  return `${hours < 12 ? '오전' : '오후'} ${hour12}:${String(date.getMinutes()).padStart(2, '0')}`
}

function startOfDay(ms) {
  const date = new Date(ms)
  return new Date(date.getFullYear(), date.getMonth(), date.getDate()).getTime()
}

// 오늘 / 어제 / 10월 13일 월요일 / (다른 해) 2025년 10월 13일 월요일
export function formatDay(ms, now = Date.now()) {
  const day = startOfDay(ms)
  const today = startOfDay(now)
  if (day === today) return '오늘'
  // 서머타임이 있는 지역에서도 하루 차이로 보이게 반올림
  if (Math.round((today - day) / DAY_MS) === 1) return '어제'
  const date = new Date(ms)
  const label = `${date.getMonth() + 1}월 ${date.getDate()}일 ${WEEKDAYS[date.getDay()]}요일`
  return date.getFullYear() === new Date(now).getFullYear() ? label : `${date.getFullYear()}년 ${label}`
}

// 메시지 목록을 그릴 순서대로 펼친다
//  { type: 'day', key, label }          날짜 구분선
//  { type: 'unread', key }              "새 메시지" 구분선 (firstUnreadMessageId 바로 앞)
//  { type: 'message', key, message, continued }  continued = 앞 메시지에 이어 붙여 본문만 그린다
export function buildTimeline(messages, { firstUnreadMessageId = null, now = Date.now() } = {}) {
  const items = []
  let previous = null
  for (const message of messages) {
    const day = startOfDay(message.publishDt)
    const newDay = !previous || day !== startOfDay(previous.publishDt)
    if (newDay) items.push({ type: 'day', key: `day-${day}`, label: formatDay(message.publishDt, now) })
    const unread = message.messageId === firstUnreadMessageId
    if (unread) items.push({ type: 'unread', key: 'unread' })
    const continued =
      !newDay &&
      !unread &&
      previous.sendUserId === message.sendUserId &&
      message.publishDt - previous.publishDt < GROUP_WINDOW_MS
    items.push({ type: 'message', key: message.messageId, message, continued })
    previous = message
  }
  return items
}

// 슬랙 아바타 느낌의 진한 색들. userId 로 고르므로 누가 언제 봐도 같은 색이다
export const AVATAR_COLORS = [
  '#E01E5A', '#2EB67D', '#ECB22E', '#36C5F0', '#4A154B',
  '#1264A3', '#E8912D', '#9F4FA7', '#007A5A', '#BC3663',
]

export function avatarColor(userId) {
  let hash = 0
  for (const ch of String(userId)) hash = (hash * 31 + ch.charCodeAt(0)) >>> 0
  return AVATAR_COLORS[hash % AVATAR_COLORS.length]
}

// 사람 아바타 글자 : 이름 첫 글자 (영문은 대문자)
export function initials(name) {
  return (name ?? '?').trim().charAt(0).toUpperCase() || '?'
}

// 팀 아이콘 글자 : 여러 단어면 단어 앞글자 두 개 (Distributed Chat → DC), 한 단어면 앞 두 글자 (60hz → 60)
export function teamInitials(teamName) {
  const words = (teamName ?? '').trim().split(/\s+/).filter(Boolean)
  if (words.length === 0) return '?'
  const letters = words.length > 1 ? words[0][0] + words[1][0] : words[0].slice(0, 2)
  return letters.toUpperCase()
}

// 사이드바 배지 : 100 이상은 서버가 100 으로 잘라 준다
export function badgeText(count) {
  return count >= 100 ? '99+' : String(count)
}

// 1.2MB / 340KB / 12B
export function formatSize(bytes) {
  if (bytes < 1024) return `${bytes}B`
  const trim = (n) => n.toFixed(1).replace(/\.0$/, '')
  if (bytes < 1024 * 1024) return `${trim(bytes / 1024)}KB`
  return `${trim(bytes / (1024 * 1024))}MB`
}

export function isImage(contentType) {
  return (contentType ?? '').startsWith('image/')
}

const FILE_KINDS = [
  { label: 'PDF', color: '#E01E5A', test: (ext, type) => ext === 'pdf' || type === 'application/pdf' },
  { label: 'ZIP', color: '#616061', test: (ext) => ['zip', 'gz', 'tar', '7z', 'rar'].includes(ext) },
  { label: 'XLS', color: '#007A5A', test: (ext) => ['xls', 'xlsx', 'csv'].includes(ext) },
  { label: 'PPT', color: '#E8912D', test: (ext) => ['ppt', 'pptx', 'key'].includes(ext) },
  { label: 'DOC', color: '#1264A3', test: (ext) => ['doc', 'docx', 'hwp', 'hwpx', 'pages'].includes(ext) },
  { label: 'MP4', color: '#4A154B', test: (ext, type) => type.startsWith('video/') },
  { label: 'MP3', color: '#9F4FA7', test: (ext, type) => type.startsWith('audio/') },
  { label: 'IMG', color: '#36C5F0', test: (ext, type) => type.startsWith('image/') },
  { label: 'TXT', color: '#616061', test: (ext, type) => type.startsWith('text/') || ['txt', 'md', 'log', 'json'].includes(ext) },
]

// 파일 카드 아이콘 : 확장자 / 형식으로 고른 짧은 글자와 색
export function fileKind(name, contentType) {
  const ext = (name ?? '').includes('.') ? name.split('.').pop().toLowerCase() : ''
  const type = (contentType ?? '').toLowerCase()
  return FILE_KINDS.find((kind) => kind.test(ext, type)) ?? { label: 'FILE', color: '#616061' }
}
