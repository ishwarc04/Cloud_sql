import { useEffect, useMemo, useRef, useState } from 'react'
import { Icon } from '../components/ui/Icon'
import { adminService } from '../features/admin/adminService'
import type { AdminAnalytics, AdminSubmission, AdminUserDetail, AdminWorkspace } from '../features/admin/types'
import { ActivityChart, CategoryChart, OutcomeChart } from '../features/admin/AdminCharts'
import { formatBytes, storagePercent } from '../features/databases/format'
import { AdminCreatePanel } from '../features/admin/AdminCreatePanel'
import { CloudOperationsPanel } from '../features/admin/CloudOperationsPanel'

type Tab = 'Overview' | 'People' | 'Workspaces' | 'Problems' | 'Activity' | 'Cloud'
const date = (value: string | null) => value ? new Date(value).toLocaleString() : 'No submissions yet'
const count = (value: number) => value.toLocaleString()
function Outcome({ value }: { value: string }) {
  return <span className={`ops-badge ${value === 'ACCEPTED' ? 'ops-success' : value === 'ERROR' ? 'ops-danger' : 'ops-warning'}`}>{value === 'ACCEPTED' ? 'Accepted' : value === 'ERROR' ? 'Query error' : 'Wrong answer'}</span>
}
function SubmissionTable({ rows, onUser }: { rows: AdminSubmission[]; onUser: (id: string) => void }) {
  return <div className="ops-table-scroll"><table className="ops-table"><thead><tr><th>Account</th><th>Problem</th><th>Result</th><th>Points</th><th>Submitted</th></tr></thead><tbody>{rows.map(row => <tr key={row.id}><td><button className="ops-text-button" onClick={() => onUser(row.userId)}>{row.userName}</button></td><td><strong>{row.problemTitle}</strong><small>{row.difficulty} · {row.category}</small></td><td><Outcome value={row.outcome} /></td><td>+{row.pointsAwarded}</td><td className="ops-muted">{date(row.submittedAt)}</td></tr>)}</tbody></table>{rows.length === 0 && <div className="ops-empty">No submissions recorded yet.</div>}</div>
}
function WorkspaceTable({ rows, onUser }: { rows: AdminWorkspace[]; onUser: (id: string) => void }) {
  return <div className="ops-table-scroll"><table className="ops-table"><thead><tr><th>Workspace</th><th>Owner</th><th>Status</th><th>Recorded storage</th><th>Created</th></tr></thead><tbody>{rows.map(row => <tr key={row.id}><td><strong>{row.name}</strong><small>{row.id.slice(0, 8)}</small></td><td>{row.ownerEmail ? <button className="ops-text-button" onClick={() => onUser(row.ownerUserId)}>{row.ownerName}</button> : row.ownerName}<small>{row.ownerEmail ?? 'Legacy demo resource'}</small></td><td><span className="ops-badge">{row.status.toLowerCase()}</span></td><td><span>{formatBytes(row.storageUsedBytes)} / {formatBytes(row.storageLimitBytes)}</span><progress value={Math.min(row.storageUsedBytes, row.storageLimitBytes)} max={Math.max(1, row.storageLimitBytes)} aria-label={`${row.name} storage usage`} /></td><td className="ops-muted">{date(row.createdAt)}</td></tr>)}</tbody></table>{rows.length === 0 && <div className="ops-empty">No database workspaces yet.</div>}</div>
}
export function AdminDashboardPage() {
  const [data, setData] = useState<AdminAnalytics | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [revision, setRevision] = useState(0)
  const [refreshing, setRefreshing] = useState(false)
  const [tab, setTab] = useState<Tab>('Overview')
  const [search, setSearch] = useState('')
  const [filter, setFilter] = useState('All')
  const [userId, setUserId] = useState<string | null>(null)
  const [detail, setDetail] = useState<AdminUserDetail | null>(null)
  const [detailError, setDetailError] = useState<string | null>(null)
  const detailPanel = useRef<HTMLElement>(null)
  const [creating, setCreating] = useState<'user' | 'question' | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  useEffect(() => {
    let active = true
    adminService.analytics().then(snapshot => { if (active) { setData(snapshot); setError(null) } })
      .catch((failure: unknown) => { if (active) setError(failure instanceof Error ? failure.message : 'Unable to load admin data.') })
      .finally(() => { if (active) setRefreshing(false) })
    return () => { active = false }
  }, [revision])
  useEffect(() => {
    if (!userId) return
    let active = true
    adminService.user(userId).then(value => { if (active) { setDetail(value); setDetailError(null) } })
      .catch((failure: unknown) => { if (active) setDetailError(failure instanceof Error ? failure.message : 'Unable to load account.') })
    return () => { active = false }
  }, [userId, revision])
  useEffect(() => { if (userId) detailPanel.current?.scrollIntoView({ behavior: 'smooth', block: 'start' }) }, [userId])
  function openUser(id: string) {
    if (id === userId) { detailPanel.current?.scrollIntoView({ behavior: 'smooth', block: 'start' }); return }
    setDetail(null); setDetailError(null); setUserId(id)
  }
  function refresh() { setRefreshing(true); setRevision(value => value + 1) }
  const visibleUsers = useMemo(() => data?.users.filter(user => `${user.name} ${user.email}`.toLowerCase().includes(search.toLowerCase()) && (filter === 'All' || user.role === filter)) ?? [], [data, search, filter])
  if (!data) return <section className="page"><div className={`inline-state ${error ? 'error-state' : ''}`} role={error ? 'alert' : 'status'}>{error ?? 'Loading platform operations…'}{error && <button onClick={refresh} disabled={refreshing}>Retry</button>}</div></section>
  const s = data.summary
  const cards = [
    { label: 'Accounts', value: count(s.totalUsers), detail: `${s.learners} learners · ${s.administrators} admins`, icon: 'users' as const },
    { label: 'Active accounts', value: count(s.activeUsersSevenDays), detail: 'Accounts submitting in the last 7 days', icon: 'activity' as const },
    { label: 'Submissions', value: count(s.totalSubmissions), detail: `${count(s.queryErrors)} query errors recorded`, icon: 'terminal' as const },
    { label: 'Problems solved', value: count(s.solvedUserProblems), detail: `${count(s.totalPoints)} points awarded`, icon: 'practice' as const },
  ]
  return <section className="page ops-page">
    <header className="page-header"><div><span className="eyebrow">Platform operations</span><h1>Admin Dashboard</h1><p>Accounts, learning activity, and cloud resources in one place.</p></div><div className="ops-header-actions"><span className="ops-live"><i />Database connected</span><button className="secondary-button" disabled={refreshing} onClick={refresh}><Icon name="refresh" />{refreshing ? 'Refreshing…' : 'Refresh'}</button></div></header>
    <div className="ops-action-bar"><span>Manage learners and your SQL catalogue</span><div><button className="secondary-button" onClick={() => { setCreating('user'); setNotice(null) }}><Icon name="users" />Create learner</button><button className="primary-button" onClick={() => { setCreating('question'); setNotice(null) }}>+ Add question</button></div></div>
    {notice && <div className="ops-success-notice" role="status">{notice}<button className="ops-text-button" onClick={() => setNotice(null)}>Dismiss</button></div>}
    {creating && <AdminCreatePanel key={creating} kind={creating} onClose={() => setCreating(null)} onCreated={message => { setCreating(null); setNotice(message); refresh() }} />}
    <div className="ops-snapshot"><span>Updated {date(data.generatedAt)}</span><span>Persisted platform snapshot · {s.databaseLatencyMs} ms database probe</span></div>
    {error && <div className="database-alert" role="alert">Refresh failed: {error}. Showing the last successful snapshot.<button onClick={refresh}>Retry</button></div>}
    <div className="ops-metrics">{cards.map(card => <article className="ops-card" key={card.label}><span className="ops-metric-label"><Icon name={card.icon} />{card.label}</span><strong>{card.value}</strong><small>{card.detail}</small></article>)}</div>
    <nav className="ops-tabs" aria-label="Admin views">{(['Overview', 'People', 'Workspaces', 'Problems', 'Activity', 'Cloud'] as Tab[]).map(value => <button key={value} aria-current={tab === value ? 'page' : undefined} onClick={() => { setTab(value); setSearch(''); setFilter('All') }}>{value}{value === 'People' && <span>{s.totalUsers}</span>}{value === 'Workspaces' && <span>{s.totalWorkspaces}</span>}</button>)}</nav>
    {tab === 'Cloud' && <CloudOperationsPanel revision={revision} />}
    {tab === 'Overview' && <>
      <div className="ops-chart-grid"><ActivityChart days={data.dailyActivity} /><OutcomeChart summary={s} /></div>
      <div className="ops-secondary-grid"><CategoryChart problems={data.problems} /><section className="ops-card"><div className="ops-card-heading"><div><span className="eyebrow">Cloud workspace allocation</span><h2>Resources and quotas</h2></div><Icon name="server" /></div><div className="ops-resource-number"><strong>{formatBytes(s.storageUsedBytes)}</strong><span>of {formatBytes(s.storageAllocatedBytes)} allocated</span></div><progress className="ops-capacity-progress" value={Math.min(s.storageUsedBytes, s.storageAllocatedBytes)} max={Math.max(1, s.storageAllocatedBytes)} aria-label="Total recorded workspace storage" /><div className="ops-resource-caption"><span>{s.totalWorkspaces} workspaces</span><span>{storagePercent(s.storageUsedBytes, s.storageAllocatedBytes).toFixed(1)}% used</span></div><dl className="ops-quota-list"><div><dt>Databases per account</dt><dd>{data.quotas.maxDatabasesPerUser}</dd></div><div><dt>Storage per database</dt><dd>{formatBytes(data.quotas.storageLimitPerDatabaseBytes)}</dd></div><div><dt>Query timeout</dt><dd>{data.quotas.queryTimeoutSeconds} seconds</dd></div><div><dt>Result row limit</dt><dd>{data.quotas.maxReturnedRows}</dd></div><div><dt>Unexpired sessions</dt><dd>{s.activeSessions}</dd></div></dl><p className="ops-caption">Storage reflects the last workspace measurement. Session counts include multiple devices.</p></section></div>
      <section className="ops-card ops-section"><div className="ops-card-heading"><div><span className="eyebrow">Latest platform events</span><h2>Recent submissions</h2></div><button className="ops-text-button" onClick={() => setTab('Activity')}>View all activity →</button></div><SubmissionTable rows={data.recentSubmissions.slice(0, 6)} onUser={openUser} /></section>
    </>}
    {tab === 'People' && <section className="ops-card ops-section"><div className="ops-card-heading"><div><h2>Accounts and progress</h2><p>Latest {data.users.length} accounts · select a name for details</p></div><div className="ops-filters"><input aria-label="Search accounts" placeholder="Search name or email" value={search} onChange={event => setSearch(event.target.value)} /><select aria-label="Filter account role" value={filter} onChange={event => setFilter(event.target.value)}><option>All</option><option value="USER">Learners</option><option value="ADMIN">Administrators</option></select></div></div><div className="ops-table-scroll"><table className="ops-table"><thead><tr><th>Account</th><th>Role</th><th>Points</th><th>Solved / attempts</th><th>Databases</th><th>Last submission</th></tr></thead><tbody>{visibleUsers.map(user => <tr key={user.id}><td><button className="ops-text-button" onClick={() => openUser(user.id)}>{user.name}</button><small>{user.email}</small></td><td><span className={`ops-badge ${user.role === 'ADMIN' ? 'ops-warning' : ''}`}>{user.role === 'ADMIN' ? 'Admin' : 'Learner'}</span></td><td><strong className="ops-points">{user.points}</strong></td><td>{user.solvedProblems} solved<small>{user.attempts} attempts</small></td><td>{user.databaseCount} / {data.quotas.maxDatabasesPerUser}<small>{formatBytes(user.storageUsedBytes)} used</small></td><td className="ops-muted">{date(user.lastSubmissionAt)}</td></tr>)}</tbody></table>{visibleUsers.length === 0 && <div className="ops-empty">No accounts match these filters.</div>}</div></section>}
    {tab === 'Workspaces' && <section className="ops-card ops-section"><div className="ops-card-heading"><div><h2>Database workspaces</h2><p>Latest {data.workspaces.length} resources · quota usage and ownership</p></div><input className="ops-search" aria-label="Search workspaces" placeholder="Search workspace or owner" value={search} onChange={event => setSearch(event.target.value)} /></div><WorkspaceTable rows={data.workspaces.filter(w => `${w.name} ${w.ownerName} ${w.ownerEmail ?? ''}`.toLowerCase().includes(search.toLowerCase()))} onUser={openUser} /></section>}
    {tab === 'Problems' && <section className="ops-card ops-section"><div className="ops-card-heading"><div><h2>Problem performance</h2><p>Unique solvers and outcomes across the complete catalogue</p></div><select className="ops-select" aria-label="Filter problem difficulty" value={filter} onChange={event => setFilter(event.target.value)}>{['All', 'Easy', 'Medium', 'Hard'].map(value => <option key={value}>{value}</option>)}</select></div><div className="ops-table-scroll"><table className="ops-table"><thead><tr><th>Problem</th><th>Difficulty</th><th>Unique solvers</th><th>Attempts</th><th>Acceptance</th><th>Query errors</th></tr></thead><tbody>{data.problems.filter(p => filter === 'All' || p.difficulty === filter).map(p => <tr key={p.id}><td><strong>{p.title}</strong><small>{p.category}</small></td><td><span className={`ops-badge ${p.difficulty === 'Hard' ? 'ops-danger' : p.difficulty === 'Easy' ? 'ops-success' : 'ops-warning'}`}>{p.difficulty}</span></td><td>{p.solvedUsers}</td><td>{p.attempts}</td><td>{p.attempts ? `${(p.accepted / p.attempts * 100).toFixed(1)}%` : 'No attempts'}</td><td>{p.errors}</td></tr>)}</tbody></table></div></section>}
    {tab === 'Activity' && <section className="ops-card ops-section"><div className="ops-card-heading"><div><h2>Submission activity</h2><p>Latest {data.recentSubmissions.length} submissions · original query text stays private</p></div><select className="ops-select" aria-label="Filter submission outcome" value={filter} onChange={event => setFilter(event.target.value)}><option value="All">All results</option><option value="ACCEPTED">Accepted</option><option value="WRONG_ANSWER">Wrong answer</option><option value="ERROR">Query error</option></select></div><SubmissionTable rows={data.recentSubmissions.filter(row => filter === 'All' || row.outcome === filter)} onUser={openUser} /></section>}
    {userId && <section ref={detailPanel} className="ops-card ops-user-detail" aria-label="Selected account details"><div className="ops-card-heading"><div><span className="eyebrow">Account inspection</span><h2>{detail?.user.name ?? 'Account details'}</h2></div><button className="secondary-button" onClick={() => setUserId(null)}>Close details</button></div>{detailError ? <div className="ops-empty error-state" role="alert">{detailError}<button onClick={refresh}>Retry</button></div> : !detail ? <div className="ops-empty">Loading account…</div> : <><div className="ops-user-summary"><div><strong>{detail.user.email}</strong><small>{detail.user.role} · joined {date(detail.user.createdAt)}</small></div><span>{detail.user.points} points</span><span>{detail.user.solvedProblems} solved</span><span>{detail.user.attempts} attempts</span><span>{detail.user.activeSessions} sessions</span></div><h3 className="ops-subheading">Personal databases</h3><WorkspaceTable rows={detail.workspaces} onUser={openUser} /><h3 className="ops-subheading">Recent submissions</h3><SubmissionTable rows={detail.recentSubmissions} onUser={openUser} /></>}</section>}
  </section>
}
