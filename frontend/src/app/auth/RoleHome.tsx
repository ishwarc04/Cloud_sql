import { Navigate } from 'react-router-dom'
import { useAuth } from './AuthContext'

export function RoleHome() {
  const { role } = useAuth()
  return <Navigate to={role === 'admin' ? '/admin' : '/dashboard'} replace />
}
