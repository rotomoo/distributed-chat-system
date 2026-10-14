import { useEffect, useState } from 'react'
import { getPresence } from '../api.js'

export const PRESENCE_POLL_MS = 15_000

// 팀 멤버 접속 상태 Map(userId → online | away | offline). 15초마다, 그리고 다시 연결될 때마다 다시 부른다.
// 조회가 실패하면 비어 있어 모두 오프라인으로 보인다
export function usePresence(teamId, connectionId) {
  const [presence, setPresence] = useState(() => new Map())

  useEffect(() => {
    setPresence(new Map())
  }, [teamId])

  useEffect(() => {
    if (!teamId) return
    let cancelled = false
    const load = () =>
      getPresence(teamId)
        .then((list) => !cancelled && setPresence(new Map(list.map((p) => [p.userId, p.status]))))
        .catch(() => !cancelled && setPresence(new Map()))
    load()
    const timer = setInterval(load, PRESENCE_POLL_MS)
    return () => {
      cancelled = true
      clearInterval(timer)
    }
  }, [teamId, connectionId])

  return presence
}
