import { useEffect, useState } from 'react'
import { apiRequest } from '../app/api'
interface Leader { rank: number; name: string; points: number; solved: number; you: boolean; reward: string }
interface Board { leaders: Leader[]; yourRank: Leader | null }
export function LeaderboardPage() {
  const [board, setBoard] = useState<Board | null>(null)
  const [error, setError] = useState('')
  const [revision, setRevision] = useState(0)
  useEffect(() => { let active = true; apiRequest<Board>('/api/leaderboard').then(data => { if (active) { setBoard(data); setError('') } }).catch((e: unknown) => { if (active) setError(e instanceof Error ? e.message : 'Unable to load rankings.') }); return () => { active = false } }, [revision])
  return <section className="page"><header className="page-header"><div><span className="eyebrow">Learn · compete · celebrate</span><h1>Leaderboard</h1><p>Every first successful solution brings you closer to the top.</p></div><button className="secondary-button" onClick={() => setRevision(r => r + 1)}>Refresh rankings</button></header>
    {error && <p role="alert" className="error-state">{error}</p>}
    {board ? <><div className="learner-reward-grid">{['SQL Champion', 'Query Master', 'Rising Star'].map((title, i) => { const leader = board.leaders[i]; return <article className={`ops-card reward-card reward-${i}`} key={title}><span className="reward-medal" aria-hidden="true">{['🏆', '🥈', '🥉'][i]}</span><span className="eyebrow">Place {i + 1} · Virtual trophy</span><h2>{title}</h2><strong>{leader && leader.points > 0 ? leader.name : 'Your place awaits'}</strong><p>{leader && leader.points > 0 ? `${leader.points} points · ${leader.solved} solved` : 'Solve your first question to compete.'}</p></article> })}</div>
    {board.yourRank && <div className="learner-rank-banner"><strong>Your rank: #{board.yourRank.rank}</strong><span>{board.yourRank.points} points · {board.yourRank.solved} solved</span><span>{board.yourRank.reward || 'Keep practicing to earn a top-three trophy.'}</span></div>}
    <div className="ops-card ops-table-scroll"><table className="ops-table"><thead><tr><th>Rank</th><th>Learner</th><th>Points</th><th>Solved</th><th>Reward</th></tr></thead><tbody>{board.leaders.map(row => <tr key={row.rank} className={row.you ? 'leader-you' : ''}><td>#{row.rank}</td><td><strong>{row.name}{row.you ? ' (You)' : ''}</strong></td><td>{row.points}</td><td>{row.solved}</td><td>{row.reward || '—'}</td></tr>)}</tbody></table></div></> : !error && <p>Loading rankings…</p>}
    <p className="ops-caption">All-time top 100 learners. Ranked by points, then solved count, then account creation time and account ID. Admins are excluded. Top-three trophies update with the rankings and are virtual recognition; no cash or physical prizes.</p>
  </section>
}
