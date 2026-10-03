import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { apiRequest } from '../../app/api'
import { formatBytes } from './format'

interface Backup { id: string; sourceWorkspaceId: string; name: string; createdAt: string; sizeBytes: number; tableCount: number; rowCount: number }
export function WorkspaceBackupsPanel({ database }: { database?: { id: string; name: string } }) {
  const [backups, setBackups] = useState<Backup[]>([])
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)
  const [restoreId, setRestoreId] = useState<string | null>(null)
  const [restoreName, setRestoreName] = useState('')
  const [deleteId, setDeleteId] = useState<string | null>(null)
  const navigate = useNavigate()
  const load = useCallback(async () => {
    try { setBackups(await apiRequest<Backup[]>('/api/backups')); setError(null) }
    catch (failure) { setError(failure instanceof Error ? failure.message : 'Unable to load backups.') }
    finally { setLoading(false) }
  }, [])
  useEffect(() => {
    let active = true
    apiRequest<Backup[]>('/api/backups').then(value => { if (active) { setBackups(value); setError(null) } }).catch((failure: unknown) => { if (active) setError(failure instanceof Error ? failure.message : 'Unable to load backups.') }).finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [])
  async function create() {
    if (!database) return
    setBusy(true); setError(null); setMessage(null)
    try { await apiRequest(`/api/databases/${database.id}/backups`, { method: 'POST' }); await load(); setMessage('Snapshot saved. Download a copy to keep it outside the platform database.') }
    catch (failure) { setError(failure instanceof Error ? failure.message : 'Backup failed.') }
    finally { setBusy(false) }
  }
  async function download(backup: Backup) {
    setBusy(true); setError(null)
    try {
      const snapshot = await apiRequest<unknown>(`/api/backups/${backup.id}`)
      const url = URL.createObjectURL(new Blob([JSON.stringify(snapshot, null, 2)], { type: 'application/json' }))
      const link = document.createElement('a'); link.href = url; link.download = `cloudsql-snapshot-${backup.id}.json`; link.click(); URL.revokeObjectURL(url)
      setMessage('Download prepared. Keep this file private; it contains your workspace data.')
    } catch (failure) { setError(failure instanceof Error ? failure.message : 'Download failed.') }
    finally { setBusy(false) }
  }
  async function restore(event: FormEvent) {
    event.preventDefault(); if (!restoreId) return
    setBusy(true); setError(null)
    try {
      const created = await apiRequest<{ id: string }>(`/api/backups/${restoreId}/restore`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ name: restoreName.trim() }) })
      navigate(`/databases/${created.id}`)
      setRestoreId(null); setMessage('Snapshot restored into a new workspace.')
    } catch (failure) { setError(failure instanceof Error ? failure.message : 'Restore failed.') }
    finally { setBusy(false) }
  }
  async function remove() {
    if (!deleteId) return
    setBusy(true); setError(null)
    try { await apiRequest(`/api/backups/${deleteId}`, { method: 'DELETE' }); setDeleteId(null); await load(); setMessage('Saved snapshot deleted. Existing workspaces were preserved.') }
    catch (failure) { setError(failure instanceof Error ? failure.message : 'Delete failed.') }
    finally { setBusy(false) }
  }
  return <section className="ops-card ops-section backup-panel" aria-label="Workspace snapshots">
    <div className="ops-card-heading"><div><span className="eyebrow">Cloud storage and recovery</span><h2>Workspace snapshots</h2><p>{backups.length} / 3 saved · 2 MB and 5,000 rows per snapshot</p></div>{database && <button className="primary-button" disabled={busy || loading || backups.length >= 3} onClick={() => void create()}>{busy ? 'Please wait…' : 'Save snapshot'}</button>}</div>
    {error && <div className="database-alert error-state" role="alert">{error}<button onClick={() => void load()} disabled={busy}>Refresh backups</button></div>}
    {message && <p className="backup-message" role="status">{message}</p>}
    <div className="ops-table-scroll"><table className="ops-table"><thead><tr><th>Source workspace</th><th>Tables / rows</th><th>Snapshot size</th><th>Saved</th><th>Recovery</th></tr></thead><tbody>{backups.map(backup => <tr key={backup.id}><td><strong>{backup.name}</strong><small>{backup.id.slice(0, 8)}</small></td><td>{backup.tableCount} tables<small>{backup.rowCount} rows</small></td><td>{formatBytes(backup.sizeBytes)}</td><td>{new Date(backup.createdAt).toLocaleString()}</td><td><div className="backup-row-actions"><button className="ops-text-button" disabled={busy} onClick={() => void download(backup)}>Download</button><button className="secondary-button" disabled={busy} onClick={() => { setRestoreId(backup.id); setDeleteId(null); setRestoreName(`${backup.name.slice(0, 48)} restored`) }}>Restore</button><button className="ops-text-button" disabled={busy} onClick={() => { setDeleteId(backup.id); setRestoreId(null) }}>Delete</button></div></td></tr>)}</tbody></table>{(loading || backups.length === 0) && <div className="ops-empty">{loading ? 'Loading snapshots…' : database ? 'Save a snapshot before experimenting with your tables.' : 'Open a database workspace to save your first snapshot.'}</div>}</div>
    {restoreId && <form className="backup-restore-form" onSubmit={event => void restore(event)}><label>New workspace name<input aria-label="Restored workspace name" value={restoreName} onChange={event => setRestoreName(event.target.value)} required maxLength={60} disabled={busy} /></label><span>Restores into a new workspace within your database and storage quotas.</span><button className="primary-button" disabled={busy}>{busy ? 'Restoring…' : 'Restore to new workspace'}</button><button className="secondary-button" type="button" disabled={busy} onClick={() => setRestoreId(null)}>Cancel</button></form>}
    {deleteId && <div className="backup-delete-confirm"><span>Delete this saved snapshot permanently? Your working database is unaffected.</span><button className="secondary-button" disabled={busy} onClick={() => void remove()}>Delete snapshot</button><button className="secondary-button" disabled={busy} onClick={() => setDeleteId(null)}>Cancel</button></div>}
    <p className="ops-caption">Logical snapshots preserve supported table columns, primary keys, and rows. Other constraints and indexes are excluded; generated/default columns and foreign keys are currently unsupported. Saved copies share the Neon database—download a copy for independent storage.</p>
  </section>
}
