import { useEffect, useState } from 'react'
import { apiRequest } from '../../app/api'
interface SecurityReport {
  failedLogins: number; blockedLogins: number; rateLimitedRequests: number; revokedSessions: number; activeSessions: number; requestsPerMinute: number
  events: { id: string; actor: string; action: string; outcome: string; occurredAt: string }[]
}
export function AdminSecurityPanel({ revision }: { revision: number }) {
  const [data, setData] = useState<SecurityReport | null>(null)
  const [error, setError] = useState('')
  useEffect(() => {
    let active = true
    apiRequest<SecurityReport>('/api/admin/security').then(value => { if (active) { setData(value); setError('') } }).catch(e => { if (active) setError(e instanceof Error ? e.message : 'Unable to load security report.') })
    return () => { active = false }
  }, [revision])
  return <div>
    {error && <p className="error-state" role="alert">{error}</p>}
    {!data && !error && <p className="ops-empty">Loading security events…</p>}
    {data && <><div className="ops-metrics">{[
      ['Failed logins', data.failedLogins], ['Blocked sign-ins', data.blockedLogins], ['Rate-limited requests', data.rateLimitedRequests], ['Revoked sessions', data.revokedSessions],
    ].map(([label, value]) => <article className="ops-card" key={label}><span className="ops-metric-label">{label}</span><strong>{value}</strong><small>Last 24 hours</small></article>)}</div>
    <section className="ops-card ops-section"><div className="ops-card-heading"><div><h2>Cloud security controls</h2><p>{data.activeSessions} active sessions · {data.requestsPerMinute} requests per UTC minute per signed-in account.</p><p>Anonymous traffic shares a separate limit. Health checks and sign-out are exempt.</p><p>Login protection: 10 failed attempts per email within 15 minutes. Requests above the threshold are temporarily blocked.</p></div></div>
    <div className="ops-table-scroll"><table className="ops-table"><thead><tr><th>Account</th><th>Security event</th><th>Result</th><th>Time</th></tr></thead><tbody>{data.events.map(event => <tr key={event.id}><td>{event.actor}</td><td>{event.action.replaceAll('_', ' ').toLowerCase()}</td><td><span className={`ops-badge ${event.outcome === 'SUCCESS' ? 'ops-success' : 'ops-warning'}`}>{event.outcome.toLowerCase()}</span></td><td>{new Date(event.occurredAt).toLocaleString()}</td></tr>)}</tbody></table></div>
    {!data.events.length && <p className="ops-empty">No security events recorded in the last 24 hours.</p>}
    <p className="ops-caption">Latest 100 security events. Passwords, session tokens and attempted email addresses are not displayed. Limits are persisted in PostgreSQL and shared across backend instances.</p></section></>}
  </div>
}
