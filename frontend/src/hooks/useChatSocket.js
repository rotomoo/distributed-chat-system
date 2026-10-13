import { useCallback, useEffect, useRef, useState } from 'react'
import { webSocketUrl } from '../api.js'

// 끊기면 1s, 2s, 4s, 8s, 10s, 10s … 뒤에 다시 붙는다. connected 를 받으면 처음(1s)부터
export function reconnectDelay(attempt) {
  return Math.min(1000 * 2 ** attempt, 10_000)
}

// 연결된 뒤 이만큼 아무것도 받지 못하면 죽은 연결로 보고 끊고 다시 붙는다.
// 서버는 25초마다 ping 을 보내고 60초 넘게 조용한 세션을 닫는다
export const SILENCE_LIMIT_MS = 60_000

// 로그인한 동안 채팅 서버와 웹소켓 하나를 유지한다 (버튼 없이 자동 연결 / 재연결).
//  status       : connecting(첫 연결 중) / connected / reconnecting(끊겨서 다시 붙는 중)
//  connectionId : connected 를 받을 때마다 1 씩 오른다. 화면은 이 값이 바뀌면 끊긴 사이 메시지를 채운다
//  subscribe    : 서버가 보낸 message / error 를 받을 함수를 등록하고, 해제 함수를 돌려준다
//  send         : 연결됐을 때만 보내고 true
// ping 은 여기서 바로 pong 으로 답하고 화면으로 넘기지 않는다
export function useChatSocket() {
  const [status, setStatus] = useState('connecting')
  const [connectionId, setConnectionId] = useState(0)
  const socketRef = useRef(null)
  const listenersRef = useRef(new Set())

  useEffect(() => {
    let stopped = false
    let attempt = 0
    let timer = null
    let watchdog = null

    // 끊김 (서버가 닫음 / 4500 SESSION_NOT_RELIABLE / 1011 / 네트워크 / 조용함 감시) → 같은 길로 다시 붙는다
    const reconnectLater = () => {
      clearTimeout(watchdog)
      socketRef.current = null
      setStatus('reconnecting')
      timer = setTimeout(open, reconnectDelay(attempt))
      attempt += 1
    }

    const open = () => {
      const socket = new WebSocket(webSocketUrl())
      socketRef.current = socket
      let connected = false

      // 받은 게 있을 때마다 다시 잰다. 죽은 연결은 close 이벤트가 한참 늦게 오므로 기다리지 않고 바로 다시 붙는다
      const watch = () => {
        clearTimeout(watchdog)
        watchdog = setTimeout(() => {
          socket.onclose = null
          socket.onmessage = null
          socket.close()
          reconnectLater()
        }, SILENCE_LIMIT_MS)
      }

      // 101(onopen)만으로는 채팅 서버까지 붙었는지 알 수 없다 (게이트웨이가 먼저 응답한다).
      // 채팅 서버가 접속 정보를 기록하고 보내는 connected 신호를 받아야 연결됨으로 본다
      socket.onmessage = (event) => {
        if (connected) watch()
        let payload
        try {
          payload = JSON.parse(event.data)
        } catch {
          return
        }
        if (payload.type === 'connected') {
          attempt = 0
          connected = true
          watch()
          setStatus('connected')
          setConnectionId((id) => id + 1)
          return
        }
        if (payload.type === 'ping') {
          socket.send(JSON.stringify({ type: 'pong' }))
          return
        }
        listenersRef.current.forEach((listener) => listener(payload))
      }

      socket.onclose = () => {
        if (stopped) return
        reconnectLater()
      }
    }

    open()

    // 로그아웃 / 화면 종료 : 다시 붙지 않게 막고 닫는다
    return () => {
      stopped = true
      clearTimeout(timer)
      clearTimeout(watchdog)
      socketRef.current?.close()
      socketRef.current = null
    }
  }, [])

  const subscribe = useCallback((listener) => {
    listenersRef.current.add(listener)
    return () => listenersRef.current.delete(listener)
  }, [])

  const send = useCallback((payload) => {
    const socket = socketRef.current
    if (!socket || socket.readyState !== WebSocket.OPEN) return false
    socket.send(JSON.stringify(payload))
    return true
  }, [])

  return { status, connectionId, subscribe, send }
}
