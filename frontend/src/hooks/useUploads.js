import { useCallback, useEffect, useRef, useState } from 'react'
import { UPLOAD_TOO_LARGE, uploadFile } from '../api.js'
import { isImage } from '../format.js'

export const MAX_FILE_BYTES = 20 * 1024 * 1024
export const MAX_FILES = 10
export const TOO_MANY_FILES = `한 메시지에 ${MAX_FILES}개까지 올릴 수 있어요`

let nextId = 1

// 입력창에 붙인 파일들. 붙이자마자 올리고, 진행률 / 실패 / 다시 / 빼기를 관리한다.
// 이미지 미리보기 object URL 은 빼거나 보내거나 화면을 떠날 때 돌려준다(revoke)
//  item : { id, name, size, contentType, previewUrl, status: uploading | done | error, progress(0~1), fileId, error }
export function useUploads(channelId) {
  const [items, setItems] = useState([])
  const [notice, setNotice] = useState(null)
  const itemsRef = useRef(items)
  itemsRef.current = items
  const filesRef = useRef(new Map()) // id → File (다시 올리기용)
  const abortsRef = useRef(new Map()) // id → abort

  const update = (id, patch) => setItems((prev) => prev.map((item) => (item.id === id ? { ...item, ...patch } : item)))

  const start = (id) => {
    const { promise, abort } = uploadFile(channelId, filesRef.current.get(id), (progress) => update(id, { progress }))
    abortsRef.current.set(id, abort)
    promise
      .then((data) => update(id, { status: 'done', progress: 1, fileId: data.fileId }))
      .catch((error) => error.name !== 'AbortError' && update(id, { status: 'error', error: error.message }))
      .finally(() => abortsRef.current.get(id) === abort && abortsRef.current.delete(id))
  }

  const add = (fileList) => {
    let message = null
    const accepted = []
    for (const file of fileList) {
      if (file.size > MAX_FILE_BYTES) {
        message = UPLOAD_TOO_LARGE
        continue
      }
      if (itemsRef.current.length + accepted.length >= MAX_FILES) {
        message = TOO_MANY_FILES
        break
      }
      const id = nextId++
      filesRef.current.set(id, file)
      accepted.push({
        id, name: file.name, size: file.size, contentType: file.type,
        previewUrl: isImage(file.type) ? URL.createObjectURL(file) : null,
        status: 'uploading', progress: 0, fileId: null, error: null,
      })
    }
    setNotice(message)
    if (accepted.length === 0) return
    itemsRef.current = [...itemsRef.current, ...accepted]
    setItems(itemsRef.current)
    accepted.forEach((item) => start(item.id))
  }

  const forget = (item) => {
    abortsRef.current.get(item.id)?.()
    abortsRef.current.delete(item.id)
    filesRef.current.delete(item.id)
    if (item.previewUrl) URL.revokeObjectURL(item.previewUrl)
  }

  // 올리는 중이면 취소, 다 올렸으면 이번 메시지에서만 뺀다
  const remove = (id) => {
    const item = itemsRef.current.find((i) => i.id === id)
    if (!item) return
    forget(item)
    itemsRef.current = itemsRef.current.filter((i) => i.id !== id)
    setItems(itemsRef.current)
    setNotice(null)
  }

  const retry = (id) => {
    update(id, { status: 'uploading', progress: 0, error: null })
    start(id)
  }

  // 보낸 뒤 비운다
  const clear = useCallback(() => {
    itemsRef.current.forEach(forget)
    itemsRef.current = []
    setItems([])
    setNotice(null)
  }, [])

  // 채널을 떠나면 올리던 것은 취소하고 미리보기도 돌려준다
  useEffect(() => () => itemsRef.current.forEach(forget), [])

  return {
    items,
    notice,
    add,
    remove,
    retry,
    clear,
    uploading: items.some((i) => i.status === 'uploading'),
    failed: items.some((i) => i.status === 'error'),
    fileIds: items.filter((i) => i.status === 'done').map((i) => i.fileId),
  }
}
