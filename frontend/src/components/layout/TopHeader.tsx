import { useLocation } from 'react-router-dom'
import { Icon } from '../ui/Icon'

const routes: Record<string, { group: string; title: string }> = {
  '/dashboard': { group: 'Workspace', title: 'Dashboard' },
  '/leaderboard': { group: 'Learn', title: 'Leaderboard' },
  '/billing': { group: 'Account', title: 'Plans & Billing' },
  '/practice': { group: 'Develop', title: 'SQL Practice' },
  '/databases': { group: 'Develop', title: 'My Databases' },
  '/admin': { group: 'Operate', title: 'Admin Dashboard' },
  '/settings': { group: 'Account', title: 'Settings' },
}

export function TopHeader({ onMenuClick }: { onMenuClick: () => void }) {
  const pathname = useLocation().pathname
  const route = pathname.startsWith('/practice/') ? { group: 'SQL Practice', title: 'Problem workspace' }
    : pathname.startsWith('/databases/') ? { group: 'My Databases', title: 'Database workspace' }
    : routes[pathname] ?? { group: 'Workspace', title: 'CloudSQL Lab' }
  return <header className="top-header">
    <div className="header-title"><button className="icon-button mobile-menu" onClick={onMenuClick} aria-label="Open navigation"><Icon name="menu" /></button><span>{route.group}</span><span className="breadcrumb-separator">/</span><strong>{route.title}</strong></div>
    <div className="header-actions"><span className="environment"><span />Operational</span><button className="search-button" type="button"><Icon name="search" /><span>Search workspace</span><kbd>Ctrl K</kbd></button><button className="icon-button notification-button" type="button" aria-label="Notifications"><Icon name="bell" /><span /></button></div>
  </header>
}
