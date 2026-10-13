// 이전 메시지(HTTP)와 실시간 메시지(웹소켓)를 합친다. 같은 messageId 는 한 번만 그린다
// (접속 후 이전 메시지를 다시 조회하면 겹치는 메시지가 생긴다). messageId(ObjectId)는 시간순이라 문자열로 정렬된다
export function mergeMessages(a, b) {
  const byId = new Map([...a, ...b].map((m) => [m.messageId, m]))
  return [...byId.values()].sort((x, y) => (x.messageId < y.messageId ? -1 : 1))
}

// 리액션 이벤트는 그 메시지의 리액션 전체를 준다. 통째로 바꾸고, 지금 불러온 범위에 없으면 그대로 둔다
export function replaceReactions(messages, messageId, reactions) {
  if (!messages.some((m) => m.messageId === messageId)) return messages
  return messages.map((m) => (m.messageId === messageId ? { ...m, reactions } : m))
}
