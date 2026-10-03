import { useState, type FormEvent } from 'react'
import { Link, Navigate, useLocation } from 'react-router-dom'
import { useAuth } from '../app/auth/AuthContext'
export function AuthPage({ mode }: { mode: 'login' | 'signup' }) {
  const { user, loading, authenticate } = useAuth()
  const location = useLocation()
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const signup = mode === 'signup'
  const from = (location.state as { from?: string } | null)?.from
  const destination = from?.startsWith('/') && !from.startsWith('//') && !['/login', '/signup'].includes(from) ? from : '/dashboard'
  if (loading) return <div className="inline-state">Checking your session…</div>
  if (user) return <Navigate to={destination} replace />
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const data = new FormData(event.currentTarget)
    setBusy(true); setError(null)
    try { await authenticate(mode, { email: String(data.get('email')), password: String(data.get('password')), ...(signup ? { name: String(data.get('name')) } : {}) }) }
    catch (failure) { setError(failure instanceof Error ? failure.message : 'Please try again.') }
    finally { setBusy(false) }
  }
  return <main className="auth-page"><div className="auth-card">
    <Link className="auth-brand" to="/login">CloudSQL <span>LAB</span></Link><span className="eyebrow">Your SQL learning workspace</span>
    <h1>{signup ? 'Start learning SQL' : 'Welcome back'}</h1><p>{signup ? 'Practice on real datasets, track your progress, and earn points as you learn.' : 'Sign in to continue your practice and open your databases.'}</p>
    <form onSubmit={(event) => void submit(event)}>
      {signup && <label>Name<input name="name" required maxLength={80} autoComplete="name" /></label>}
      <label>Email<input name="email" type="email" required maxLength={254} autoComplete="email" /></label>
      <label>Password<input name="password" type="password" required minLength={signup ? 12 : undefined} maxLength={128} autoComplete={signup ? 'new-password' : 'current-password'} />{signup && <small>Use at least 12 characters.</small>}</label>
      {error && <p className="error-state" role="alert">{error}</p>}<button className="primary-button" disabled={busy}>{busy ? 'Please wait…' : signup ? 'Create account' : 'Sign in'}</button>
    </form><p>{signup ? 'Already have an account?' : 'New to CloudSQL Lab?'} <Link to={signup ? '/login' : '/signup'} state={location.state}>{signup ? 'Sign in' : 'Create account'}</Link></p>
  </div></main>
}
