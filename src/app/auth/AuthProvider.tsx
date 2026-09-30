import type { ReactNode } from 'react'
import { AuthContext, type AuthState, type UserRole } from './AuthContext'

// DEVELOPMENT ONLY: frontend role simulation until backend authentication/RBAC replaces this provider.
const DEVELOPMENT_ROLE: UserRole = import.meta.env.VITE_DEV_ROLE?.toLowerCase() === 'admin' ? 'admin' : 'user'

export function AuthProvider({ children }: { children: ReactNode }) {
  const value: AuthState = { role: DEVELOPMENT_ROLE }
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
