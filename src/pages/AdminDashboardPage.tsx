import { useEffect, useState } from 'react'
import { Icon, type IconName } from '../components/ui/Icon'
import { adminService } from '../features/admin/adminService'
import type { AdminDatabase, AdminOverview } from '../features/admin/types'
import { formatBytes, formatCreatedAt, storagePercent } from '../features/databases/format'

export function AdminDashboardPage() {
  const [overview, setOverview] = useState<AdminOverview | null>(null)
  const [databases, setDatabases] = useState<AdminDatabase[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let active = true
    Promise.all([adminService.overview(), adminService.databases()]).then(([overviewData, databaseData]) => {
      if (!active) return
      setOverview(overviewData); setDatabases(databaseData)
    }).catch((requestError: unknown) => {
      if (active) setError(requestError instanceof Error ? requestError.message : 'Admin data could not be loaded.')
    }).finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [])

  if (loading) return <section className="page"><div className="inline-state page-loading"><span className="spinner" />Loading platform overview…</div></section>
  if (error || !overview) return <section className="page"><div className="inline-state error-state">{error ?? 'Admin overview is unavailable.'}</div></section>

  const metrics: Array<{ label: string; value: string; detail: string; icon: IconName }> = [
    { label: 'Database workspaces', value: String(overview.totalDatabaseWorkspaces), detail: `${overview.activeDatabaseWorkspaces} active`, icon: 'database' },
    { label: 'Allocated storage', value: formatBytes(overview.totalStorageUsedBytes), detail: `${formatBytes(overview.availableStorageBytes)} available`, icon: 'server' },
    { label: 'Practice catalogue', value: String(overview.totalPracticeProblems), detail: `${overview.solvedProblemCount} solved`, icon: 'practice' },
    { label: 'Practice attempts', value: String(overview.totalPracticeAttempts), detail: 'Recorded submissions', icon: 'terminal' },
  ]

  return <section className="page admin-dashboard-page">
    <header className="page-header"><div><span className="eyebrow">Platform operations</span><h1>Admin Dashboard</h1><p>Live operational summary from the current CloudSQL Lab backend.</p></div><span className="admin-health"><i />Service {overview.serviceStatus.toLowerCase()}</span></header>

    <div className="admin-metrics">
      {metrics.map((metric) => <article key={metric.label}><span className="admin-metric-icon"><Icon name={metric.icon} /></span><div><span>{metric.label}</span><strong>{metric.value}</strong><small>{metric.detail}</small></div></article>)}
    </div>

    <div className="admin-status-grid">
      <section className="admin-status-panel"><div className="admin-section-heading"><div><span>Capacity</span><strong>Workspace storage</strong></div><span>{storagePercent(overview.totalStorageUsedBytes, overview.totalStorageCapacityBytes).toFixed(1)}%</span></div><div className="admin-capacity"><div className="quota-track"><span style={{ width: `${storagePercent(overview.totalStorageUsedBytes, overview.totalStorageCapacityBytes)}%` }} /></div><div><span>{formatBytes(overview.totalStorageUsedBytes)} used</span><span>{formatBytes(overview.totalStorageCapacityBytes)} allocated</span></div></div></section>
      <section className="admin-status-panel"><div className="admin-section-heading"><div><span>Learning</span><strong>SQL Practice progress</strong></div><span>{overview.solvedProblemCount} / {overview.totalPracticeProblems}</span></div><div className="admin-capacity"><div className="quota-track progress-track"><span style={{ width: `${storagePercent(overview.solvedProblemCount, overview.totalPracticeProblems)}%` }} /></div><div><span>{overview.solvedProblemCount} solved</span><span>{overview.totalPracticeAttempts} attempts</span></div></div></section>
    </div>

    <section className="admin-workspaces">
      <div className="admin-table-title"><div><span>Resources</span><strong>Database workspaces</strong></div><span>{databases.length} total</span></div>
      <div className="admin-database-head"><span>Workspace</span><span>Owner</span><span>Status</span><span>Created</span><span>Storage</span></div>
      {databases.map((database) => <div className="admin-database-row" key={database.id}><div><strong>{database.name}</strong><small>{database.id.slice(0, 8)}</small></div><code>{database.ownerUserId}</code><span className="database-status"><i />{database.status.toLowerCase()}</span><span>{formatCreatedAt(database.createdAt)}</span><div className="database-storage"><span>{formatBytes(database.storageUsedBytes)} / {formatBytes(database.storageLimitBytes)}</span><div className="quota-track"><span style={{ width: `${storagePercent(database.storageUsedBytes, database.storageLimitBytes)}%` }} /></div></div></div>)}
      {databases.length === 0 && <div className="admin-empty">No personal database workspaces have been created.</div>}
    </section>
  </section>
}
