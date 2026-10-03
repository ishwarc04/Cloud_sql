import { createContext, useContext } from 'react'

export type UserRole = 'user' | 'admin'

export interface AuthState {
  role: UserRole | null
  user: Account | null
  loading: boolean
  error: string | null
  refresh: () => Promise<void>
  authenticate: (mode: 'login' | 'signup', input: { email: string; password: string; name?: string }) => Promise<void>
  logout: () => Promise<void>
}

export interface Account { id: string; name: string; email: string; role: 'USER' | 'ADMIN' }

export const AuthContext = createContext<AuthState | null>(null)

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth must be used within AuthProvider')
  return context
}
