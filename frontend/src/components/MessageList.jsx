import { buildTimeline } from '../format.js'
import MessageRow from './MessageRow.jsx'

// 날짜 구분선, 같은 사람 5분 안 연속 메시지 묶기, 첫 안 읽은 메시지 앞 "새 메시지" 구분선
//  pickingMessageId : 반응 고르기가 열린 메시지 (동작 상자를 계속 보이게)
export default function MessageList({ messages, firstUnreadMessageId, nameOf, myUserId, pickingMessageId, actions }) {
  const timeline = buildTimeline(messages, { firstUnreadMessageId })

  return (
    <div className="message-list">
      {timeline.map((item) => {
        if (item.type === 'day') {
          return (
            <div key={item.key} className="day-divider" role="separator">
              <span className="day-label">{item.label}</span>
            </div>
          )
        }
        if (item.type === 'unread') {
          return (
            <div key={item.key} className="unread-divider" role="separator">
              <span>새 메시지</span>
            </div>
          )
        }
        return (
          <MessageRow
            key={item.key}
            message={item.message}
            continued={item.continued}
            name={nameOf(item.message.sendUserId)}
            myUserId={myUserId}
            nameOf={nameOf}
            picking={item.message.messageId === pickingMessageId}
            actions={actions}
          />
        )
      })}
    </div>
  )
}
