import { useEffect, useState } from 'react'
import { createTeam, getTeams, joinTeam } from '../api.js'
import { teamInitials } from '../format.js'
import Modal from './Modal.jsx'

// 팀 찾기(검색 → 가입) / 새 팀 만들기. 끝나면 onDone(teamId) 으로 그 팀을 연다
export default function TeamModal({ myTeamIds, onDone, onClose }) {
  const [tab, setTab] = useState('find')
  const [keyword, setKeyword] = useState('')
  const [pageNumber, setPageNumber] = useState(1)
  const [result, setResult] = useState({ list: [], paging: null })
  const [teamName, setTeamName] = useState('')
  const [alert, setAlert] = useState(null)

  const search = async (name, page) => {
    setResult(await getTeams({ teamName: name, pageNumber: page }))
  }

  useEffect(() => {
    search('', 1)
  }, [])

  const handleSearch = (event) => {
    event.preventDefault()
    setPageNumber(1)
    search(keyword, 1)
  }

  const goPage = (page) => {
    setPageNumber(page)
    search(keyword, page)
  }

  const join = async (team) => {
    try {
      await joinTeam(team.teamId)
      onDone(team.teamId)
    } catch (error) {
      setAlert(error.message)
    }
  }

  const create = async (event) => {
    event.preventDefault()
    const name = teamName.trim()
    if (!name) return
    try {
      const team = await createTeam(name)
      onDone(team.teamId)
    } catch (error) {
      setAlert(error.message)
    }
  }

  const switchTab = (next) => {
    setTab(next)
    setAlert(null)
  }

  const totalPageCount = result.paging?.totalPageCount ?? 1

  return (
    <Modal title="팀" onClose={onClose}>
      <div className="modal-tabs" role="tablist">
        <button type="button" role="tab" aria-selected={tab === 'find'} className={tab === 'find' ? 'active' : ''} onClick={() => switchTab('find')}>팀 찾기</button>
        <button type="button" role="tab" aria-selected={tab === 'create'} className={tab === 'create' ? 'active' : ''} onClick={() => switchTab('create')}>새 팀 만들기</button>
      </div>

      {tab === 'find' ? (
        <div className="modal-body">
          <form onSubmit={handleSearch}>
            <input className="text-input" value={keyword} onChange={(e) => setKeyword(e.target.value)} placeholder="팀 이름으로 찾기" aria-label="팀 이름으로 찾기" />
          </form>
          <ul className="pick-list">
            {result.list.map((team) => (
              <li key={team.teamId} className="pick-item">
                <span className="pick-team-icon">{teamInitials(team.teamName)}</span>
                <span className="pick-name">{team.teamName}</span>
                {myTeamIds.includes(team.teamId) ? (
                  <span className="pick-joined">참여 중</span>
                ) : (
                  <button type="button" className="btn btn-primary btn-sm" onClick={() => join(team)}>가입</button>
                )}
              </li>
            ))}
            {result.list.length === 0 && <li className="pick-empty">찾는 팀이 없어요</li>}
          </ul>
          {totalPageCount > 1 && (
            <div className="pager">
              <button type="button" className="btn btn-sm" disabled={pageNumber <= 1} onClick={() => goPage(pageNumber - 1)}>이전</button>
              <span>{pageNumber} / {totalPageCount}</span>
              <button type="button" className="btn btn-sm" disabled={pageNumber >= totalPageCount} onClick={() => goPage(pageNumber + 1)}>다음</button>
            </div>
          )}
        </div>
      ) : (
        <form className="modal-body" onSubmit={create}>
          <label className="field-label" htmlFor="new-team-name">팀 이름</label>
          <input id="new-team-name" className="text-input" value={teamName} onChange={(e) => setTeamName(e.target.value)} placeholder="예: 60hz" />
          <p className="field-help">만들면 #general 채널이 함께 생겨요.</p>
          <button type="submit" className="btn btn-primary" disabled={!teamName.trim()}>만들기</button>
        </form>
      )}

      {alert && <div className="inline-error" role="alert">{alert}</div>}
    </Modal>
  )
}
