import { useEffect, useRef, useState } from 'react'
import { webSocketUrl } from '../api.js'

export default function ChatRoom({ myUserId }) {
  const webSocketRef = useRef(null)
  const messagesRef = useRef(null)
  const [connected, setConnected] = useState(false)
  const [messages, setMessages] = useState([])
  const [input, setInput] = useState('')

  const connect = () => {
    const webSocket = new WebSocket(webSocketUrl())
    webSocket.onopen = () => setConnected(true)
    webSocket.onclose = () => setConnected(false)
    // 컨슈머가 채팅 서버를 통해 전달한 메시지 : {messageId, channelId, sendUserId, message, publishDt}
    // 내가 보낸 메시지도 카프카를 거쳐 돌아오므로 여기서 같이 그린다
    webSocket.onmessage = (event) => {
      const chatMessage = JSON.parse(event.data)
      setMessages((prev) => [...prev, chatMessage])
    }
    webSocketRef.current = webSocket
    setMessages([])
  }

  const disconnect = () => {
    webSocketRef.current?.close()
    webSocketRef.current = null
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

  return (
    <section className="panel chat">
      <div className="toolbar">
        <button className="btn btn-primary" onClick={connect} disabled={connected}>Connect</button>
        <button className="btn btn-danger" onClick={disconnect} disabled={!connected}>Disconnect</button>
        <span className={`status ${connected ? 'on' : ''}`}>{connected ? '연결됨' : '연결 안 됨'}</span>
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
