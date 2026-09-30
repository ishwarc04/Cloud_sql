import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router-dom'
import { Icon } from '../components/ui/Icon'
import { databaseService } from '../features/databases/databaseService'
import { formatBytes, storagePercent } from '../features/databases/format'
import type { DatabaseWorkspace, WorkspaceQueryResult, WorkspaceSchema } from '../features/databases/types'

function displayCell(value: unknown) {
  if (value === null) return <span className="null-value">NULL</span>
  return String(value)
}

export function DatabaseWorkspacePage() {
  const databaseId = useParams().databaseId ?? ''
  const [database, setDatabase] = useState<DatabaseWorkspace | null>(null)
  const [schema, setSchema] = useState<WorkspaceSchema | null>(null)
  const [query, setQuery] = useState('')
  const [result, setResult] = useState<WorkspaceQueryResult | null>(null)
  const [loading, setLoading] = useState(true)
  const [refreshing, setRefreshing] = useState(false)
  const [running, setRunning] = useState(false)
  const [pageError, setPageError] = useState<string | null>(null)
  const [queryError, setQueryError] = useState<string | null>(null)

  const refreshWorkspace = useCallback(async (quiet = false) => {
    if (!quiet) setRefreshing(true)
    try {
      const [databaseData, schemaData] = await Promise.all([databaseService.get(databaseId), databaseService.schema(databaseId)])
      setDatabase(databaseData); setSchema(schemaData); setPageError(null)
    } catch (requestError) {
      setPageError(requestError instanceof Error ? requestError.message : 'Database workspace could not be loaded.')
    } finally { setRefreshing(false); setLoading(false) }
  }, [databaseId])

  useEffect(() => {
    let active = true
    Promise.all([databaseService.get(databaseId), databaseService.schema(databaseId)]).then(([databaseData, schemaData]) => {
      if (!active) return
      setDatabase(databaseData); setSchema(schemaData)
    }).catch((requestError: unknown) => {
      if (active) setPageError(requestError instanceof Error ? requestError.message : 'Database workspace could not be loaded.')
    }).finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [databaseId])

  async function execute(event: FormEvent) {
    event.preventDefault()
    if (!query.trim() || running) return
    setRunning(true); setResult(null); setQueryError(null)
    try {
      setResult(await databaseService.execute(databaseId, query))
      await refreshWorkspace(true)
    } catch (requestError) {
      setQueryError(requestError instanceof Error ? requestError.message : 'SQL could not be executed.')
    } finally { setRunning(false) }
  }

  if (loading) return <section className="page"><div className="inline-state page-loading"><span className="spinner" />Loading database console…</div></section>
  if (pageError || !database) return <section className="page"><div className="inline-state error-state"><span>{pageError ?? 'Database not found.'}</span><Link to="/databases">Return to databases</Link></div></section>

  return <section className="page database-workspace-page">
    <div className="problem-context-bar"><Link to="/databases"><Icon name="chevron" />All databases</Link><span>Personal workspace</span></div>
    <header className="database-workspace-header"><div className="database-title"><span className="section-icon"><Icon name="database" /></span><div><h1>{database.name}</h1><span className="database-status"><i />{database.status.toLowerCase()} · H2 local</span></div></div><div className="workspace-quota"><div><span>Storage</span><strong>{formatBytes(database.storageUsedBytes)} / {formatBytes(database.storageLimitBytes)}</strong></div><div className="quota-track"><span style={{ width: `${storagePercent(database.storageUsedBytes, database.storageLimitBytes)}%` }} /></div></div></header>

    <div className="database-console">
      <aside className="schema-explorer">
        <div className="panel-heading"><div><span>Explorer</span><strong>Public schema</strong></div><button className="icon-button" type="button" onClick={() => void refreshWorkspace()} disabled={refreshing} aria-label="Refresh schema"><Icon name="refresh" /></button></div>
        <div className="schema-tree">
          {schema?.tables.map((table) => <section className="tree-table" key={table.name}><div><Icon name="database" /><strong>{table.name}</strong></div>{table.columns.map((column) => <div className="tree-column" key={column.name}><span>{column.name}</span><small>{column.dataType}{column.nullable ? '' : ' · required'}</small></div>)}</section>)}
          {schema?.tables.length === 0 && <div className="schema-empty"><strong>No tables</strong><p>Run a CREATE TABLE statement to define this database.</p></div>}
        </div>
      </aside>

      <div className="database-workbench">
        <form className="editor-panel" onSubmit={execute}>
          <div className="panel-heading editor-heading"><div><span>Query editor</span><strong>workspace.sql</strong></div><span className="read-only-note">Single statement</span></div>
          <div className="editor-shell database-editor"><div className="line-rail" aria-hidden="true">1<br />2<br />3<br />4<br />5<br />6<br />7<br />8<br />9<br />10</div><textarea aria-label="Workspace SQL query" value={query} onChange={(event) => setQuery(event.target.value)} placeholder={'CREATE TABLE customers (\n  id INT PRIMARY KEY,\n  name VARCHAR(100) NOT NULL\n);'} spellCheck={false} /></div>
          <div className="editor-footer"><span>H2 SQL · table operations · maximum 200 result rows</span><button className="run-button" type="submit" disabled={running || !query.trim()}>{running ? <><span className="spinner" />Running</> : <><Icon name="play" />Run query</>}</button></div>
        </form>

        <section className="results-panel" aria-live="polite">
          <div className="panel-heading"><div><span>Output</span><strong>Query result</strong></div>{result && <span className="execution-meta">{result.statementType} · {result.rows.length > 0 ? `${result.rows.length} rows` : `${result.affectedRows} affected`} · {result.executionTimeMs} ms</span>}</div>
          {running && <div className="inline-state result-empty"><span className="spinner" />Executing statement…</div>}
          {!running && queryError && <div className="query-error"><strong>Execution failed</strong><p>{queryError}</p></div>}
          {!running && !queryError && !result && <div className="inline-state result-empty">Run a statement to inspect its output.</div>}
          {!running && result && result.columns.length > 0 && <div className="data-scroll result-scroll"><table><thead><tr>{result.columns.map((column) => <th key={column}>{column}</th>)}</tr></thead><tbody>{result.rows.map((row, rowIndex) => <tr key={rowIndex}>{row.map((cell, cellIndex) => <td key={cellIndex}>{displayCell(cell)}</td>)}</tr>)}</tbody></table>{result.rows.length === 0 && <div className="inline-state">Query completed with no rows.</div>}</div>}
          {!running && result && result.columns.length === 0 && <div className="statement-success"><span><Icon name="check" /></span><div><strong>Statement completed</strong><p>{result.affectedRows} {result.affectedRows === 1 ? 'row' : 'rows'} affected in {result.executionTimeMs} ms.</p></div></div>}
        </section>
      </div>
    </div>
  </section>
}
