import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { apiRequest } from '../app/api'
import { useAuth } from '../app/auth/AuthContext'
interface Breakdown { name: string; solved: number; total: number }
interface Dashboard {
  totalPoints: number; solvedProblems: number; attemptCount: number
  difficultyProgress: Breakdown[]; categoryProgress: Breakdown[]
  recentSubmissions: { id: string; problemId: number; title: string; difficulty: string; category: string; outcome: string; pointsAwarded: number; submittedAt: string }[]
}
export function UserDashboardPage() {
  const { user } = useAuth()
  const [data, setData] = useState<Dashboard | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [retry, setRetry] = useState(0)
  useEffect(() => {
    let active = true
    apiRequest<Dashboard>('/api/dashboard').then(value => { if (active) { setData(value); setError(null) } }).catch((failure: unknown) => { if (active) setError(failure instanceof Error ? failure.message : 'Unable to load progress.') })
    return () => { active = false }
  }, [retry])
  function progress(title: string, rows: Breakdown[]) {
    return <section className="dashboard-panel"><h2>{title}</h2>{rows.map(row => <div className="progress-entry" key={row.name}><div><span>{row.name}</span><strong>{row.solved} / {row.total}</strong></div><progress value={row.solved} max={Math.max(row.total, 1)} aria-label={`${row.name}: ${row.solved} of ${row.total} solved`} /></div>)}</section>
  }
  return <section className="page">
    <header className="page-header"><div><span className="eyebrow">Your learning journey</span><h1>Hello, {user?.name}</h1><p>Build your SQL skills, one solution at a time.</p></div><Link className="primary-button" to="/practice">Practice SQL</Link></header>
    {error && <div className="inline-state error-state" role="alert">{error}<button onClick={() => setRetry(value => value + 1)}>Retry</button></div>}
    {!data && !error && <div className="inline-state" role="status">Loading your progress…</div>}
    {data && <><div className="dashboard-stats">{[['Total points', data.totalPoints], ['Solved problems', data.solvedProblems], ['Total attempts', data.attemptCount]].map(([label, value]) => <div className="dashboard-panel" key={label}><span>{label}</span><strong className="stat-value">{value}</strong></div>)}</div>
      <p className="points-guide">Earn 10 points for Easy, 20 for Medium, and 30 for Hard. Points count once per problem.</p>
      <div className="dashboard-grid">{progress('Difficulty progress', data.difficultyProgress)}{progress('Category progress', data.categoryProgress)}</div>
      <section className="dashboard-panel submissions-panel"><h2>Recent submissions</h2>{data.recentSubmissions.length === 0 ? <p>No submissions yet. <Link to="/practice">Choose your first problem</Link> to get started.</p> : <div className="submission-table"><table><thead><tr><th>Problem</th><th>Result</th><th>Points earned</th><th>Submitted</th></tr></thead><tbody>{data.recentSubmissions.map(item => <tr key={item.id}><td><Link to={`/practice/${item.problemId}`}>{item.title}</Link><small>{item.difficulty} · {item.category}</small></td><td>{item.outcome === 'ACCEPTED' ? 'Accepted' : item.outcome === 'ERROR' ? 'Query error' : 'Try again'}</td><td>+{item.pointsAwarded}</td><td>{new Date(item.submittedAt).toLocaleString()}</td></tr>)}</tbody></table></div>}</section>
    </>}
  </section>
}
