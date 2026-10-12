import { useEffect, useState } from 'react'
import { createTeam, getTeams } from '../api.js'

export default function TeamPanel() {
  const [teamName, setTeamName] = useState('')
  const [pageNumber, setPageNumber] = useState(1)
  const [teams, setTeams] = useState({ list: [], paging: null })
  const [alert, setAlert] = useState(null) // { text, error }

  // 입력창이 등록과 검색을 겸한다
  const load = async (keyword = teamName, page = pageNumber) => {
    try {
      setTeams(await getTeams({ teamName: keyword, pageNumber: page }))
    } catch (error) {
      console.error('팀 목록 조회 오류:', error)
    }
  }

  useEffect(() => {
    load(teamName, pageNumber)
  }, [teamName, pageNumber])

  const handleSubmit = async (event) => {
    event.preventDefault()
    try {
      await createTeam(teamName)
      setAlert({ text: `팀 등록 완료: ${teamName}` })
      setTeamName('')
      setPageNumber(1)
    } catch (error) {
      setAlert({ text: `에러: ${error.message}`, error: true })
    }
  }

  const totalPageCount = teams.paging?.totalPageCount ?? 0

  return (
    <section className="panel teams">
      <h3>👥 Teams</h3>
      <form className="inline-form" onSubmit={handleSubmit}>
        <input
          value={teamName}
          onChange={(e) => {
            setTeamName(e.target.value)
            setPageNumber(1)
          }}
          placeholder="팀 이름 입력"
          required
        />
        <button type="submit" className="btn btn-info">팀 등록</button>
      </form>
      {alert && <div className={`message ${alert.error ? 'error' : 'success'}`}>{alert.text}</div>}

      <ul className="team-list">
        {teams.list.map((team) => (
          <li key={team.teamId}>{team.teamName} <span className="muted">(ID: {team.teamId})</span></li>
        ))}
      </ul>

      {totalPageCount > 1 && (
        <div className="pagination">
          {Array.from({ length: totalPageCount }, (_, i) => i + 1).map((page) => (
            <button
              key={page}
              className={`btn btn-sm ${page === pageNumber ? 'btn-primary' : 'btn-default'}`}
              onClick={() => setPageNumber(page)}
            >
              {page}
            </button>
          ))}
        </div>
      )}
    </section>
  )
}
