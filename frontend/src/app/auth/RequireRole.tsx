import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { useAuth, type UserRole } from './AuthContext'

export function RequireRole({ role: requiredRole, children }: { role: UserRole; children: ReactNode }) {
  const { role } = useAuth()
  const location = useLocation()

  if (role !== requiredRole) {
    return <Navigate to={role === 'admin' ? '/admin' : '/dashboard'} replace state={{ blockedPath: location.pathname }} />
  }

  return children
}
