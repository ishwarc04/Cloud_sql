import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { Icon } from '../components/ui/Icon'
import { databaseService } from '../features/databases/databaseService'
import { formatBytes, formatCreatedAt, storagePercent } from '../features/databases/format'
import type { DatabaseListResponse } from '../features/databases/types'
import { WorkspaceBackupsPanel } from '../features/databases/WorkspaceBackupsPanel'

export function DatabaseListPage() {
  const [data, setData] = useState<DatabaseListResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [deleting, setDeleting] = useState<string | null>(null)
  const dialog = useRef<HTMLDialogElement>(null)

  const load = useCallback(async () => {
    setError(null)
    try { setData(await databaseService.list()) }
    catch (requestError) { setError(requestError instanceof Error ? requestError.message : 'Databases could not be loaded.') }
    finally { setLoading(false) }
  }, [])

  useEffect(() => {
    let active = true
    databaseService.list().then((response) => { if (active) setData(response) })
      .catch((requestError: unknown) => { if (active) setError(requestError instanceof Error ? requestError.message : 'Databases could not be loaded.') })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [])

  async function deleteDatabase(id: string, name: string) {
    if (!window.confirm(`Delete “${name}” and all of its data? This cannot be undone.`)) return
    setDeleting(id)
    setError(null)
    try { await databaseService.delete(id); await load() }
    catch (requestError) { setError(requestError instanceof Error ? requestError.message : 'Database could not be deleted.') }
    finally { setDeleting(null) }
  }

  const quotaReached = data ? data.quota.databaseCount >= data.quota.maximumDatabases : false

  return <section className="page">
    <header className="page-header">
      <div><span className="eyebrow">Cloud workspace</span><h1>My Databases</h1><p>Create isolated SQL environments and manage them within your local resource quota.</p></div>
      <button className="primary-button button-with-icon" type="button" onClick={() => dialog.current?.showModal()} disabled={quotaReached}><Icon name="plus" />Create database</button>
    </header>

    {data && <div className="database-summary">
      <div><span>Databases</span><strong>{data.quota.databaseCount} / {data.quota.maximumDatabases}</strong></div>
      <div><span>Total storage</span><strong>{formatBytes(data.quota.totalStorageUsedBytes)} / {formatBytes(data.quota.totalStorageLimitBytes)}</strong></div>
      <div className="quota-track" aria-label="Total storage usage"><span style={{ width: `${storagePercent(data.quota.totalStorageUsedBytes, data.quota.totalStorageLimitBytes)}%` }} /></div>
    </div>}

    {error && <div className="database-alert">{error}<button type="button" onClick={() => void load()}>Retry</button></div>}
    {loading && <div className="inline-state"><span className="spinner" />Loading databases…</div>}
    {!loading && data && <div className="database-list">
      <div className="database-list-head"><span>Database</span><span>Status</span><span>Created</span><span>Storage</span><span>Actions</span></div>
      {data.databases.map((database) => <div className="database-list-row" key={database.id}>
        <div className="database-identity"><span className="section-icon"><Icon name="database" /></span><div><strong>{database.name}</strong><small>H2 workspace</small></div></div>
        <span className="database-status"><i />{database.status.toLowerCase()}</span>
        <span>{formatCreatedAt(database.createdAt)}</span>
        <div className="database-storage"><span>{formatBytes(database.storageUsedBytes)} / {formatBytes(database.storageLimitBytes)}</span><div className="quota-track"><span style={{ width: `${storagePercent(database.storageUsedBytes, database.storageLimitBytes)}%` }} /></div></div>
        <div className="database-actions"><Link className="secondary-button" to={`/databases/${database.id}`}>Open</Link><button className="icon-button danger-button" type="button" aria-label={`Delete ${database.name}`} onClick={() => void deleteDatabase(database.id, database.name)} disabled={deleting === database.id}>{deleting === database.id ? <span className="spinner" /> : <Icon name="trash" />}</button></div>
      </div>)}
      {data.databases.length === 0 && <div className="database-empty"><div><strong>No databases yet</strong><p>Create an isolated workspace to start building tables and running SQL.</p></div><button type="button" className="secondary-button" onClick={() => dialog.current?.showModal()}>Create your first database</button></div>}
    </div>}
    {quotaReached && <p className="quota-note">Database quota reached. Delete a workspace before creating another.</p>}
    <CreateDatabaseDialog ref={dialog} quota={data?.quota} onCreated={() => { dialog.current?.close(); void load() }} />
    <WorkspaceBackupsPanel />
  </section>
}

function CreateDatabaseDialog({ ref, quota, onCreated }: { ref: React.RefObject<HTMLDialogElement | null>; quota?: DatabaseListResponse['quota']; onCreated: () => void }) {
  const [name, setName] = useState('')
  const [creating, setCreating] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (!name.trim()) return
    setCreating(true); setError(null)
    try { await databaseService.create(name); setName(''); onCreated() }
    catch (requestError) { setError(requestError instanceof Error ? requestError.message : 'Database could not be created.') }
    finally { setCreating(false) }
  }

  return <dialog className="database-dialog" ref={ref} onClose={() => { setError(null); setName('') }}>
    <form onSubmit={submit}>
      <div className="dialog-heading"><div><span className="eyebrow">New workspace</span><h2>Create database</h2></div><button className="icon-button" type="button" onClick={() => ref.current?.close()} aria-label="Close dialog"><Icon name="close" /></button></div>
      <label className="field-label" htmlFor="database-name">Database name</label>
      <input id="database-name" value={name} onChange={(event) => setName(event.target.value)} minLength={3} maxLength={60} pattern="[A-Za-z0-9][A-Za-z0-9 _-]*" placeholder="Customer analytics" autoFocus required />
      <small className="field-help">3–60 characters. Letters, numbers, spaces, hyphens, and underscores.</small>
      <div className="dialog-quota"><span>Workspace quota</span><strong>{quota?.databaseCount ?? 0} of {quota?.maximumDatabases ?? 2} used</strong></div>
      {error && <p className="form-error">{error}</p>}
      <div className="dialog-actions"><button className="secondary-button" type="button" onClick={() => ref.current?.close()}>Cancel</button><button className="primary-button" type="submit" disabled={creating || name.trim().length < 3}>{creating ? 'Creating…' : 'Create database'}</button></div>
    </form>
  </dialog>
}
