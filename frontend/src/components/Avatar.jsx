import { avatarColor, initials } from '../format.js'

// 이름 첫 글자 + userId 로 정한 배경색. presence 를 주면 오른쪽 아래에 접속 점(초록 / 빈 원)을 붙인다
export default function Avatar({ userId, name, size = 36, presence, onClick }) {
  const style = { width: size, height: size, fontSize: Math.round(size * 0.45), background: avatarColor(userId) }
  const content = (
    <>
      {initials(name)}
      {presence !== undefined && <span className={`presence-dot ${presence ? 'online' : 'offline'}`} />}
    </>
  )
  if (onClick) {
    return (
      <button type="button" className="avatar" style={style} onClick={onClick} aria-label={`${name} 프로필`}>
        {content}
      </button>
    )
  }
  return (
    <span className="avatar" style={style} aria-hidden="true">
      {content}
    </span>
  )
}
