import { useEffect } from 'react'
import { fileUrl } from '../api.js'
import { formatSize } from '../format.js'

// 이미지 크게 보기. 바깥 / ✕ / Esc 로 닫는다
export default function Lightbox({ attachment, onClose }) {
  useEffect(() => {
    const handleKey = (event) => event.key === 'Escape' && onClose()
    window.addEventListener('keydown', handleKey)
    return () => window.removeEventListener('keydown', handleKey)
  }, [onClose])

  const url = fileUrl(attachment.fileId)
  return (
    <div className="lightbox" role="dialog" aria-modal="true" aria-label={attachment.name} onClick={(e) => e.target === e.currentTarget && onClose()}>
      <header className="lightbox-bar">
        <span className="lightbox-name">{attachment.name}</span>
        <span className="lightbox-size">{formatSize(attachment.size)}</span>
        <a className="lightbox-download" href={url} download={attachment.name}>다운로드</a>
        <button type="button" className="lightbox-close" onClick={onClose} aria-label="크게 보기 닫기">✕</button>
      </header>
      <img className="lightbox-image" src={url} alt={attachment.name} />
    </div>
  )
}
