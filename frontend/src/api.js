// client-api 는 { code, msg, data, success } 로 응답한다. code 0 이 성공.

async function json(response) {
  try {
    return await response.json()
  } catch {
    return {}
  }
}

export async function getMyInfo() {
  const response = await fetch('/v1/private/api/user/my-info')
  const body = await json(response)
  return response.ok && body.code === 0 ? body.data : null
}

export async function signup(account, password) {
  const response = await fetch('/v1/public/api/auth/signup', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ account, password }),
  })
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
  await fetch('/v1/private/api/user/logout', { method: 'POST', redirect: 'manual' })
}

export async function getTeams({ teamName = '', pageNumber = 1, pageSize = 10 } = {}) {
  const params = new URLSearchParams({ teamName, pageNumber, pageSize })
  const response = await fetch(`/v1/private/api/team/teams?${params}`)
  const body = await json(response)
  return body.code === 0 ? body.data : { list: [], paging: null }
}

export async function createTeam(teamName) {
  const response = await fetch('/v1/private/api/team/team', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ teamName }),
  })
  const body = await json(response)
  if (body.code !== 0) throw new Error(body.msg || '팀 등록에 실패했습니다.')
}

export function webSocketUrl() {
  const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws'
  return `${protocol}://${window.location.host}/websocket`
}
