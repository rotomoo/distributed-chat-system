// 브라우저 WebSocket 대역. 테스트가 서버 쪽 신호(connected, message, error)를 직접 흘려 넣는다
export class FakeWebSocket {
  static CONNECTING = 0
  static OPEN = 1
  static CLOSED = 3
  static instances = []

  constructor(url) {
    this.url = url
    this.readyState = FakeWebSocket.CONNECTING
    this.sent = []
    FakeWebSocket.instances.push(this)
  }

  send(data) {
    this.sent.push(JSON.parse(data))
  }

  // 서버가 닫을 때도 쓴다 (code : 1000 정상, 1011 서버 오류, 4500 SESSION_NOT_RELIABLE)
  close(code = 1000) {
    this.readyState = FakeWebSocket.CLOSED
    this.onclose?.({ code })
  }

  // 채팅 서버 heartbeat
  ping() {
    this.receive({ type: 'ping' })
  }

  // 게이트웨이 101 → 채팅 서버 connected 신호까지
  connected() {
    this.readyState = FakeWebSocket.OPEN
    this.receive({ type: 'connected', chatServer: '172.21.0.14:33815' })
  }

  receive(payload) {
    this.onmessage?.({ data: JSON.stringify(payload) })
  }

  static last() {
    return FakeWebSocket.instances.at(-1)
  }
}
