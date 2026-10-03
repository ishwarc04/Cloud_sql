import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from './AuthContext'
export function RequireAuth({ children }: { children: ReactNode }) {
  const { user, loading, error, refresh } = useAuth()
  const location = useLocation()
  if (loading) return <div className="inline-state" role="status">Checking your session…</div>
  if (error) return <div className="inline-state error-state" role="alert">{error}<button onClick={() => void refresh()}>Retry</button></div>
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname }} />
  return children
}
