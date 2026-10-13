import { useState } from 'react'
import { login, signup } from '../api.js'

export default function AuthForm({ onLogin }) {
  const [tab, setTab] = useState('login')
  const [account, setAccount] = useState('')
  const [password, setPassword] = useState('')
  const [message, setMessage] = useState(null) // { text, error }

  const switchTab = (next) => {
    setTab(next)
    setMessage(null)
  }

  const handleSubmit = async (event) => {
    event.preventDefault()
    try {
      if (tab === 'signup') {
        await signup(account, password)
        setMessage({ text: '가입했어요. 이제 로그인하세요.' })
        setTab('login')
      } else {
        await login(account, password)
        onLogin()
      }
    } catch (error) {
      setMessage({ text: error.message, error: true })
    }
  }

  const isSignup = tab === 'signup'

  return (
    <div className="auth-page">
      <div className="auth-logo">
        <span className="auth-logo-mark">#</span> distributed chat
      </div>
      <h1 className="auth-title">{isSignup ? '새 계정 만들기' : '로그인'}</h1>
      <p className="auth-subtitle">{isSignup ? '아이디와 비밀번호만 있으면 바로 시작해요.' : '팀과 대화를 이어 가세요.'}</p>
      <form className="auth-form" onSubmit={handleSubmit}>
        <label htmlFor="account">아이디</label>
        <input id="account" className="text-input" value={account} onChange={(e) => setAccount(e.target.value)} autoComplete="username" required />
        <label htmlFor="password">비밀번호</label>
        <input
          id="password"
          className="text-input"
          type="password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          autoComplete={isSignup ? 'new-password' : 'current-password'}
          required
        />
        <button type="submit" className="btn btn-primary btn-block">{isSignup ? '회원가입' : '로그인'}</button>
        {message && <div className={`auth-message ${message.error ? 'error' : 'success'}`} role="status">{message.text}</div>}
      </form>
      <p className="auth-switch">
        {isSignup ? '이미 계정이 있나요? ' : '처음이신가요? '}
        <button type="button" onClick={() => switchTab(isSignup ? 'login' : 'signup')}>
          {isSignup ? '로그인' : '계정 만들기'}
        </button>
      </p>
    </div>
  )
}
