import { useEffect, useState } from 'react'
import { getMyInfo, logout } from './api.js'
import AuthForm from './components/AuthForm.jsx'
import ChatRoom from './components/ChatRoom.jsx'
import TeamPanel from './components/TeamPanel.jsx'

export default function App() {
  // undefined: 확인 중, null: 비로그인
  const [me, setMe] = useState(undefined)

  const refreshMe = async () => {
    try {
      setMe(await getMyInfo())
    } catch {
      setMe(null)
    }
  }

  useEffect(() => {
    refreshMe()
  }, [])

  const handleLogout = async () => {
    await logout()
    setMe(null)
  }

  if (me === undefined) return null
  if (me === null) return <AuthForm onLogin={refreshMe} />

  return (
    <div className="app">
      <header className="header">
        <h1>💬 Chat Room</h1>
        <div className="header-user">
          <span>{me.account} (ID: {me.userId})</span>
          <button className="btn btn-danger" onClick={handleLogout}>Logout</button>
        </div>
      </header>
      <main className="layout">
        <ChatRoom myUserId={me.userId} />
        <TeamPanel />
      </main>
    </div>
  )
}
