import { useEffect, useRef, useState } from 'react'

export const AWAY_AFTER_MS = 10 * 60 * 1000
// 입력이 있었다고 보는 것들. 탭이 숨겨지는 것은 입력이 아니다
const ACTIVITY_EVENTS = ['mousemove', 'mousedown', 'keydown', 'touchstart', 'wheel', 'scroll']

// 10분 동안 입력이 없거나 탭이 10분 동안 숨겨져 있으면 away, 다시 입력하거나 탭이 보이면 active.
// (숨겨진 탭에는 입력이 오지 않으므로 "마지막 입력 / 탭이 다시 보인 때부터 10분" 하나로 둘 다 된다)
// 바뀔 때만 {type:'presence', state} 를 보내고, (재)연결될 때마다 지금 상태를 한 번 보낸다 → 'active' | 'away'
export function useAwayState(socket) {
  const { connectionId, send } = socket
  const [state, setState] = useState('active')
  const stateRef = useRef('active')
  const sendRef = useRef(send)
  sendRef.current = send

  useEffect(() => {
    let lastActivity = Date.now()
    let timer = null

    // away 는 active 일 때 타이머로만, active 는 away 일 때 입력으로만 오므로 늘 바뀌는 경우에만 불린다
    const change = (next) => {
      stateRef.current = next
      setState(next)
      sendRef.current({ type: 'presence', state: next })
    }

    // 입력마다 타이머를 다시 거는 대신 시각만 적어 두고, 타이머가 울릴 때 남은 시간만큼 다시 건다
    const check = () => {
      const idle = Date.now() - lastActivity
      if (idle >= AWAY_AFTER_MS) change('away')
      else timer = setTimeout(check, AWAY_AFTER_MS - idle)
    }

    const activity = () => {
      lastActivity = Date.now()
      if (stateRef.current === 'away') {
        change('active')
        timer = setTimeout(check, AWAY_AFTER_MS)
      }
    }

    const visibility = () => document.visibilityState === 'visible' && activity()

    ACTIVITY_EVENTS.forEach((type) => window.addEventListener(type, activity, { passive: true, capture: true }))
    document.addEventListener('visibilitychange', visibility)
    timer = setTimeout(check, AWAY_AFTER_MS)
    return () => {
      clearTimeout(timer)
      ACTIVITY_EVENTS.forEach((type) => window.removeEventListener(type, activity, { capture: true }))
      document.removeEventListener('visibilitychange', visibility)
    }
  }, [])

  // 다시 붙은 서버는 기기 상태를 모르므로 지금 상태를 알려 준다
  useEffect(() => {
    if (connectionId > 0) sendRef.current({ type: 'presence', state: stateRef.current })
  }, [connectionId])

  return state
}
