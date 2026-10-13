import { fileUrl } from '../api.js'
import { fileKind, formatSize, isImage } from '../format.js'

// 메시지에 붙은 파일. 이미지는 썸네일(누르면 크게), 그 외는 내려받는 파일 카드
export default function Attachments({ attachments, onOpenImage, onImageLoad }) {
  return (
    <div className="attachments">
      {attachments.map((attachment) => {
        const url = fileUrl(attachment.fileId)
        if (isImage(attachment.contentType)) {
          return (
            <button key={attachment.fileId} type="button" className="attachment-image" onClick={() => onOpenImage(attachment)} aria-label={`${attachment.name} 크게 보기`}>
              <img src={url} alt={attachment.name} loading="lazy" onLoad={onImageLoad} />
            </button>
          )
        }
        const kind = fileKind(attachment.name, attachment.contentType)
        return (
          <a key={attachment.fileId} className="file-card" href={url} download={attachment.name}>
            <span className="file-icon" style={{ background: kind.color }}>{kind.label}</span>
            <span className="file-info">
              <span className="file-name">{attachment.name}</span>
              <span className="file-meta">{kind.label} · {formatSize(attachment.size)}</span>
            </span>
          </a>
        )
      })}
    </div>
  )
}
