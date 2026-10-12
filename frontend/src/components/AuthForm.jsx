import { useState } from 'react'
import { login, signup } from '../api.js'

export default function AuthForm({ onLogin }) {
  const [tab, setTab] = useState('signup')
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
        const data = await signup(account, password)
        setMessage({ text: `회원가입 성공! User ID: ${data.userId}` })
        setTab('login')
      } else {
        await login(account, password)
        onLogin()
      }
    } catch (error) {
      setMessage({ text: `에러: ${error.message}`, error: true })
    }
  }

  return (
    <div className="auth-page">
      <div className="auth-container">
        <div className="tab">
          <button className={tab === 'signup' ? 'active' : ''} onClick={() => switchTab('signup')}>회원가입</button>
          <button className={tab === 'login' ? 'active' : ''} onClick={() => switchTab('login')}>로그인</button>
        </div>
        <h2>{tab === 'signup' ? '회원가입' : '로그인'}</h2>
        <form onSubmit={handleSubmit}>
          <div className="form-group">
            <label htmlFor="account">아이디</label>
            <input id="account" value={account} onChange={(e) => setAccount(e.target.value)} required />
          </div>
          <div className="form-group">
            <label htmlFor="password">비밀번호</label>
            <input id="password" type="password" value={password} onChange={(e) => setPassword(e.target.value)} required />
          </div>
          <button type="submit" className="btn btn-primary btn-block">
            {tab === 'signup' ? '회원가입' : '로그인'}
          </button>
          {message && <div className={`message ${message.error ? 'error' : 'success'}`}>{message.text}</div>}
        </form>
      </div>
    </div>
  )
}
