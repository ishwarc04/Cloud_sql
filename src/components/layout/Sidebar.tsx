import { NavLink } from 'react-router-dom'
import { useState } from 'react'
import { useAuth } from '../../app/auth/AuthContext'
import { Icon, type IconName } from '../ui/Icon'

const navigation: Array<{ label: string; to: string; icon: IconName }> = [
  { label: 'Dashboard', to: '/dashboard', icon: 'dashboard' },
  { label: 'SQL Practice', to: '/practice', icon: 'practice' },
  { label: 'My Databases', to: '/databases', icon: 'database' },
]

export function Sidebar({ open, onClose }: { open: boolean; onClose: () => void }) {
  const { role, user, logout } = useAuth()
  const [logoutError, setLogoutError] = useState<string | null>(null)
  const [loggingOut, setLoggingOut] = useState(false)
  async function signOut() {
    setLoggingOut(true); setLogoutError(null)
    try { await logout() } catch { setLogoutError('Sign out failed. Please retry.') }
    finally { setLoggingOut(false) }
  }

  return <>
    <button className={`sidebar-backdrop ${open ? 'is-visible' : ''}`} onClick={onClose} aria-label="Close navigation" />
    <aside className={`sidebar ${open ? 'is-open' : ''}`}>
      <div className="brand">
        <div className="brand-mark" aria-hidden="true"><span /><span /><span /></div>
        <div><strong>CloudSQL</strong><span>LAB</span></div>
        <button className="icon-button sidebar-close" onClick={onClose} aria-label="Close navigation"><Icon name="close" /></button>
      </div>
      <button className="workspace-switcher" type="button"><span className="workspace-avatar"><Icon name="layers" /></span><span className="workspace-copy"><small>Workspace</small><strong>CloudSQL Lab</strong></span><Icon name="chevron" className="workspace-chevron" /></button>
      <nav className="sidebar-nav" aria-label="Primary navigation">
        <span className="nav-label">Develop</span>
        {navigation.map((item) => <NavLink key={item.to} to={item.to} onClick={onClose} className={({ isActive }) => `nav-item ${isActive ? 'is-active' : ''}`}><Icon name={item.icon} /><span>{item.label}</span></NavLink>)}
        {role === 'admin' && <><span className="nav-label nav-label-spaced">Operate</span><NavLink to="/admin" onClick={onClose} className={({ isActive }) => `nav-item ${isActive ? 'is-active' : ''}`}><Icon name="admin" /><span>Admin Dashboard</span><span className="admin-tag">Admin</span></NavLink></>}
      </nav>
      <div className="sidebar-footer">
        <NavLink to="/settings" onClick={onClose} className={({ isActive }) => `nav-item ${isActive ? 'is-active' : ''}`}><Icon name="settings" /><span>Settings</span></NavLink>
        <div className="user-card"><div className="user-avatar">{user?.name.slice(0, 2).toUpperCase()}</div><div><strong>{user?.name}</strong><span>{role === 'admin' ? 'Administrator' : 'Student account'}</span></div><span className="role-indicator">{role === 'admin' ? 'A' : 'U'}</span></div>
        <button className="logout-button" disabled={loggingOut} onClick={() => void signOut()}>{loggingOut ? 'Signing out…' : 'Sign out'}</button>
        {logoutError && <p className="error-state" role="alert">{logoutError}</p>}
      </div>
    </aside>
  </>
}
