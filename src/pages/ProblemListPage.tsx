import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { DifficultyBadge } from '../features/problems/DifficultyBadge'
import { problemService } from '../features/problems/problemService'
import type { ProblemSummary } from '../features/problems/types'
import { Icon } from '../components/ui/Icon'
import { ProgressStatus } from '../features/problems/ProgressStatus'

type DifficultyFilter = 'All' | ProblemSummary['difficulty']

export function ProblemListPage() {
  const [problems, setProblems] = useState<ProblemSummary[]>([])
  const [search, setSearch] = useState('')
  const [difficulty, setDifficulty] = useState<DifficultyFilter>('All')
  const [category, setCategory] = useState('All')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const loadProblems = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      setProblems(await problemService.list())
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : 'Problems could not be loaded.')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    let active = true
    problemService.list().then((data) => {
      if (active) setProblems(data)
    }).catch((requestError: unknown) => {
      if (active) setError(requestError instanceof Error ? requestError.message : 'Problems could not be loaded.')
    }).finally(() => {
      if (active) setLoading(false)
    })
    return () => { active = false }
  }, [])

  const filteredProblems = useMemo(() => problems.filter((problem) => {
    const matchesDifficulty = difficulty === 'All' || problem.difficulty === difficulty
    const query = search.trim().toLowerCase()
    const matchesSearch = !query || `${problem.title} ${problem.topic}`.toLowerCase().includes(query)
    return matchesDifficulty && matchesSearch && (category === 'All' || problem.topic === category)
  }), [category, difficulty, problems, search])

  return <section className="page practice-list-page">
    <header className="page-header">
      <div><span className="eyebrow">SQL learning</span><h1>SQL Practice</h1><p>Build fluency with focused interview problems against real sample tables.</p></div>
      <span className="problem-count">{problems.length} problems</span>
    </header>

    <div className="problem-toolbar">
      <label className="problem-search"><Icon name="search" /><span className="sr-only">Search problems</span><input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Search title or topic" /></label>
      <label className="filter-control"><span>Difficulty</span><select value={difficulty} onChange={(event) => setDifficulty(event.target.value as DifficultyFilter)}><option>All</option><option>Easy</option><option>Medium</option><option>Hard</option></select></label>
      <label className="filter-control"><span>Category</span><select value={category} onChange={(event) => setCategory(event.target.value)}>{['All', 'Basic Select', 'Advanced Select', 'Aggregation', 'Basic Join', 'Advanced Join', 'Alternative Queries'].map(value => <option key={value}>{value}</option>)}</select></label>
    </div>

    <div className="problem-table" aria-live="polite">
      <div className="problem-table-head"><span>Status</span><span>Problem</span><span>Difficulty</span><span>Topic</span><span /></div>
      {loading && <div className="inline-state"><span className="spinner" />Loading problems…</div>}
      {error && <div className="inline-state error-state"><span>{error}</span><button type="button" onClick={() => void loadProblems()}>Retry</button></div>}
      {!loading && !error && filteredProblems.map((problem) => <Link className="problem-row" to={`/practice/${problem.id}`} key={problem.id}>
        <ProgressStatus status={problem.status} />
        <strong>{problem.title}<small className="problem-points">{problem.difficulty === 'Easy' ? 10 : problem.difficulty === 'Medium' ? 20 : 30} points</small></strong>
        <DifficultyBadge difficulty={problem.difficulty} />
        <span className="topic-label">{problem.topic}</span>
        <Icon name="chevron" />
      </Link>)}
      {!loading && !error && filteredProblems.length === 0 && <div className="inline-state">No problems match these filters.</div>}
    </div>
  </section>
}
