import { useState } from 'react'
import { Outlet } from 'react-router-dom'
import { Sidebar } from './Sidebar'
import { TopHeader } from './TopHeader'

export function AppShell() {
  const [sidebarOpen, setSidebarOpen] = useState(false)
  return <div className="app-shell"><Sidebar open={sidebarOpen} onClose={() => setSidebarOpen(false)} /><div className="app-frame"><TopHeader onMenuClick={() => setSidebarOpen(true)} /><main className="main-content"><Outlet /></main></div></div>
}
