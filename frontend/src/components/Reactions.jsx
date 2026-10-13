// 메시지 아래 리액션 칩 "👍 3". 내가 누른 것은 강조, 누르면 넣기 /빼기, 끝의 +😀 로 새로 고르기
export default function Reactions({ reactions, myUserId, nameOf, onToggle, onAdd }) {
  return (
    <div className="reactions">
      {reactions.map(({ emoji, userIds }) => {
        const mine = userIds.includes(myUserId)
        const names = userIds.map(nameOf).join(', ')
        return (
          <button
            key={emoji}
            type="button"
            className={`reaction-chip ${mine ? 'mine' : ''}`}
            aria-pressed={mine}
            title={`${names}님이 반응했어요`}
            onClick={() => onToggle(emoji)}
          >
            <span className="reaction-emoji">{emoji}</span>
            <span className="reaction-count">{userIds.length}</span>
          </button>
        )
      })}
      <button type="button" className="reaction-chip reaction-add" onClick={(e) => onAdd(e.currentTarget)} aria-label="반응 추가">
        +😀
      </button>
    </div>
  )
}
