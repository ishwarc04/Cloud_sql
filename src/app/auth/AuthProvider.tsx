import { useEffect, useState, type ReactNode } from 'react'
import { AuthContext, type Account } from './AuthContext'
import { apiFetch, apiRequest } from '../api'

async function fetchAccount(): Promise<Account | null> {
  const response = await apiFetch('/api/auth/me')
  if (response.status === 401) return null
  if (!response.ok) throw new Error('Your session could not be checked. Please retry.')
  return response.json() as Promise<Account>
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<Account | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const refresh = async () => {
    setLoading(true); setError(null)
    try { setUser(await fetchAccount()) }
    catch (failure) { setError(failure instanceof Error ? failure.message : 'Unable to connect.') }
    finally { setLoading(false) }
  }
  useEffect(() => {
    let active = true
    fetchAccount().then(account => { if (active) setUser(account) })
      .catch((failure: unknown) => { if (active) setError(failure instanceof Error ? failure.message : 'Unable to connect.') })
      .finally(() => { if (active) setLoading(false) })
    const expired = () => setUser(null)
    window.addEventListener('cloudsql-session-expired', expired)
    return () => { active = false; window.removeEventListener('cloudsql-session-expired', expired) }
  }, [])
  const authenticate = async (mode: 'login' | 'signup', input: { email: string; password: string; name?: string }) => {
    const account = await apiRequest<Account>(`/api/auth/${mode}`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(input) })
    setError(null); setUser(account)
  }
  const logout = async () => { await apiRequest<void>('/api/auth/logout', { method: 'POST' }); setUser(null) }
  return <AuthContext.Provider value={{ user, role: user ? (user.role === 'ADMIN' ? 'admin' : 'user') : null, loading, error, refresh, authenticate, logout }}>{children}</AuthContext.Provider>
}
