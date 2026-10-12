import { useEffect, useRef, useState } from 'react'
import { webSocketUrl } from '../api.js'

const STATUS_LABEL = {
  idle: '연결 안 됨',
  connecting: '연결 중…',
  open: '연결됨',
}

export default function ChatRoom({ myUserId }) {
  const webSocketRef = useRef(null)
  const messagesRef = useRef(null)
  // 같은 메시지가 두 번 오면(소켓이 둘이었던 경우 등) 한 번만 그린다
  const seenMessageIdsRef = useRef(new Set())
  const [status, setStatus] = useState('idle')
  const [messages, setMessages] = useState([])
  const [input, setInput] = useState('')

  const connect = () => {
    // 더블클릭 방지 : 이미 연결 중이거나 연결돼 있으면 새 소켓을 만들지 않는다
    const current = webSocketRef.current
    if (current && (current.readyState === WebSocket.CONNECTING || current.readyState === WebSocket.OPEN)) {
      return
    }

    const webSocket = new WebSocket(webSocketUrl())
    webSocketRef.current = webSocket
    setStatus('connecting')
    setMessages([])
    seenMessageIdsRef.current = new Set()

    // 101(onopen)만으로는 채팅 서버까지 붙었는지 알 수 없다 (게이트웨이가 먼저 응답한다).
    // 채팅 서버가 접속 정보를 기록하고 보내는 connected 신호를 받아야 연결됨으로 본다
    webSocket.onclose = () => {
      // 끊긴 소켓이 지금 소켓일 때만 상태를 바꾼다 (이전 소켓의 늦은 close 이벤트 무시)
      if (webSocketRef.current === webSocket) {
        webSocketRef.current = null
        setStatus('idle')
      }
    }
    // connected : 채팅 서버 준비 완료
    // message   : 컨슈머가 채팅 서버를 통해 전달한 메시지 {messageId, channelId, sendUserId, message, publishDt}
    //             내가 보낸 메시지도 카프카를 거쳐 돌아오므로 여기서 같이 그린다
    webSocket.onmessage = (event) => {
      const chatMessage = JSON.parse(event.data)
      if (chatMessage.type === 'connected') {
        setStatus('open')
        return
      }
      if (chatMessage.type !== 'message' || seenMessageIdsRef.current.has(chatMessage.messageId)) return
      seenMessageIdsRef.current.add(chatMessage.messageId)
      setMessages((prev) => [...prev, chatMessage])
    }
  }

  const disconnect = () => {
    webSocketRef.current?.close()
  }

  // 화면을 벗어나면(로그아웃 등) 연결을 정리한다
  useEffect(() => disconnect, [])

  useEffect(() => {
    const box = messagesRef.current
    if (box) box.scrollTop = box.scrollHeight
  }, [messages])

  const send = (event) => {
    event.preventDefault()
    const webSocket = webSocketRef.current
    if (!input.trim() || webSocket?.readyState !== WebSocket.OPEN) return
    webSocket.send(input)
    setInput('')
  }

  const connected = status === 'open'

  return (
    <section className="panel chat">
      <div className="toolbar">
        <button className="btn btn-primary" onClick={connect} disabled={status !== 'idle'}>Connect</button>
        <button className="btn btn-danger" onClick={disconnect} disabled={status === 'idle'}>Disconnect</button>
        <span className={`status ${status}`}>{STATUS_LABEL[status]}</span>
      </div>

      <div className="messages" ref={messagesRef}>
        {messages.map((m) => {
          const isSelf = m.sendUserId === myUserId
          return (
            <div key={m.messageId} className={`message-row ${isSelf ? 'self' : ''}`}>
              {!isSelf && <div className="sender">User {m.sendUserId}</div>}
              <div className="message-content">{m.message}</div>
            </div>
          )
        })}
      </div>

      <form className="composer" onSubmit={send}>
        <input
          value={input}
          onChange={(e) => setInput(e.target.value)}
          placeholder={connected ? 'Type a message...' : 'Connect 후 입력할 수 있습니다'}
          disabled={!connected}
        />
        <button className="btn btn-success" type="submit" disabled={!connected}>Send</button>
      </form>
    </section>
  )
}
