import { useEffect } from 'react'

// 가운데 뜨는 창. 바깥을 누르거나 Esc 면 닫는다
export default function Modal({ title, onClose, children }) {
  useEffect(() => {
    const handleKey = (event) => event.key === 'Escape' && onClose()
    window.addEventListener('keydown', handleKey)
    return () => window.removeEventListener('keydown', handleKey)
  }, [onClose])

  return (
    <div className="modal-backdrop" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div className="modal" role="dialog" aria-modal="true" aria-label={title}>
        <header className="modal-header">
          <h2>{title}</h2>
          <button type="button" className="icon-button" onClick={onClose} aria-label="닫기">✕</button>
        </header>
        {children}
      </div>
    </div>
  )
}
