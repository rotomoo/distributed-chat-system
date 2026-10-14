import { useEffect, useState } from 'react'
import { getMyInfo, logout } from './api.js'
import { disablePush } from './push.js'
import AuthForm from './components/AuthForm.jsx'
import Workspace from './components/Workspace.jsx'

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

  // 이 기기의 웹 푸시 구독을 지우고(세션이 살아 있을 때) 세션을 끊는다.
  // 화면을 내리면 Workspace 가 언마운트되며 웹소켓도 다시 붙지 않고 닫힌다
  const handleLogout = async () => {
    try {
      await disablePush().catch(() => {})
      await logout()
    } finally {
      setMe(null)
    }
  }

  if (me === undefined) return null
  if (me === null) return <AuthForm onLogin={refreshMe} />
  return <Workspace me={me} onLogout={handleLogout} />
}
