import { teamInitials } from '../format.js'

// 맨 왼쪽 팀 목록. + 는 팀 찾기 / 만들기
export default function WorkspaceRail({ teams, teamId, onSelect, onAdd }) {
  return (
    <nav className="workspace-rail" aria-label="팀">
      {teams.map((team) => (
        <button
          key={team.teamId}
          type="button"
          className={`rail-team ${team.teamId === teamId ? 'active' : ''}`}
          onClick={() => onSelect(team.teamId)}
          title={team.teamName}
          aria-label={team.teamName}
          aria-current={team.teamId === teamId ? 'true' : undefined}
        >
          {teamInitials(team.teamName)}
        </button>
      ))}
      <button type="button" className="rail-add" onClick={onAdd} title="팀 찾기 / 만들기" aria-label="팀 추가">
        +
      </button>
    </nav>
  )
}
