import { useEffect, useLayoutEffect, useRef, useState } from 'react'
import { EMOJI_GROUPS, readRecentEmoji, rememberEmoji, searchEmoji } from '../emoji.js'

const WIDTH = 320
const GAP = 6

// anchor(누른 버튼) 근처에 뜨는 이모지 고르기. 아래 자리가 모자라면 위로, 오른쪽이 모자라면 왼쪽으로.
// 고르면 최근 사용에 기억하고 onPick(emoji). 바깥을 누르거나 Esc 면 onClose
export default function EmojiPicker({ anchor, onPick, onClose }) {
  const boxRef = useRef(null)
  const [query, setQuery] = useState('')
  const [recent] = useState(readRecentEmoji)
  const [position, setPosition] = useState({ top: 0, left: 0 })

  useLayoutEffect(() => {
    if (!anchor) return
    const rect = anchor.getBoundingClientRect()
    const height = boxRef.current.offsetHeight
    const below = rect.bottom + GAP + height <= window.innerHeight
    setPosition({
      top: below ? rect.bottom + GAP : Math.max(GAP, rect.top - GAP - height),
      // 버튼 왼쪽에 맞춰 열고, 오른쪽 자리가 모자라면(메시지 동작 상자) 버튼 오른쪽 끝에 맞춘다
      left: Math.max(GAP, rect.left + WIDTH <= window.innerWidth - GAP ? rect.left : rect.right - WIDTH),
    })
  }, [anchor])

  useEffect(() => {
    const handleKey = (event) => event.key === 'Escape' && onClose()
    const handleDown = (event) => {
      if (!boxRef.current.contains(event.target) && !anchor?.contains(event.target)) onClose()
    }
    window.addEventListener('keydown', handleKey)
    document.addEventListener('mousedown', handleDown)
    return () => {
      window.removeEventListener('keydown', handleKey)
      document.removeEventListener('mousedown', handleDown)
    }
  }, [anchor, onClose])

  const pick = (emoji) => {
    rememberEmoji(emoji)
    onPick(emoji)
  }

  const groups = query.trim()
    ? [{ name: '찾은 이모지', emojis: searchEmoji(query) }]
    : [
        ...(recent.length > 0 ? [{ name: '최근 사용', emojis: recent }] : []),
        ...EMOJI_GROUPS.map((group) => ({ name: group.name, emojis: group.items.map(([emoji]) => emoji) })),
      ]

  return (
    <div ref={boxRef} className="emoji-picker" role="dialog" aria-label="이모지 고르기" style={{ top: position.top, left: position.left, width: WIDTH }}>
      <input
        className="emoji-search"
        autoFocus
        value={query}
        onChange={(e) => setQuery(e.target.value)}
        placeholder="이모지 찾기 (예: 좋아요, coffee)"
        aria-label="이모지 찾기"
      />
      <div className="emoji-scroll">
        {groups.map((group) => (
          <section key={group.name} aria-label={group.name}>
            <h4 className="emoji-group-name">{group.name}</h4>
            <div className="emoji-grid">
              {group.emojis.map((emoji) => (
                <button key={emoji} type="button" className="emoji-cell" onClick={() => pick(emoji)} aria-label={emoji}>
                  {emoji}
                </button>
              ))}
            </div>
          </section>
        ))}
        {groups[0].emojis.length === 0 && <p className="emoji-empty">찾는 이모지가 없어요</p>}
      </div>
    </div>
  )
}
