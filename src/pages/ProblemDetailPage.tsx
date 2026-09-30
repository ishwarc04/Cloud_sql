import { useEffect, useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router-dom'
import { Icon } from '../components/ui/Icon'
import { DifficultyBadge } from '../features/problems/DifficultyBadge'
import { problemService } from '../features/problems/problemService'
import { ProgressStatus } from '../features/problems/ProgressStatus'
import type { ProblemDetail, QueryResult, SubmissionResult } from '../features/problems/types'

function displayCell(value: unknown) {
  if (value === null) return <span className="null-value">NULL</span>
  return String(value)
}

export function ProblemDetailPage() {
  const problemId = Number(useParams().problemId)
  return <ProblemWorkspace key={problemId} problemId={problemId} />
}

function ProblemWorkspace({ problemId }: { problemId: number }) {
  const [problem, setProblem] = useState<ProblemDetail | null>(null)
  const [query, setQuery] = useState('')
  const [result, setResult] = useState<QueryResult | null>(null)
  const [loadingProblem, setLoadingProblem] = useState(true)
  const [running, setRunning] = useState<'run' | 'submit' | null>(null)
  const [submission, setSubmission] = useState<SubmissionResult | null>(null)
  const [pageError, setPageError] = useState<string | null>(null)
  const [queryError, setQueryError] = useState<string | null>(null)

  useEffect(() => {
    let active = true
    problemService.get(problemId).then((data) => {
      if (!active) return
      setProblem(data)
      setQuery(data.starterQuery)
    }).catch((error: unknown) => {
      if (active) setPageError(error instanceof Error ? error.message : 'Problem could not be loaded.')
    }).finally(() => {
      if (active) setLoadingProblem(false)
    })
    return () => { active = false }
  }, [problemId])

  async function runQuery(event: FormEvent) {
    event.preventDefault()
    if (!query.trim() || running) return
    setRunning('run')
    setResult(null)
    setSubmission(null)
    setQueryError(null)
    try {
      setResult(await problemService.execute(problemId, query))
    } catch (error) {
      setQueryError(error instanceof Error ? error.message : 'The query could not be executed.')
    } finally {
      setRunning(null)
    }
  }

  async function submitQuery() {
    if (!query.trim() || running) return
    setRunning('submit')
    setResult(null)
    setSubmission(null)
    setQueryError(null)
    try {
      const response = await problemService.submit(problemId, query)
      setSubmission(response)
      setResult(response)
      setProblem((current) => current ? { ...current, status: response.status, attempts: response.attempts } : current)
    } catch (error) {
      setQueryError(error instanceof Error ? error.message : 'The solution could not be submitted.')
    } finally {
      setRunning(null)
    }
  }

  if (loadingProblem) return <section className="page"><div className="inline-state page-loading"><span className="spinner" />Loading problem workspace…</div></section>
  if (pageError || !problem) return <section className="page"><div className="inline-state error-state"><span>{pageError ?? 'Problem not found.'}</span><Link to="/practice">Return to problems</Link></div></section>

  return <section className="page practice-workspace">
    <div className="problem-context-bar"><Link to="/practice"><Icon name="chevron" />All problems</Link><span>Problem {problem.id.toString().padStart(2, '0')}</span></div>
    <header className="problem-detail-header"><div><h1>{problem.title}</h1><p>{problem.description}</p></div><div className="problem-metadata"><ProgressStatus status={problem.status} compact /><DifficultyBadge difficulty={problem.difficulty} /><span className="topic-label">{problem.topic}</span>{problem.attempts > 0 && <span className="attempt-count">{problem.attempts} {problem.attempts === 1 ? 'attempt' : 'attempts'}</span>}</div></header>

    <div className="practice-layout">
      <aside className="schema-panel">
        <div className="panel-heading"><div><span>Sample database</span><strong>Schema and data</strong></div><span className="engine-tag">H2 · isolated</span></div>
        {problem.tables.map((table) => <section className="schema-table" key={table.name}>
          <div className="schema-table-name"><Icon name="database" /><strong>{table.name}</strong></div>
          <div className="data-scroll"><table><thead><tr>{table.columns.map((column) => <th key={column.name}><span>{column.name}</span><small>{column.type}</small></th>)}</tr></thead><tbody>{table.sampleRows.map((row, rowIndex) => <tr key={rowIndex}>{row.map((cell, cellIndex) => <td key={cellIndex}>{displayCell(cell)}</td>)}</tr>)}</tbody></table></div>
        </section>)}
      </aside>

      <div className="query-workbench">
        <form className="editor-panel" onSubmit={runQuery}>
          <div className="panel-heading editor-heading"><div><span>Query editor</span><strong>solution.sql</strong></div><span className="read-only-note">SELECT only</span></div>
          <div className="editor-shell"><div className="line-rail" aria-hidden="true">1<br />2<br />3<br />4<br />5<br />6<br />7<br />8</div><textarea aria-label="SQL query" value={query} onChange={(event) => setQuery(event.target.value)} spellCheck={false} /></div>
          <div className="editor-footer"><span>H2 SQL · maximum 200 rows</span><div className="editor-actions"><button className="run-button run-secondary" type="submit" disabled={Boolean(running) || !query.trim()}>{running === 'run' ? <><span className="spinner" />Running</> : 'Run'}</button><button className="run-button" type="button" onClick={() => void submitQuery()} disabled={Boolean(running) || !query.trim()}>{running === 'submit' ? <><span className="spinner" />Judging</> : <><Icon name="check" />Submit solution</>}</button></div></div>
        </form>

        <section className="results-panel" aria-live="polite">
          <div className="panel-heading"><div><span>Output</span><strong>Query result</strong></div>{result && <span className="execution-meta">{result.rows.length} rows · {result.executionTimeMs} ms</span>}</div>
          {running && <div className="inline-state result-empty"><span className="spinner" />{running === 'submit' ? 'Checking solution…' : 'Executing query…'}</div>}
          {!running && queryError && <div className="query-error"><strong>Execution failed</strong><p>{queryError}</p></div>}
          {!running && !queryError && !result && <div className="inline-state result-empty">Run the query to inspect its result.</div>}
          {!running && result && <>{submission && <div className={`submission-verdict ${submission.correct ? 'is-correct' : 'is-incorrect'}`}><span className="verdict-icon"><Icon name={submission.correct ? 'check' : 'close'} /></span><div><strong>{submission.correct ? 'Solution accepted' : 'Result does not match'}</strong><p>{submission.message}</p></div></div>}<div className="data-scroll result-scroll"><table><thead><tr>{result.columns.map((column) => <th key={column}>{column}</th>)}</tr></thead><tbody>{result.rows.map((row, rowIndex) => <tr key={rowIndex}>{row.map((cell, cellIndex) => <td key={cellIndex}>{displayCell(cell)}</td>)}</tr>)}</tbody></table>{result.rows.length === 0 && <div className="inline-state">Query completed with no rows.</div>}</div></>}
        </section>
      </div>
    </div>
  </section>
}
