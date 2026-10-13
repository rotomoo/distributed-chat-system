import { useState } from 'react'
import Avatar from './Avatar.jsx'
import Modal from './Modal.jsx'

// 팀 멤버 중 한 명을 골라 DM 을 연다 (나는 빼고)
export default function DmPicker({ members, myUserId, error, onPick, onClose }) {
  const [filter, setFilter] = useState('')
  const candidates = members.filter(
    (m) => m.userId !== myUserId && m.userName.toLowerCase().includes(filter.trim().toLowerCase()),
  )

  return (
    <Modal title="새 메시지" onClose={onClose}>
      <div className="modal-body">
        <input className="text-input" autoFocus value={filter} onChange={(e) => setFilter(e.target.value)} placeholder="이름으로 찾기" aria-label="이름으로 찾기" />
        <ul className="pick-list">
          {candidates.map((member) => (
            <li key={member.userId}>
              <button type="button" className="pick-item clickable" onClick={() => onPick(member.userId)}>
                <Avatar userId={member.userId} name={member.userName} size={28} presence={member.online} />
                <span className="pick-name">{member.userName}</span>
                <span className="pick-presence">{member.online ? '온라인' : '오프라인'}</span>
              </button>
            </li>
          ))}
          {candidates.length === 0 && <li className="pick-empty">대화할 멤버가 없어요</li>}
        </ul>
        {error && <div className="inline-error" role="alert">{error}</div>}
      </div>
    </Modal>
  )
}
