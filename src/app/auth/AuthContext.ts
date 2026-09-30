import { createContext, useContext } from 'react'

export type UserRole = 'user' | 'admin'

export interface AuthState {
  role: UserRole
}

export const AuthContext = createContext<AuthState | null>(null)

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth must be used within AuthProvider')
  return context
}
