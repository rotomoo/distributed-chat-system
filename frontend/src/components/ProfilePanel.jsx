import Avatar from './Avatar.jsx'

// 오른쪽 프로필. person = { userId, name, account, online }
export default function ProfilePanel({ person, isMe, error, onClose, onMessage }) {
  return (
    <aside className="profile-panel" aria-label="프로필">
      <header className="profile-header">
        <span>프로필</span>
        <button type="button" className="icon-button" onClick={onClose} aria-label="프로필 닫기">✕</button>
      </header>
      <div className="profile-body">
        <div className="profile-avatar">
          <Avatar userId={person.userId} name={person.name} size={180} />
        </div>
        <h2 className="profile-name">
          {person.name}
          {isMe && <span className="profile-me">(나)</span>}
        </h2>
        {person.account && <div className="profile-account">@{person.account}</div>}
        <div className={`profile-presence ${person.online ? 'online' : 'offline'}`}>
          {person.online ? '● 온라인' : '○ 오프라인'}
        </div>
        {!isMe && (
          <button type="button" className="btn btn-outline" onClick={() => onMessage(person.userId)}>
            메시지 보내기
          </button>
        )}
        {error && <div className="inline-error" role="alert">{error}</div>}
      </div>
    </aside>
  )
}
