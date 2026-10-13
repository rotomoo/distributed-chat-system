import { useLayoutEffect, useRef, useState } from 'react'
import { fileKind, formatSize } from '../format.js'
import EmojiPicker from './EmojiPicker.jsx'

// Enter 보내기, Shift+Enter 줄바꿈. 한글 조합 중 Enter 는 조합 확정이라 보내지 않는다.
// 파일은 클립 버튼 / 붙여넣기(이미지) / 채널에 끌어다 놓기로 붙이고(uploads = useUploads), 다 올라간 것만 같이 보낸다.
//  disabled : 연결 전 (글 입력 / 보내기 잠금. 파일은 미리 올려 둘 수 있다)
//  onSend(text, fileIds) : false 를 돌려주면 입력을 비우지 않는다
export default function Composer({ placeholder, disabled, disabledHint, error, uploads, onSend }) {
  const [text, setText] = useState('')
  const [pickerAnchor, setPickerAnchor] = useState(null)
  const textareaRef = useRef(null)
  const fileInputRef = useRef(null)
  // 이모지를 넣은 뒤 커서를 이모지 뒤로 옮기려고 기억해 둔다
  const caretRef = useRef(null)

  // 줄이 늘면 입력창도 같이 늘린다 (최대 높이는 CSS)
  useLayoutEffect(() => {
    const textarea = textareaRef.current
    textarea.style.height = 'auto'
    textarea.style.height = `${textarea.scrollHeight}px`
    if (caretRef.current !== null) {
      textarea.focus()
      textarea.setSelectionRange(caretRef.current, caretRef.current)
      caretRef.current = null
    }
  }, [text])

  const { items, notice, uploading, failed, fileIds } = uploads
  let blockedHint = null
  if (uploading) blockedHint = '파일을 다 올리면 보낼 수 있어요'
  else if (failed) blockedHint = '실패한 파일을 다시 올리거나 빼 주세요'
  const canSend = !disabled && !blockedHint && (text.trim() !== '' || fileIds.length > 0)

  const submit = () => {
    if (!canSend) return
    if (onSend(text, fileIds) === false) return
    setText('')
    uploads.clear()
  }

  const handleKeyDown = (event) => {
    if (event.key !== 'Enter' || event.shiftKey || event.nativeEvent.isComposing) return
    event.preventDefault()
    submit()
  }

  // 클립보드에 파일(캡처 이미지 등)이 있으면 글 대신 파일로 붙인다
  const handlePaste = (event) => {
    const files = [...(event.clipboardData?.files ?? [])]
    if (files.length === 0) return
    event.preventDefault()
    uploads.add(files)
  }

  const handleFiles = (event) => {
    uploads.add([...event.target.files])
    event.target.value = '' // 같은 파일을 다시 고를 수 있게
  }

  // 커서 자리에 넣는다 (선택한 글이 있으면 바꾼다)
  const insertEmoji = (emoji) => {
    const textarea = textareaRef.current
    const start = textarea.selectionStart ?? text.length
    const end = textarea.selectionEnd ?? start
    caretRef.current = start + emoji.length
    setText(text.slice(0, start) + emoji + text.slice(end))
    setPickerAnchor(null)
  }

  return (
    <div className="composer-area">
      {error && <div className="composer-error" role="alert">{error}</div>}
      {notice && <div className="composer-error" role="alert">{notice}</div>}
      <div className={`composer ${disabled ? 'disabled' : ''}`}>
        {items.length > 0 && (
          <ul className="upload-chips" aria-label="올린 파일">
            {items.map((item) => (
              <UploadChip key={item.id} item={item} onRemove={() => uploads.remove(item.id)} onRetry={() => uploads.retry(item.id)} />
            ))}
          </ul>
        )}
        <div className="composer-row">
          <textarea
            ref={textareaRef}
            rows={1}
            value={text}
            onChange={(e) => setText(e.target.value)}
            onKeyDown={handleKeyDown}
            onPaste={handlePaste}
            placeholder={disabled ? disabledHint : placeholder}
            aria-label={placeholder}
            disabled={disabled}
          />
        </div>
        <div className="composer-toolbar">
          <button type="button" className="composer-tool" onClick={() => fileInputRef.current.click()} aria-label="파일 올리기" title="파일 올리기">
            <svg viewBox="0 0 20 20" width="18" height="18" aria-hidden="true">
              <path d="M13.5 6.5 7.8 12.2a1.5 1.5 0 0 0 2.1 2.1l6-6a3 3 0 0 0-4.2-4.2l-6.4 6.4a4.5 4.5 0 0 0 6.4 6.4l5-5" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" />
            </svg>
          </button>
          <input ref={fileInputRef} type="file" multiple hidden onChange={handleFiles} data-testid="file-input" />
          <button
            type="button"
            className="composer-tool"
            disabled={disabled}
            onClick={(e) => setPickerAnchor(pickerAnchor ? null : e.currentTarget)}
            aria-label="이모지"
            title="이모지"
          >
            😀
          </button>
          <span className="composer-spacer" />
          <button type="button" className="send-button" onClick={submit} disabled={!canSend} aria-label="보내기">
            <svg viewBox="0 0 20 20" width="16" height="16" aria-hidden="true">
              <path d="M2 17.5 18 10 2 2.5l2.2 6.4L12 10l-7.8 1.1z" fill="currentColor" />
            </svg>
          </button>
        </div>
      </div>
      <div className={`composer-hint ${blockedHint ? 'warn' : ''}`}>
        {blockedHint ?? (
          <>
            <b>Enter</b> 보내기 · <b>Shift + Enter</b> 줄바꿈
          </>
        )}
      </div>
      {pickerAnchor && <EmojiPicker anchor={pickerAnchor} onPick={insertEmoji} onClose={() => setPickerAnchor(null)} />}
    </div>
  )
}

function UploadChip({ item, onRemove, onRetry }) {
  const kind = fileKind(item.name, item.contentType)
  const percent = Math.round(item.progress * 100)
  return (
    <li className={`upload-chip ${item.status}`} aria-label={item.name}>
      {item.previewUrl ? (
        <img className="upload-thumb" src={item.previewUrl} alt="" />
      ) : (
        <span className="file-icon small" style={{ background: kind.color }}>{kind.label}</span>
      )}
      <span className="upload-info">
        <span className="upload-name">{item.name}</span>
        {item.status === 'uploading' && (
          <>
            <span className="upload-meta">올리는 중 {percent}%</span>
            <span className="upload-progress" role="progressbar" aria-valuenow={percent} aria-valuemin={0} aria-valuemax={100}>
              <span style={{ width: `${percent}%` }} />
            </span>
          </>
        )}
        {item.status === 'done' && <span className="upload-meta">{formatSize(item.size)}</span>}
        {item.status === 'error' && (
          <span className="upload-meta error">
            {item.error}{' '}
            <button type="button" className="upload-retry" onClick={onRetry}>다시</button>
          </span>
        )}
      </span>
      <button type="button" className="upload-remove" onClick={onRemove} aria-label={`${item.name} 빼기`}>✕</button>
    </li>
  )
}
