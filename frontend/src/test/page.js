// GET /channel/{id}/messages 응답 data. hasNext : 최신 / before 페이지면 더 이전 것이, after 페이지면 더 나중 것이 있다
export const page = (list, hasNext = false) => ({ list, hasNext })
