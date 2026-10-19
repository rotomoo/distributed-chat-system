// client-api 는 { code, msg, data, success } 로 응답한다. code 0 이 성공.

const PRIVATE = '/v1/private/api'

async function json(response) {
  try {
    return await response.json()
  } catch {
    return {}
  }
}

// 실패하면 서버 메시지(msg)로 에러를 던진다
async function request(url, options, failMessage) {
  const response = await fetch(url, options)
  const body = await json(response)
  if (body.code !== 0) throw new Error(body.msg || failMessage)
  return body.data
}

const postJson = (body, method = 'POST') => ({
  method,
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify(body),
})

// 목록 조회는 실패해도 화면이 깨지지 않게 빈 배열
async function list(url) {
  try {
    return (await request(url))?.list ?? []
  } catch {
    return []
  }
}

export async function getMyInfo() {
  const response = await fetch(`${PRIVATE}/user/my-info`)
  const body = await json(response)
  return response.ok && body.code === 0 ? body.data : null
}

export async function signup(account, password) {
  const response = await fetch('/v1/public/api/auth/signup', postJson({ account, password }))
  const body = await json(response)
  if (!response.ok) throw new Error(body.msg || '회원가입에 실패했습니다.')
  return body.data
}

// 스프링 시큐리티 formLogin 이라 form-urlencoded 로 보낸다
export async function login(account, password) {
  const response = await fetch('/v1/public/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ account, password }),
  })
  if (response.status === 503) throw new Error('서버와 연결할 수 없습니다.')
  if (!response.ok) {
    const body = await json(response)
    throw new Error(body.msg || '로그인에 실패했습니다. 아이디 비밀번호를 확인해주세요.')
  }
}

// 로그아웃 성공 시 서버가 front-end.url 로 리다이렉트한다. 따라가지 않고 세션만 끊는다.
export async function logout() {
  await fetch(`${PRIVATE}/user/logout`, { method: 'POST', redirect: 'manual' })
}

// ── 팀 ────────────────────────────────────────────────

export async function getTeams({ teamName = '', pageNumber = 1, pageSize = 10 } = {}) {
  const params = new URLSearchParams({ teamName, pageNumber, pageSize })
  try {
    return (await request(`${PRIVATE}/team/teams?${params}`)) ?? { list: [], paging: null }
  } catch {
    return { list: [], paging: null }
  }
}

// → { teamId, teamName, defaultChannelId }
export function createTeam(teamName) {
  return request(`${PRIVATE}/team/team`, postJson({ teamName }), '팀 만들기에 실패했습니다.')
}

export function getMyTeams() {
  return list(`${PRIVATE}/team/teams/me`)
}

// 이미 멤버면 그대로 성공. #general 에도 같이 참여된다
export function joinTeam(teamId) {
  return request(`${PRIVATE}/team/${teamId}/join`, { method: 'POST' }, '팀 가입에 실패했습니다.')
}

// 팀 멤버 [{ userId, userName }] (이름용. 접속 상태는 getPresence)
export function getTeamMembers(teamId) {
  return list(`${PRIVATE}/team/${teamId}/members`)
}

// 팀 멤버 접속 상태 (client-api) [{ userId, status: online | away | offline }].
// 서버가 없거나 실패하면 빈 배열 → 모두 오프라인으로 보인다
export function getPresence(teamId) {
  return list(`${PRIVATE}/presence?teamId=${teamId}`)
}

// ── 채널 / DM ─────────────────────────────────────────

// 서버가 unreadCount / muted 를 안 주면 0 / false
const withUnread = (items) => items.map((item) => ({ ...item, unreadCount: item.unreadCount ?? 0, muted: item.muted === true }))

// 팀의 공개 채널 [{ channelId, channelName, joined, unreadCount }]
export async function getChannels(teamId) {
  return withUnread(await list(`${PRIVATE}/channel/channels?teamId=${teamId}`))
}

// 내 DM [{ channelId, userId, userName, unreadCount }]
export async function getDms(teamId) {
  return withUnread(await list(`${PRIVATE}/channel/dms?teamId=${teamId}`))
}

// → { channelId, channelName, joined }
export function createChannel(teamId, channelName) {
  return request(`${PRIVATE}/channel/channel?teamId=${teamId}`, postJson({ channelName }), '채널을 만들지 못했습니다.')
}

export function joinChannel(channelId) {
  return request(`${PRIVATE}/channel/${channelId}/join`, { method: 'POST' }, '채널 참여에 실패했습니다.')
}

// 있으면 그 DM, 없으면 새로 만든다 → { channelId, userId, userName }
export function openDm(teamId, userId) {
  return request(`${PRIVATE}/channel/dm?teamId=${teamId}`, postJson({ userId }), 'DM 을 열지 못했습니다.')
}

// 채널 메시지 이력 (MongoDB), 오래된 순 → { list, hasNext }
// before : 그 messageId 보다 이전 size 개 (위로 스크롤) / after : 그 뒤 size 개 (재접속 빈틈 채우기) / 둘 다 없으면 최신 size 개
// hasNext : 최신 / before 페이지면 이보다 이전 메시지가, after 페이지면 이보다 나중 메시지가 더 있다
export async function getChannelMessages(channelId, { before, after, size } = {}) {
  const params = new URLSearchParams()
  if (before) params.set('before', before)
  if (after) params.set('after', after)
  if (size) params.set('size', size)
  const data = await request(`${PRIVATE}/channel/${channelId}/messages?${params}`, undefined, '메시지를 불러오지 못했습니다.')
  return { list: data.list, hasNext: data.hasNext === true }
}

// 이 채널 알림 끄기 / 켜기 (배지는 그대로 센다)
export function setChannelMuted(channelId, muted) {
  return request(`${PRIVATE}/channel/${channelId}/notification`, postJson({ muted }, 'PUT'), '알림 설정을 바꾸지 못했어요')
}

// 여기까지 읽음. 서버는 기존 값보다 뒤일 때만 갱신한다
export function markRead(channelId, messageId) {
  return request(`${PRIVATE}/channel/${channelId}/read`, postJson({ messageId }, 'PUT'), '읽음 처리에 실패했습니다.')
}

// 안 읽은 메시지 수 / 첫 안 읽은 메시지 ("새 메시지" 구분선 위치) → { unreadCount, firstUnreadMessageId }. 읽음 처리는 하지 않는다
export function getCatchUp(channelId) {
  return request(`${PRIVATE}/channel/${channelId}/catch-up`, undefined, '안 읽은 메시지 정보를 불러오지 못했습니다.')
}

// ── 첨부파일 ───────────────────────────────────────────

export function fileUrl(fileId) {
  return `${PRIVATE}/file/${fileId}`
}

export const UPLOAD_TOO_LARGE = '20MB 이하만 올릴 수 있어요'

// 진행률을 보여 주려고 fetch 대신 XMLHttpRequest. → { promise: {fileId, name, contentType, size}, abort }
// 취소하면 name 이 AbortError 인 에러로 끝난다
export function uploadFile(channelId, file, onProgress) {
  const xhr = new XMLHttpRequest()
  const promise = new Promise((resolve, reject) => {
    xhr.upload.onprogress = (event) => event.lengthComputable && onProgress?.(event.loaded / event.total)
    xhr.onload = () => {
      let body = {}
      try {
        body = JSON.parse(xhr.responseText)
      } catch {
        // 게이트웨이 오류 페이지 등
      }
      if (xhr.status === 413) reject(new Error(UPLOAD_TOO_LARGE))
      else if (xhr.status >= 200 && xhr.status < 300 && body.code === 0) resolve(body.data)
      else reject(new Error(body.msg || '파일을 올리지 못했어요'))
    }
    xhr.onerror = () => reject(new Error('네트워크 오류로 올리지 못했어요'))
    xhr.onabort = () => reject(Object.assign(new Error('취소했어요'), { name: 'AbortError' }))
  })
  const form = new FormData()
  form.append('file', file)
  form.append('channelId', String(channelId))
  xhr.open('POST', `${PRIVATE}/file`)
  xhr.send(form)
  return { promise, abort: () => xhr.abort() }
}

// ── 웹 푸시 구독 (client-api. 보내기는 notification-consumer) ─────────────────────────

// → base64url 공개키
export async function getVapidPublicKey() {
  return (await request(`${PRIVATE}/notification/vapid-public-key`, undefined, '알림 키를 받지 못했어요')).publicKey
}

// PushSubscription.toJSON() = { endpoint, keys: { p256dh, auth } }. 같은 endpoint 면 서버가 갱신한다
export function saveSubscription(subscription) {
  return request(`${PRIVATE}/notification/subscriptions`, postJson(subscription), '알림 구독을 저장하지 못했어요')
}

export function deleteSubscription(endpoint) {
  return request(`${PRIVATE}/notification/subscriptions`, postJson({ endpoint }, 'DELETE'), '알림 구독을 지우지 못했어요')
}

export function webSocketUrl() {
  const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws'
  return `${protocol}://${window.location.host}/websocket`
}
