import { useEffect, useState } from 'react'
import { apiRequest } from '../../app/api'
interface Session { id: string; createdAt: string; expiresAt: string; current: boolean }
export function ActiveSessions() {
  const [sessions, setSessions] = useState<Session[]>([])
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [loaded, setLoaded] = useState(false)
  useEffect(() => {
    let active = true
    apiRequest<Session[]>('/api/auth/sessions').then(value => { if (active) { setSessions(value); setLoaded(true) } }).catch(e => { if (active) setError(e instanceof Error ? e.message : 'Unable to load sessions.') })
    return () => { active = false }
  }, [])
  async function revoke(id: string) {
    setBusy(true); setError('')
    try { await apiRequest(`/api/auth/sessions/${id}`, { method: 'DELETE' }); setSessions(await apiRequest<Session[]>('/api/auth/sessions')) }
    catch (e) { setError(e instanceof Error ? e.message : 'Unable to revoke session.') }
    finally { setBusy(false) }
  }
  return <section className="ops-card ops-section"><div className="ops-card-heading"><div><h2>Active sessions</h2><p>Revoke another session to prevent further API access from that sign-in. Use Sign out for this device.</p></div></div>
    {error && <p className="error-state" role="alert">{error}</p>}
    <div className="ops-table-scroll"><table className="ops-table"><thead><tr><th>Session</th><th>Signed in</th><th>Expires</th><th>Action</th></tr></thead><tbody>{sessions.map(session => <tr key={session.id}><td>{session.current ? 'Current session' : 'Other session'}<small>{session.id.slice(0, 8)}</small></td><td>{new Date(session.createdAt).toLocaleString()}</td><td>{new Date(session.expiresAt).toLocaleString()}</td><td>{session.current ? <span className="ops-badge ops-success">This sign-in</span> : <button className="secondary-button" disabled={busy} onClick={() => void revoke(session.id)}>Revoke access</button>}</td></tr>)}</tbody></table></div>
    {!loaded && !error && <p className="ops-empty">Loading sessions…</p>}
  </section>
}
