import { useState } from 'react'
import { badgeText } from '../format.js'
import Avatar from './Avatar.jsx'

const STATUS_LABEL = { connected: '연결됨', connecting: '연결 중', reconnecting: '다시 연결 중' }

const matches = (name, filter) => name.toLowerCase().includes(filter.trim().toLowerCase())

// 팀 이름(맨 위) / 대화 찾기 / 채널 / 채널 둘러보기 / 다이렉트 메시지 / 내 프로필
export default function Sidebar({
  team, channels, dms, selectedId, me, status, presenceOf,
  onSelect, onCreateChannel, onJoinChannel, onOpenDmPicker, onOpenTeamModal, onOpenProfile, onLogout,
}) {
  const [filter, setFilter] = useState('')
  const [creating, setCreating] = useState(false)
  const [newName, setNewName] = useState('')
  const [alert, setAlert] = useState(null)
  const [browseOpen, setBrowseOpen] = useState(false)
  const [menuOpen, setMenuOpen] = useState(false)

  const joined = channels.filter((c) => c.joined && matches(c.channelName, filter))
  const browsable = channels.filter((c) => !c.joined && matches(c.channelName, filter))
  const shownDms = dms.filter((dm) => matches(dm.userName ?? '', filter))

  const submitNewChannel = async (event) => {
    event.preventDefault()
    const name = newName.trim()
    if (!name) return
    try {
      await onCreateChannel(name)
      setNewName('')
      setCreating(false)
      setAlert(null)
    } catch (error) {
      setAlert(error.message)
    }
  }

  const join = async (channel) => {
    try {
      await onJoinChannel(channel)
      setAlert(null)
    } catch (error) {
      setAlert(error.message)
    }
  }

  const myName = me.userName ?? me.account

  return (
    <aside className="sidebar">
      <button type="button" className="team-header" onClick={onOpenTeamModal}>
        <span className="team-name">{team?.teamName ?? '팀 없음'}</span>
        <span className="caret">▾</span>
      </button>

      <div className="sidebar-scroll">
        <div className="sidebar-filter">
          <input value={filter} onChange={(e) => setFilter(e.target.value)} placeholder="대화 찾기" aria-label="대화 찾기" />
        </div>

        {team && (
          <>
            <div className="section-header">
              <span>채널</span>
              <button type="button" className="section-add" onClick={() => setCreating((v) => !v)} aria-label="채널 만들기">+</button>
            </div>
            {creating && (
              <form className="new-channel" onSubmit={submitNewChannel}>
                <input
                  autoFocus
                  value={newName}
                  onChange={(e) => setNewName(e.target.value)}
                  onKeyDown={(e) => e.key === 'Escape' && setCreating(false)}
                  placeholder="새 채널 이름"
                  aria-label="새 채널 이름"
                />
              </form>
            )}
            <ul className="nav-list">
              {joined.map((channel) => (
                <li key={channel.channelId}>
                  <button
                    type="button"
                    className={`nav-item ${channel.channelId === selectedId ? 'active' : ''} ${channel.unreadCount > 0 ? 'unread' : ''} ${channel.muted ? 'muted' : ''}`}
                    onClick={() => onSelect(channel.channelId)}
                  >
                    <span className="nav-icon">#</span>
                    <span className="nav-label">{channel.channelName}</span>
                    {channel.muted && <span className="muted-icon" aria-label="알림 꺼짐">🔕</span>}
                    {channel.unreadCount > 0 && <span className="badge">{badgeText(channel.unreadCount)}</span>}
                  </button>
                </li>
              ))}
            </ul>

            {browsable.length > 0 && (
              <>
                <button type="button" className="browse-toggle" onClick={() => setBrowseOpen((v) => !v)} aria-expanded={browseOpen}>
                  <span className="nav-icon">{browseOpen ? '▾' : '▸'}</span>
                  채널 둘러보기 <span className="muted-count">{browsable.length}</span>
                </button>
                {browseOpen && (
                  <ul className="nav-list browse-list">
                    {browsable.map((channel) => (
                      <li key={channel.channelId} className="browse-item">
                        <span className="nav-icon">#</span>
                        <span className="nav-label">{channel.channelName}</span>
                        <button type="button" className="join-button" onClick={() => join(channel)}>참여</button>
                      </li>
                    ))}
                  </ul>
                )}
              </>
            )}

            <div className="section-header">
              <span>다이렉트 메시지</span>
              <button type="button" className="section-add" onClick={onOpenDmPicker} aria-label="새 메시지">+</button>
            </div>
            <ul className="nav-list">
              {shownDms.map((dm) => (
                <li key={dm.channelId}>
                  <button
                    type="button"
                    className={`nav-item dm ${dm.channelId === selectedId ? 'active' : ''} ${dm.unreadCount > 0 ? 'unread' : ''} ${dm.muted ? 'muted' : ''}`}
                    onClick={() => onSelect(dm.channelId)}
                  >
                    <Avatar userId={dm.userId} name={dm.userName} size={20} presence={presenceOf(dm.userId)} />
                    <span className="nav-label">{dm.userName}</span>
                    {dm.muted && <span className="muted-icon" aria-label="알림 꺼짐">🔕</span>}
                    {dm.unreadCount > 0 && <span className="badge">{badgeText(dm.unreadCount)}</span>}
                  </button>
                </li>
              ))}
            </ul>
          </>
        )}

        {alert && <div className="sidebar-alert" role="alert">{alert}</div>}
      </div>

      <div className="me-row">
        <button type="button" className="me-button" onClick={() => setMenuOpen((v) => !v)} aria-haspopup="menu" aria-expanded={menuOpen}>
          <Avatar userId={me.userId} name={myName} size={28} />
          <span className="me-name">{myName}</span>
          <span className={`status-dot ${status}`} title={STATUS_LABEL[status]} aria-label={STATUS_LABEL[status]} />
        </button>
        {menuOpen && (
          <div className="me-menu" role="menu">
            <button type="button" role="menuitem" onClick={() => { setMenuOpen(false); onOpenProfile(me.userId) }}>프로필 보기</button>
            <button type="button" role="menuitem" className="danger" onClick={onLogout}>로그아웃</button>
          </div>
        )}
      </div>
    </aside>
  )
}
