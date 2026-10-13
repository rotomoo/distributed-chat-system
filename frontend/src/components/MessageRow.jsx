import { formatTime } from '../format.js'
import Attachments from './Attachments.jsx'
import Avatar from './Avatar.jsx'
import Reactions from './Reactions.jsx'

// 메시지 한 줄. continued 면 앞 메시지에 이어 붙여 본문만 (시간은 마우스를 올릴 때).
// 본문 아래로 첨부 → 리액션 순. 마우스를 올리면 오른쪽 위에 동작 상자(반응 추가 …)가 뜬다
//  actions : { openProfile(userId), toggleReaction(message, emoji), openReactionPicker(message, anchor), openImage(attachment), imageLoaded() }
export default function MessageRow({ message, continued, name, myUserId, nameOf, picking, actions }) {
  const time = formatTime(message.publishDt)
  const openProfile = () => actions.openProfile(message.sendUserId)
  const attachments = message.attachments ?? []
  const reactions = message.reactions ?? []
  const openPicker = (anchor) => actions.openReactionPicker(message, anchor)

  return (
    <div className={`message-row ${continued ? 'continued' : 'first'} ${picking ? 'picking' : ''}`} data-message-id={message.messageId}>
      <div className="message-gutter">
        {continued ? (
          <span className="hover-time">{time}</span>
        ) : (
          <Avatar userId={message.sendUserId} name={name} onClick={openProfile} />
        )}
      </div>
      <div className="message-body">
        {!continued && (
          <div className="message-meta">
            <button type="button" className="sender-name" onClick={openProfile}>{name}</button>
            <span className="message-time">{time}</span>
          </div>
        )}
        {message.message && <div className="message-text">{message.message}</div>}
        {attachments.length > 0 && (
          <Attachments attachments={attachments} onOpenImage={actions.openImage} onImageLoad={actions.imageLoaded} />
        )}
        {reactions.length > 0 && (
          <Reactions
            reactions={reactions}
            myUserId={myUserId}
            nameOf={nameOf}
            onToggle={(emoji) => actions.toggleReaction(message, emoji)}
            onAdd={openPicker}
          />
        )}
      </div>
      <div className="message-actions" role="toolbar" aria-label="메시지 동작">
        <button type="button" className="message-action" onClick={(e) => openPicker(e.currentTarget)} aria-label="반응 추가" title="반응 추가">
          😀<span className="message-action-plus">+</span>
        </button>
      </div>
    </div>
  )
}
