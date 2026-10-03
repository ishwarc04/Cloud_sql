import { useEffect, useState } from 'react'
import { apiRequest } from '../../app/api'
import { formatBytes } from '../databases/format'

interface Hour { hour: string; requests: number; errors: number; averageLatencyMs: number; maxLatencyMs: number }
interface CloudReport {
  hours: Hour[]
  health: { id: string; checkedAt: string; status: string; latencyMs: number }[]
  audit: { id: string; actorName: string; action: string; target: string; outcome: string; occurredAt: string }[]
  usage: { userId: string; name: string; requests: number; errors: number; workspaceRequests: number; backupCount: number; backupBytes: number }[]
  totalRequests: number; failedRequests: number; averageLatencyMs: number; probeSuccessPercent: number; probeCount: number
  backupCount: number; backupBytes: number; generatedAt: string
}
function RequestChart({ hours }: { hours: Hour[] }) {
  const [metric, setMetric] = useState<'requests' | 'averageLatencyMs'>('requests')
  const [selected, setSelected] = useState<number | null>(null)
  const peak = Math.max(2, Math.ceil(Math.max(0, ...hours.map(hour => hour[metric])) / 2) * 2)
  const current = hours[selected ?? hours.length - 1]
  return <section className="ops-card"><div className="ops-card-heading"><div><span className="eyebrow">Last 24 hour buckets · UTC</span><h2>API traffic and response time</h2></div><div className="ops-switch">{(['requests', 'averageLatencyMs'] as const).map(value => <button key={value} aria-pressed={metric === value} onClick={() => setMetric(value)}>{value === 'requests' ? 'Requests' : 'Latency'}</button>)}</div></div>
    <div className="ops-chart-summary"><strong>{metric === 'requests' ? hours.reduce((sum, hour) => sum + hour.requests, 0) : current?.averageLatencyMs.toFixed(1)}</strong><span>{metric === 'requests' ? 'observed requests' : 'ms average for selected hour'}</span><small>{current && `${current.hour.slice(0, 16).replace('T', ' ')} UTC · ${current.requests} requests · ${current.errors} failed · ${current.averageLatencyMs.toFixed(1)} ms avg · ${current.maxLatencyMs} ms max`}</small></div>
    <svg className="ops-activity-svg" viewBox="0 0 640 200" role="img" aria-label={`Hourly ${metric === 'requests' ? 'API requests' : 'average latency'} over 24 UTC buckets`}>
      {[0, .5, 1].map(fraction => <g key={fraction}><line x1="40" x2="630" y1={164 - 150 * fraction} y2={164 - 150 * fraction} className="ops-gridline" /><text x="32" y={168 - 150 * fraction} textAnchor="end">{peak * fraction}</text></g>)}
      {hours.map((hour, i) => <g key={hour.hour} tabIndex={0} aria-label={`${hour.hour}: ${hour.requests} requests, ${hour.errors} failed, ${hour.averageLatencyMs.toFixed(1)} ms`} onMouseEnter={() => setSelected(i)} onFocus={() => setSelected(i)}><title>{hour.hour}: {hour[metric].toFixed(1)}</title><rect x={44 + i * 24} y="14" width="20" height="150" fill="transparent" /><rect x={46 + i * 24} y={164 - hour[metric] / peak * 150} width="15" height={hour[metric] / peak * 150} rx="2" className="ops-bar-total" />{metric === 'requests' && <rect x={46 + i * 24} y={164 - hour.errors / peak * 150} width="15" height={hour.errors / peak * 150} className="cloud-bar-error" />}{i % 4 === 0 && <text x={53 + i * 24} y="188" textAnchor="middle">{hour.hour.slice(11, 16)}</text>}</g>)}
    </svg><p className="ops-caption">Measured by the backend. Failed requests include HTTP 4xx and 5xx. Health probes and CORS preflights are excluded.</p></section>
}
export function CloudOperationsPanel({ revision }: { revision: number }) {
  const [data, setData] = useState<CloudReport | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [search, setSearch] = useState('')
  const [outcome, setOutcome] = useState('All')
  const [probing, setProbing] = useState(false)
  useEffect(() => {
    let active = true
    apiRequest<CloudReport>('/api/admin/cloud').then(value => { if (active) { setData(value); setError(null) } }).catch((failure: unknown) => { if (active) setError(failure instanceof Error ? failure.message : 'Unable to read cloud operations.') })
    return () => { active = false }
  }, [revision])
  async function probe() {
    setProbing(true)
    try { setData(await apiRequest<CloudReport>('/api/admin/cloud/probe', { method: 'POST' })); setError(null) }
    catch (failure) { setError(failure instanceof Error ? failure.message : 'Probe failed.') }
    finally { setProbing(false) }
  }
  if (!data) return <div className={`ops-empty ${error ? 'error-state' : ''}`} role={error ? 'alert' : 'status'}>{error ?? 'Loading cloud operations…'}</div>
  const errorPercent = data.totalRequests ? data.failedRequests / data.totalRequests * 100 : 0
  const latest = data.health[0]
  const events = data.audit.filter(event => `${event.actorName} ${event.action} ${event.target}`.toLowerCase().includes(search.toLowerCase()) && (outcome === 'All' || (outcome === 'SUCCESS' ? event.outcome === 'SUCCESS' : event.outcome !== 'SUCCESS')))
  return <div className="cloud-panel">
    {error && <p className="error-state" role="alert">{error}</p>}
    <div className="cloud-intro"><div><span className="eyebrow">Cloud service management</span><h2>Monitor, account, and recover</h2><p>Persisted telemetry and audit events from your running application.</p></div><button className="secondary-button" disabled={probing} onClick={() => void probe()}>{probing ? 'Checking…' : 'Check database'}</button></div>
    <div className="ops-metrics cloud-metrics">{[['API requests', data.totalRequests.toLocaleString(), 'Last 24 UTC hour buckets'], ['Failed responses', `${errorPercent.toFixed(1)}%`, `${data.failedRequests} HTTP errors`], ['Average response', `${data.averageLatencyMs.toFixed(1)} ms`, 'Backend processing time'], ['Saved snapshots', data.backupCount, `${formatBytes(data.backupBytes)} stored in PostgreSQL`]].map(([label, value, detail]) => <article className="ops-card" key={label}><span className="ops-metric-label">{label}</span><strong>{value}</strong><small>{detail}</small></article>)}</div>
    <div className="ops-chart-grid"><RequestChart hours={data.hours} /><section className="ops-card"><div className="ops-card-heading"><div><span className="eyebrow">Database readiness · last 24 hours</span><h2>Observed probe results</h2></div><span className={`ops-badge ${latest?.status === 'DOWN' ? 'ops-danger' : latest ? 'ops-success' : ''}`}>{latest?.status ?? 'No samples'}</span></div><div className="cloud-probe-summary"><strong>{data.probeCount ? `${data.probeSuccessPercent.toFixed(1)}%` : '—'}</strong><span>successful probes · {data.probeCount} observed samples</span><div className="cloud-probe-grid">{data.health.slice(0, 120).reverse().map(sample => <span key={sample.id} className={sample.status === 'UP' ? 'is-up' : 'is-down'} title={`${new Date(sample.checkedAt).toLocaleString()}: ${sample.status} (${sample.latencyMs} ms)`} aria-label={`${sample.checkedAt}: ${sample.status}`} />)}</div><p>{latest ? `Latest check ${new Date(latest.checkedAt).toLocaleString()} · ${latest.latencyMs} ms` : 'Use Check database to record the first probe.'}</p></div><p className="ops-caption">Production samples every minute while the backend runs. Sleeping services and missing samples are gaps; this is not an end-to-end uptime or SLA guarantee.</p></section></div>
    <section className="ops-card ops-section"><div className="ops-card-heading"><div><span className="eyebrow">Security and accountability</span><h2>Audit trail</h2><p>Latest 100 events · retained for 30 days</p></div><div className="ops-filters"><input aria-label="Search audit events" value={search} placeholder="Search actor or action" onChange={event => setSearch(event.target.value)} /><select aria-label="Filter audit result" value={outcome} onChange={event => setOutcome(event.target.value)}><option value="All">All events</option><option value="SUCCESS">Successful</option><option value="FAILED">Failed / denied</option></select></div></div><div className="ops-table-scroll"><table className="ops-table"><thead><tr><th>Actor</th><th>Action</th><th>Resource</th><th>Result</th><th>Time</th></tr></thead><tbody>{events.map(event => <tr key={event.id}><td>{event.actorName}</td><td>{event.action.replaceAll('_', ' ').toLowerCase()}</td><td className="cloud-resource-id">{event.target}</td><td><span className={`ops-badge ${event.outcome === 'SUCCESS' ? 'ops-success' : 'ops-danger'}`}>{event.outcome.replaceAll('_', ' ').toLowerCase()}</span></td><td>{new Date(event.occurredAt).toLocaleString()}</td></tr>)}</tbody></table>{events.length === 0 && <div className="ops-empty">No matching audit events.</div>}</div><p className="ops-caption">Passwords, tokens, raw SQL, and backup contents are excluded from audit records.</p></section>
    <section className="ops-card ops-section"><div className="ops-card-heading"><div><span className="eyebrow">Usage accounting</span><h2>Consumption by account</h2><p>Metered API requests over 24 UTC buckets and currently retained backups.</p></div></div><div className="ops-table-scroll"><table className="ops-table"><thead><tr><th>Account</th><th>API requests</th><th>Failed</th><th>Workspace requests</th><th>Saved backups</th><th>Backup storage</th></tr></thead><tbody>{data.usage.map(user => <tr key={user.userId}><td>{user.name}</td><td>{user.requests}</td><td>{user.errors}</td><td>{user.workspaceRequests}</td><td>{user.backupCount} / 3</td><td>{formatBytes(user.backupBytes)}</td></tr>)}</tbody></table></div><p className="ops-caption">This is application metering, not a Neon, Render, or Vercel bill. Anonymous traffic appears in platform totals.</p></section>
    <section className="ops-card ops-section"><div className="ops-card-heading"><div><span className="eyebrow">Architecture and DevOps</span><h2>Your cloud deployment</h2></div><a className="ops-text-button" href="https://github.com/ishwarc04/Cloud_sql/actions/workflows/ci.yml" target="_blank" rel="noreferrer">View build checks ↗</a></div><div className="cloud-architecture"><div><strong>Vercel</strong><span>Frontend + function proxy</span><small>SaaS delivery / serverless request handling</small></div><b>→</b><div><strong>Render</strong><span>Spring Boot in Docker</span><small>Managed hosting / container deployment</small></div><b>→</b><div><strong>Neon</strong><span>PostgreSQL persistence</span><small>Managed SQL / logical tenant schemas</small></div></div><p className="ops-caption">Workspace snapshots preserve supported table columns, primary keys, and data. They are stored in the same database; users can download an independent copy. Other constraints and indexes are outside this snapshot format.</p></section>
  </div>
}
