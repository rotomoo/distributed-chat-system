import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import {
  createChannel, getChannels, getDms, getMyTeams, getTeamMembers, joinChannel, openDm,
} from '../api.js'
import { useChatSocket } from '../hooks/useChatSocket.js'
import ChannelView from './ChannelView.jsx'
import DmPicker from './DmPicker.jsx'
import ProfilePanel from './ProfilePanel.jsx'
import Sidebar from './Sidebar.jsx'
import TeamModal from './TeamModal.jsx'
import WorkspaceRail from './WorkspaceRail.jsx'

export const TEAM_STORAGE_KEY = 'chat.teamId'
export const PRESENCE_POLL_MS = 30_000
// 서버도 안 읽은 수를 100 에서 자른다
const UNREAD_CAP = 100

// 사파리 사생활 보호 모드 등에서는 localStorage 접근이 예외를 던진다
function readStoredTeamId() {
  try {
    const value = localStorage.getItem(TEAM_STORAGE_KEY)
    return value ? Number(value) : null
  } catch {
    return null
  }
}

function storeTeamId(teamId) {
  try {
    localStorage.setItem(TEAM_STORAGE_KEY, String(teamId))
  } catch {
    // 기억 못 해도 동작에는 지장 없다
  }
}

const setUnread = (items, channelId, update) =>
  items.map((item) => (item.channelId === channelId ? { ...item, unreadCount: update(item.unreadCount) } : item))

// 로그인 뒤 화면 전체. 팀 / 채널 목록 / 멤버(접속 여부) / 웹소켓을 들고 있고 열린 채널은 ChannelView 가 그린다
export default function Workspace({ me, onLogout }) {
  const socket = useChatSocket()
  const [teams, setTeams] = useState([])
  const [teamsLoaded, setTeamsLoaded] = useState(false)
  const [teamId, setTeamId] = useState(null)
  const [channels, setChannels] = useState([])
  const [dms, setDms] = useState([])
  const [members, setMembers] = useState([])
  // 열린 채널과, 열기 직전 배지 수 (ChannelView 가 안 읽은 메시지가 다 들어오게 첫 페이지 크기를 정한다)
  const [opened, setOpened] = useState(null) // { channelId, unreadAtOpen }
  const [profileUserId, setProfileUserId] = useState(null)
  const [profileError, setProfileError] = useState(null)
  const [modal, setModal] = useState(null) // 'team' | 'dm'
  const [dmError, setDmError] = useState(null)
  const [sidebarOpen, setSidebarOpen] = useState(false)

  // 실시간 메시지 처리는 구독 함수 안에서 최신 목록을 봐야 해서 ref 로도 들고 있는다
  const listsRef = useRef({ channels, dms, openedId: null })
  listsRef.current = { channels, dms, openedId: opened?.channelId ?? null }
  // 목록에 없는 채널 메시지가 오면 DM 목록을 한 번 다시 부른다 (상대가 새로 DM 을 연 경우).
  // 다시 불러도 없으면 다른 팀 채널이므로 또 부르지 않는다
  const checkedUnknownRef = useRef(new Set())

  const myName = me.userName ?? me.account

  // 서버에서 다시 받은 목록의 안 읽은 수를 쓰되, 지금 열린 채널은 0 (열면 로컬에서 지운 그대로. 슬랙처럼)
  const withOpenRead = (items) => setUnread(items, listsRef.current.openedId, () => 0)

  const loadTeams = useCallback(async (preferredTeamId) => {
    const list = await getMyTeams()
    setTeams(list)
    setTeamsLoaded(true)
    const wanted = preferredTeamId ?? readStoredTeamId()
    setTeamId(list.some((team) => team.teamId === wanted) ? wanted : (list[0]?.teamId ?? null))
  }, [])

  useEffect(() => {
    loadTeams()
  }, [loadTeams])

  // 채널을 열면 그 채널 배지를 지운다
  const openChannel = useCallback((channelId, unreadAtOpen = 0) => {
    setOpened({ channelId, unreadAtOpen })
    setChannels((items) => setUnread(items, channelId, () => 0))
    setDms((items) => setUnread(items, channelId, () => 0))
    setSidebarOpen(false)
  }, [])

  // 팀을 바꾸면 채널 / DM / 멤버를 새로 부르고 첫 번째 참여 채널을 연다. 멤버(접속 여부)는 30초마다 다시
  useEffect(() => {
    if (!teamId) return
    storeTeamId(teamId)
    let cancelled = false
    setChannels([])
    setDms([])
    setMembers([])
    setOpened(null)
    setProfileUserId(null)
    checkedUnknownRef.current = new Set()

    Promise.all([getChannels(teamId), getDms(teamId)]).then(([channelList, dmList]) => {
      if (cancelled) return
      setChannels(channelList)
      setDms(dmList)
      const first = channelList.find((c) => c.joined) ?? dmList[0]
      if (first) openChannel(first.channelId, first.unreadCount)
    })

    const loadMembers = () => getTeamMembers(teamId).then((list) => !cancelled && setMembers(list))
    loadMembers()
    const timer = setInterval(loadMembers, PRESENCE_POLL_MS)
    return () => {
      cancelled = true
      clearInterval(timer)
    }
  }, [teamId, openChannel])

  // 열려 있지 않은 채널에 남이 보낸 메시지가 오면 그 채널 배지를 올린다
  useEffect(
    () =>
      socket.subscribe((event) => {
        if (event.type !== 'message' || event.sendUserId === me.userId) return
        const { channels: channelList, dms: dmList, openedId } = listsRef.current
        if (event.channelId === openedId) return
        const bump = (items) => setUnread(items, event.channelId, (count) => Math.min(count + 1, UNREAD_CAP))
        if (channelList.some((c) => c.channelId === event.channelId && c.joined)) {
          setChannels(bump)
        } else if (dmList.some((dm) => dm.channelId === event.channelId)) {
          setDms(bump)
        } else if (teamId && !checkedUnknownRef.current.has(event.channelId)) {
          checkedUnknownRef.current.add(event.channelId)
          getDms(teamId).then((list) => setDms(withOpenRead(list)))
        }
      }),
    [socket.subscribe, me.userId, teamId],
  )

  // 다시 연결되면(첫 연결 말고) 끊긴 동안 닫힌 채널에 온 메시지 수를 서버에서 다시 받는다
  const lastConnectionIdRef = useRef(socket.connectionId)
  useEffect(() => {
    const previous = lastConnectionIdRef.current
    lastConnectionIdRef.current = socket.connectionId
    if (previous === 0 || socket.connectionId === previous || !teamId) return
    let cancelled = false
    Promise.all([getChannels(teamId), getDms(teamId)]).then(([channelList, dmList]) => {
      if (cancelled) return
      setChannels(withOpenRead(channelList))
      setDms(withOpenRead(dmList))
    })
    return () => {
      cancelled = true
    }
  }, [socket.connectionId, teamId])

  const memberById = useMemo(() => new Map(members.map((m) => [m.userId, m])), [members])

  const nameOf = useCallback(
    (userId) => {
      if (userId === me.userId) return myName
      return memberById.get(userId)?.userName ?? dms.find((dm) => dm.userId === userId)?.userName ?? `User ${userId}`
    },
    [memberById, dms, me.userId, myName],
  )

  const presenceOf = useCallback(
    (userId) => (userId === me.userId ? socket.status === 'connected' : memberById.get(userId)?.online === true),
    [memberById, me.userId, socket.status],
  )

  const select = (channelId) => {
    const item = [...channels, ...dms].find((c) => c.channelId === channelId)
    openChannel(channelId, item?.unreadCount ?? 0)
  }

  const handleCreateChannel = async (name) => {
    const created = await createChannel(teamId, name)
    setChannels(await getChannels(teamId))
    openChannel(created.channelId)
  }

  const handleJoinChannel = async (channel) => {
    await joinChannel(channel.channelId)
    setChannels(await getChannels(teamId))
    openChannel(channel.channelId)
  }

  // 있으면 그 DM, 없으면 만들어서 연다
  const openDmWith = async (userId) => {
    const dm = await openDm(teamId, userId)
    const dmList = await getDms(teamId)
    setDms(dmList)
    openChannel(dm.channelId, dmList.find((d) => d.channelId === dm.channelId)?.unreadCount ?? 0)
  }

  const messageFromProfile = async (userId) => {
    setProfileError(null)
    try {
      await openDmWith(userId)
    } catch (error) {
      setProfileError(error.message)
    }
  }

  const pickDm = async (userId) => {
    setDmError(null)
    try {
      await openDmWith(userId)
      setModal(null)
    } catch (error) {
      setDmError(error.message)
    }
  }

  const openProfile = (userId) => {
    setProfileError(null)
    setProfileUserId(userId)
  }

  const closeModal = useCallback(() => setModal(null), [])

  const handleTeamDone = (newTeamId) => {
    setModal(null)
    loadTeams(newTeamId)
  }

  const team = teams.find((t) => t.teamId === teamId)
  const openedChannel = opened && channels.find((c) => c.channelId === opened.channelId && c.joined)
  const openedDm = opened && dms.find((d) => d.channelId === opened.channelId)
  const view = openedChannel
    ? { channelId: openedChannel.channelId, kind: 'channel', name: openedChannel.channelName }
    : openedDm
      ? { channelId: openedDm.channelId, kind: 'dm', name: openedDm.userName, userId: openedDm.userId }
      : null

  const profile = profileUserId !== null && {
    userId: profileUserId,
    name: nameOf(profileUserId),
    account: profileUserId === me.userId ? me.account : undefined,
    online: presenceOf(profileUserId),
  }

  return (
    <div className="app-shell">
      {socket.status === 'reconnecting' && (
        <div className="connection-banner" role="status">연결이 끊겼어요. 다시 연결하는 중…</div>
      )}
      <div className={`workspace ${sidebarOpen ? 'sidebar-open' : ''}`}>
        <WorkspaceRail teams={teams} teamId={teamId} onSelect={setTeamId} onAdd={() => setModal('team')} />
        <Sidebar
          team={team}
          channels={channels}
          dms={dms}
          selectedId={opened?.channelId ?? null}
          me={me}
          status={socket.status}
          presenceOf={presenceOf}
          onSelect={select}
          onCreateChannel={handleCreateChannel}
          onJoinChannel={handleJoinChannel}
          onOpenDmPicker={() => {
            setDmError(null)
            setModal('dm')
          }}
          onOpenTeamModal={() => setModal('team')}
          onOpenProfile={openProfile}
          onLogout={onLogout}
        />
        <div className="sidebar-scrim" onClick={() => setSidebarOpen(false)} />

        <main className="main-pane">
          {view ? (
            <ChannelView
              key={view.channelId}
              channel={view}
              unreadAtOpen={opened.unreadAtOpen}
              socket={socket}
              myUserId={me.userId}
              nameOf={nameOf}
              presenceOf={presenceOf}
              onOpenProfile={openProfile}
              onToggleSidebar={() => setSidebarOpen(true)}
            />
          ) : (
            <div className="empty-main">
              <button type="button" className="icon-button sidebar-toggle" onClick={() => setSidebarOpen(true)} aria-label="사이드바 열기">☰</button>
              {teamsLoaded && teams.length === 0 ? (
                <>
                  <h2>아직 참여한 팀이 없어요</h2>
                  <p>팀을 찾아 가입하거나 새 팀을 만들어 보세요.</p>
                  <button type="button" className="btn btn-primary" onClick={() => setModal('team')}>팀 찾기 / 만들기</button>
                </>
              ) : (
                teamsLoaded && <p>왼쪽에서 채널을 골라 대화를 시작하세요.</p>
              )}
            </div>
          )}
        </main>

        {profile && (
          <ProfilePanel
            person={profile}
            isMe={profileUserId === me.userId}
            error={profileError}
            onClose={() => setProfileUserId(null)}
            onMessage={messageFromProfile}
          />
        )}
      </div>

      {modal === 'team' && (
        <TeamModal myTeamIds={teams.map((t) => t.teamId)} onDone={handleTeamDone} onClose={closeModal} />
      )}
      {modal === 'dm' && (
        <DmPicker members={members} myUserId={me.userId} error={dmError} onPick={pickDm} onClose={closeModal} />
      )}
    </div>
  )
}
