import { useState, type CSSProperties } from 'react'
import type { AdminAnalytics, DailyActivity, AdminProblem } from './types'

export function ActivityChart({ days }: { days: DailyActivity[] }) {
  const [metric, setMetric] = useState<'submissions' | 'registrations'>('submissions')
  const [selected, setSelected] = useState<number | null>(null)
  const peak = Math.ceil(Math.max(1, ...days.map(day => day[metric])) / 2) * 2
  const width = 640, left = 36, top = 14, height = 150, base = top + height
  const step = (width - left - 12) / Math.max(days.length, 1)
  const chosen = days[selected ?? days.length - 1]
  return <section className="ops-card ops-activity"><div className="ops-card-heading"><div><span className="eyebrow">Last 14 days · UTC</span><h2>Platform activity</h2></div><div className="ops-switch" aria-label="Chart metric">{(['submissions', 'registrations'] as const).map(value => <button key={value} aria-pressed={metric === value} onClick={() => setMetric(value)}>{value === 'submissions' ? 'Submissions' : 'Signups'}</button>)}</div></div>
    <div className="ops-chart-summary"><strong>{days.reduce((sum, day) => sum + day[metric], 0)}</strong><span>{metric} over 14 days</span>{chosen && <small>{chosen.date}: {chosen[metric]}{metric === 'submissions' ? ` · ${chosen.accepted} accepted · ${chosen.errors} errors` : ''}</small>}</div>
    <svg className="ops-activity-svg" viewBox={`0 0 ${width} 200`} role="img" aria-label={`Daily ${metric} over the last 14 UTC dates`}>
      {[0, .5, 1].map(fraction => <g key={fraction}><line x1={left} x2={width - 10} y1={base - height * fraction} y2={base - height * fraction} className="ops-gridline" /><text x={left - 8} y={base - height * fraction + 4} textAnchor="end">{Math.round(peak * fraction)}</text></g>)}
      {days.map((day, index) => {
        const totalHeight = day[metric] / peak * height, acceptedHeight = metric === 'submissions' ? day.accepted / peak * height : totalHeight
        const x = left + index * step + 7, barWidth = Math.max(4, step - 14)
        return <g key={day.date} tabIndex={0} onMouseEnter={() => setSelected(index)} onFocus={() => setSelected(index)} aria-label={`${day.date}: ${day[metric]} ${metric}`}>
          <title>{day.date}: {day[metric]} {metric}</title><rect x={x - 3} y={top} width={barWidth + 6} height={height} fill="transparent" />
          <rect x={x} y={base - totalHeight} width={barWidth} height={totalHeight} rx="3" className="ops-bar-total" opacity={selected === null || selected === index ? 1 : .6} />
          <rect x={x} y={base - acceptedHeight} width={barWidth} height={acceptedHeight} rx="3" className="ops-bar-accepted" />
          {(index % 3 === 0 || index === days.length - 1) && <text x={x + barWidth / 2} y={187} textAnchor="middle">{day.date.slice(5)}</text>}
        </g>
      })}
    </svg>
    <div className="ops-chart-legend"><span><i className="legend-success" />{metric === 'submissions' ? 'Accepted' : 'New accounts'}</span>{metric === 'submissions' && <span><i className="legend-accent" />Other submissions</span>}<span>Hover or focus a day for counts</span></div>
  </section>
}

export function OutcomeChart({ summary }: { summary: AdminAnalytics['summary'] }) {
  const total = summary.totalSubmissions
  const acceptedPercent = total ? summary.acceptedSubmissions / total * 100 : 0
  const wrongEnd = total ? (summary.acceptedSubmissions + summary.wrongAnswers) / total * 100 : 0
  const style: CSSProperties = { background: total ? `conic-gradient(var(--success) 0% ${acceptedPercent}%, var(--accent) ${acceptedPercent}% ${wrongEnd}%, var(--danger) ${wrongEnd}% 100%)` : 'var(--border)' }
  return <section className="ops-card"><div className="ops-card-heading"><div><span className="eyebrow">All recorded submissions</span><h2>Submission outcomes</h2></div></div><div className="ops-outcomes">
    <div className="ops-donut" style={style} role="img" aria-label={`${summary.acceptedSubmissions} accepted, ${summary.wrongAnswers} wrong answers, ${summary.queryErrors} query errors`}><div><strong>{acceptedPercent.toFixed(1)}%</strong><span>accepted</span></div></div>
    <div className="ops-outcome-list">{[['Accepted', summary.acceptedSubmissions, 'success'], ['Wrong answer', summary.wrongAnswers, 'accent'], ['Query error', summary.queryErrors, 'danger']].map(([label, count, color]) => <div key={label}><span><i className={`legend-${color}`} />{label}</span><strong>{count}</strong></div>)}</div>
  </div><p className="ops-caption">Acceptance counts include repeat solves. Points are awarded only once.</p></section>
}

export function CategoryChart({ problems }: { problems: AdminProblem[] }) {
  const categories = ['Basic Select', 'Advanced Select', 'Aggregation', 'Basic Join', 'Advanced Join', 'Alternative Queries']
  const rows = categories.map(name => ({ name, solved: problems.filter(p => p.category === name).reduce((sum, p) => sum + p.solvedUsers, 0) }))
  const maximum = Math.max(1, ...rows.map(row => row.solved))
  return <section className="ops-card"><div className="ops-card-heading"><div><span className="eyebrow">Unique account–problem completions</span><h2>Learning by category</h2></div></div><div className="ops-category-bars">{rows.map(row => <div key={row.name}><span>{row.name}</span><div className="ops-bar-track"><span style={{ width: `${row.solved / maximum * 100}%` }} /></div><strong>{row.solved}</strong></div>)}</div></section>
}
