import { useEffect, useLayoutEffect, useRef, useState } from 'react'
import { getCatchUp, getChannelMessages, markRead } from '../api.js'
import { useUploads } from '../hooks/useUploads.js'
import { mergeMessages, replaceReactions } from '../messages.js'
import Avatar from './Avatar.jsx'
import Composer from './Composer.jsx'
import EmojiPicker from './EmojiPicker.jsx'
import Lightbox from './Lightbox.jsx'
import MessageList from './MessageList.jsx'

export const PAGE_SIZE = 20
// 재접속 빈틈 채우기 한 번에 가져오는 수 (서버 최대 100). hasNext 면 이어서 조회
export const GAP_SIZE = 100
export const MAX_PAGE_SIZE = 100
export const READ_DEBOUNCE_MS = 500
// 맨 아래에서 이만큼 안쪽이면 맨 아래를 보고 있는 것으로 본다
const BOTTOM_SLACK_PX = 60
// 구분선으로 스크롤할 때 위에 남기는 여백 (맨 위에 붙는 날짜 표시에 가리지 않게)
const DIVIDER_GAP_PX = 48
// 같은 리액션을 연달아 누르면 서버의 reaction 이벤트가 올 때까지(최대 3초) 무시한다
export const REACTION_GUARD_MS = 3000

// 처음 열 때는 안 읽은 메시지가 다 들어오게 넉넉히 (첫 안 읽은 메시지 앞 몇 개도 같이 보이게 +5)
export function firstPageSize(unreadAtOpen) {
  return Math.min(Math.max(PAGE_SIZE, unreadAtOpen + 5), MAX_PAGE_SIZE)
}

// 열린 채널 하나. 채널이 바뀌면 key 로 새로 마운트되므로 상태는 항상 이 채널 것이다.
//  channel      : { channelId, kind: 'channel' | 'dm', name, userId(DM 상대) }
//  unreadAtOpen : 열기 직전 사이드바 배지 수 (첫 페이지 크기)
//  socket       : useChatSocket 결과
export default function ChannelView({ channel, unreadAtOpen = 0, socket, myUserId, nameOf, presenceOf, onOpenProfile, onToggleSidebar }) {
  const { channelId } = channel
  const { status, connectionId, subscribe, send } = socket
  const connected = status === 'connected'

  const [messages, setMessages] = useState([])
  const [loaded, setLoaded] = useState(false)
  const [hasMore, setHasMore] = useState(false)
  const [loadingOlder, setLoadingOlder] = useState(false)
  const [error, setError] = useState(null)
  const [sendError, setSendError] = useState(null)
  // 안 읽은 수 / 첫 안 읽은 메시지. loading → ready(data) / failed. 실패해도 채널은 그대로 쓴다
  const [catchUp, setCatchUp] = useState({ state: 'loading', data: null })
  // 맨 아래(가장 최근 메시지)를 보고 있는지 : 보고 있을 때만 읽음 처리하고, 새 메시지가 오면 따라 내려간다
  const [atBottom, setAtBottom] = useState(true)
  // "새 메시지" 구분선이 화면 위로 지나갔는지(또는 아직 안 불러왔는지) : 그동안 "↑ 안 읽은 메시지" 알약을 띄운다
  const [dividerAbove, setDividerAbove] = useState(false)
  const [markedRead, setMarkedRead] = useState(false)
  const [jumpTick, setJumpTick] = useState(0)
  const uploads = useUploads(channelId)
  const [dragging, setDragging] = useState(false)
  const dragDepthRef = useRef(0)
  const [reactionPicker, setReactionPicker] = useState(null) // { message, anchor }
  const [lightbox, setLightbox] = useState(null) // 크게 보는 이미지 첨부
  // `${messageId} ${emoji}` → 보낸 시각. 서버 이벤트가 오기 전 같은 칩 연타를 막는다
  const pendingReactionsRef = useRef(new Map())

  const aliveRef = useRef(true)
  const messagesRef = useRef(messages)
  messagesRef.current = messages
  const hasMoreRef = useRef(hasMore)
  hasMoreRef.current = hasMore
  const boxRef = useRef(null)
  const loadingOlderRef = useRef(false)
  // 이전 메시지를 위에 붙일 때 보던 위치를 유지하려고 붙이기 전 "아래에서부터의 거리"를 기억한다
  const keepFromBottomRef = useRef(null)
  const atBottomRef = useRef(true)
  // 처음 열 때 한 번만 "새 메시지" 위치(없으면 맨 아래)로 맞춘다
  const positionedRef = useRef(false)
  const lastConnectionIdRef = useRef(connectionId)
  // 재연결 빈틈 채우기의 기준 : 연결이 끊겨 있는 동안 가진 가장 최근 messageId.
  // 연결된 뒤 바로 들어온 실시간 메시지를 기준으로 삼으면 끊긴 사이 메시지를 건너뛰므로, 연결돼 있는 동안은 고정한다
  const gapCursorRef = useRef(null)
  const markedRef = useRef(null)

  const firstUnreadMessageId = catchUp.data?.firstUnreadMessageId ?? null
  const unreadCount = catchUp.data?.unreadCount ?? 0
  const catchUpLoading = catchUp.state === 'loading'

  useEffect(() => {
    aliveRef.current = true
    return () => {
      aliveRef.current = false
    }
  }, [])

  // 열 때 : 최신 메시지 + 캐치업(안 읽은 수 / 첫 안 읽은 메시지)을 같이 조회
  useEffect(() => {
    let cancelled = false
    const size = firstPageSize(unreadAtOpen)
    getChannelMessages(channelId, { size })
      .then(({ list, hasNext }) => {
        if (cancelled) return
        setMessages((prev) => mergeMessages(list, prev))
        setHasMore(hasNext)
      })
      .catch((e) => !cancelled && setError(e.message))
      .finally(() => !cancelled && setLoaded(true))
    getCatchUp(channelId)
      .then((data) => !cancelled && setCatchUp({ state: 'ready', data }))
      .catch(() => !cancelled && setCatchUp({ state: 'failed', data: null }))
    return () => {
      cancelled = true
    }
    // 첫 페이지 크기(unreadAtOpen)는 열 때 한 번만 쓴다
  }, [channelId])

  // 끊겨 있는 동안 바뀌는 메시지는 HTTP 로 받은 것뿐(빈틈 없음)이라 그 최신 것을 기준으로 삼는다
  useEffect(() => {
    if (status !== 'connected') gapCursorRef.current = messages.at(-1)?.messageId ?? null
  }, [status, messages])

  // (재)연결될 때마다 끊긴 사이 메시지를 채운다. 끊기기 전 기준이 있으면 그 이후를, 없으면 최신 페이지를
  useEffect(() => {
    if (connectionId === lastConnectionIdRef.current) return
    lastConnectionIdRef.current = connectionId

    const fillGap = async () => {
      let newest = gapCursorRef.current
      if (!newest) {
        const { list, hasNext } = await getChannelMessages(channelId, { size: PAGE_SIZE })
        if (!aliveRef.current) return
        setMessages((prev) => mergeMessages(prev, list))
        setHasMore((prev) => prev || hasNext)
        return
      }
      for (;;) {
        const { list, hasNext } = await getChannelMessages(channelId, { after: newest, size: GAP_SIZE })
        if (!aliveRef.current) return
        setMessages((prev) => mergeMessages(prev, list))
        if (!hasNext || list.length === 0) return
        newest = list.at(-1).messageId
      }
    }
    fillGap().catch((e) => aliveRef.current && setError(e.message))
  }, [connectionId, channelId])

  // 실시간 : 이 채널 메시지는 붙이고(같은 messageId 는 한 번만), 리액션은 그 메시지 것을 통째로 바꾸고,
  // 서버 error 는 입력창 위에
  useEffect(
    () =>
      subscribe((event) => {
        if (event.type === 'message' && event.channelId === channelId) {
          setMessages((prev) => mergeMessages(prev, [event]))
        } else if (event.type === 'reaction' && event.channelId === channelId) {
          for (const key of pendingReactionsRef.current.keys()) {
            if (key.startsWith(`${event.messageId} `)) pendingReactionsRef.current.delete(key)
          }
          setMessages((prev) => replaceReactions(prev, event.messageId, event.reactions ?? []))
        } else if (event.type === 'error') {
          setSendError(event.message)
        }
      }),
    [subscribe, channelId],
  )

  // 읽음 처리 : 가장 최근 메시지가 화면에 보이면(맨 아래) 그 messageId 까지. 바뀔 때만, 500ms 몰아서.
  // 캐치업 응답 전에 읽음 처리하면 서버가 안 읽은 메시지를 0 으로 세어 구분선 위치를 잃으므로 응답을 기다린다
  const newestId = messages.at(-1)?.messageId
  useEffect(() => {
    if (!newestId || catchUpLoading || !atBottom || newestId === markedRef.current) return
    const timer = setTimeout(() => {
      markedRef.current = newestId
      setMarkedRead(true)
      markRead(channelId, newestId).catch(() => {})
    }, READ_DEBOUNCE_MS)
    return () => clearTimeout(timer)
  }, [channelId, newestId, catchUpLoading, atBottom])

  // 맨 위 메시지보다 이전 것을 PAGE_SIZE 개 더. 더 불러왔으면 true
  const loadOlder = async () => {
    const oldest = messagesRef.current[0]
    if (loadingOlderRef.current || !hasMoreRef.current || !oldest) return false
    loadingOlderRef.current = true
    setLoadingOlder(true)
    try {
      const { list, hasNext } = await getChannelMessages(channelId, { before: oldest.messageId, size: PAGE_SIZE })
      if (!aliveRef.current) return false
      const box = boxRef.current
      keepFromBottomRef.current = box.scrollHeight - box.scrollTop
      messagesRef.current = mergeMessages(list, messagesRef.current)
      hasMoreRef.current = hasNext
      setMessages((prev) => mergeMessages(list, prev))
      setHasMore(hasNext)
      return list.length > 0
    } catch (e) {
      if (aliveRef.current) setError(e.message)
      return false
    } finally {
      loadingOlderRef.current = false
      if (aliveRef.current) setLoadingOlder(false)
    }
  }

  // 지금 스크롤 위치로 "맨 아래를 보는지" / "구분선이 위로 지나갔는지" 를 다시 잰다
  const measure = () => {
    const box = boxRef.current
    const bottom = box.scrollHeight - box.scrollTop - box.clientHeight < BOTTOM_SLACK_PX
    atBottomRef.current = bottom
    setAtBottom(bottom)
    const divider = box.querySelector('.unread-divider')
    setDividerAbove(divider ? divider.offsetTop < box.scrollTop : firstUnreadMessageId !== null)
  }

  const handleScroll = (event) => {
    measure()
    if (event.currentTarget.scrollTop < 80) loadOlder()
  }

  // 알약을 누르면 구분선으로. 구분선이 아직 안 불러온 범위면 이전 페이지를 불러 가며 찾는다
  const jumpToUnread = async () => {
    while (!messagesRef.current.some((m) => m.messageId === firstUnreadMessageId) && hasMoreRef.current) {
      if (!(await loadOlder())) break
    }
    if (aliveRef.current) setJumpTick((tick) => tick + 1)
  }

  // 스크롤 위치 : 알약을 눌렀으면 구분선으로 / 이전 메시지를 위에 붙였으면 보던 위치 유지 /
  // 처음 열 때는 "새 메시지" 구분선으로 (없으면 맨 아래) / 그 뒤로는 맨 아래를 보고 있을 때만 따라 내려간다
  const jumpedRef = useRef(0)
  useLayoutEffect(() => {
    const box = boxRef.current
    const divider = box.querySelector('.unread-divider')
    if (jumpTick !== jumpedRef.current) {
      jumpedRef.current = jumpTick
      keepFromBottomRef.current = null
      if (divider) box.scrollTop = divider.offsetTop - DIVIDER_GAP_PX
    } else if (keepFromBottomRef.current !== null) {
      box.scrollTop = box.scrollHeight - keepFromBottomRef.current
      keepFromBottomRef.current = null
    } else if (!positionedRef.current && loaded && !catchUpLoading) {
      positionedRef.current = true
      box.scrollTop = divider ? divider.offsetTop - DIVIDER_GAP_PX : box.scrollHeight
    } else if (atBottomRef.current) {
      box.scrollTop = box.scrollHeight
    }
    measure()
  }, [messages, loaded, catchUpLoading, jumpTick])

  const handleSend = (text, fileIds) => {
    setSendError(null)
    atBottomRef.current = true
    const payload = { type: 'message', channelId, message: text.trim() }
    if (fileIds.length > 0) payload.attachments = fileIds.map((fileId) => ({ fileId }))
    return send(payload)
  }

  // 리액션 넣기 / 빼기. 화면은 서버의 reaction 이벤트로만 바꾼다 (낙관적 갱신 없음)
  const toggleReaction = (picked, emoji) => {
    // 고르기 창을 연 사이 리액션이 바뀌었을 수 있으니 지금 가진 메시지로 판단
    const message = messagesRef.current.find((m) => m.messageId === picked.messageId) ?? picked
    const key = `${message.messageId} ${emoji}`
    const sentAt = pendingReactionsRef.current.get(key)
    if (sentAt !== undefined && Date.now() - sentAt < REACTION_GUARD_MS) return
    const mine = (message.reactions ?? []).some((r) => r.emoji === emoji && r.userIds.includes(myUserId))
    const sent = send({ type: 'reaction', channelId, messageId: message.messageId, emoji, action: mine ? 'remove' : 'add' })
    if (sent) pendingReactionsRef.current.set(key, Date.now())
  }

  const rowActions = {
    openProfile: onOpenProfile,
    toggleReaction,
    openReactionPicker: (message, anchor) => setReactionPicker({ message, anchor }),
    openImage: setLightbox,
    // 이미지가 늦게 그려져 높이가 늘어도 맨 아래를 보고 있었으면 계속 맨 아래
    imageLoaded: () => {
      if (atBottomRef.current) boxRef.current.scrollTop = boxRef.current.scrollHeight
    },
  }

  // 파일을 채널에 끌어다 놓기. 안쪽 요소를 지날 때마다 enter / leave 가 오므로 깊이로 센다
  const isFileDrag = (event) => [...(event.dataTransfer?.types ?? [])].includes('Files')
  const dragHandlers = {
    onDragEnter: (event) => {
      if (!isFileDrag(event)) return
      event.preventDefault()
      dragDepthRef.current += 1
      setDragging(true)
    },
    onDragOver: (event) => isFileDrag(event) && event.preventDefault(),
    onDragLeave: (event) => {
      if (!isFileDrag(event)) return
      dragDepthRef.current = Math.max(0, dragDepthRef.current - 1)
      if (dragDepthRef.current === 0) setDragging(false)
    },
    onDrop: (event) => {
      if (!isFileDrag(event)) return
      event.preventDefault()
      dragDepthRef.current = 0
      setDragging(false)
      uploads.add([...event.dataTransfer.files])
    },
  }

  const isDm = channel.kind === 'dm'
  const target = isDm ? `${channel.name}에게` : `#${channel.name}에`
  const placeholder = `${target} 메시지 보내기`
  const showPill = unreadCount > 0 && dividerAbove && !markedRead

  return (
    <section className="channel-view" {...dragHandlers}>
      {dragging && (
        <div className="drop-overlay" role="status">
          <div className="drop-overlay-box">여기에 놓으면 {target} 올려요</div>
        </div>
      )}
      <header className="channel-header">
        <button type="button" className="icon-button sidebar-toggle" onClick={onToggleSidebar} aria-label="사이드바 열기">☰</button>
        {isDm ? (
          <button type="button" className="channel-title dm" onClick={() => onOpenProfile(channel.userId)}>
            <Avatar userId={channel.userId} name={channel.name} size={24} presence={presenceOf(channel.userId)} />
            <span className="channel-name">{channel.name}</span>
          </button>
        ) : (
          <h1 className="channel-title"># {channel.name}</h1>
        )}
      </header>

      <div className="message-pane">
        {showPill && (
          <button type="button" className="unread-pill" onClick={jumpToUnread}>
            ↑ 안 읽은 메시지 {unreadCount}개
          </button>
        )}
        <div className="message-scroll" ref={boxRef} onScroll={handleScroll}>
          {/* 메시지가 적으면 슬랙처럼 입력창 쪽(아래)에 붙인다 */}
          <div className="message-scroll-inner">
            {loadingOlder && <div className="loading-older">이전 메시지 불러오는 중…</div>}
            {loaded && !hasMore && !error && (
              <div className="channel-intro">
                {isDm ? (
                  <>
                    <Avatar userId={channel.userId} name={channel.name} size={72} />
                    <h2>{channel.name}</h2>
                    <p>{channel.name}님과 나눈 대화의 시작이에요.</p>
                  </>
                ) : (
                  <>
                    <h2># {channel.name}</h2>
                    <p>#{channel.name} 채널의 시작이에요.</p>
                  </>
                )}
              </div>
            )}
            {error && <div className="history-error" role="alert">{error}</div>}
            <MessageList
              messages={messages}
              firstUnreadMessageId={firstUnreadMessageId}
              nameOf={nameOf}
              myUserId={myUserId}
              pickingMessageId={reactionPicker?.message.messageId ?? null}
              actions={rowActions}
            />
          </div>
        </div>
      </div>

      <Composer
        placeholder={placeholder}
        disabled={!connected}
        disabledHint="연결되면 메시지를 보낼 수 있어요"
        error={sendError}
        uploads={uploads}
        onSend={handleSend}
      />

      {reactionPicker && (
        <EmojiPicker
          anchor={reactionPicker.anchor}
          onPick={(emoji) => {
            toggleReaction(reactionPicker.message, emoji)
            setReactionPicker(null)
          }}
          onClose={() => setReactionPicker(null)}
        />
      )}
      {lightbox && <Lightbox attachment={lightbox} onClose={() => setLightbox(null)} />}
    </section>
  )
}
