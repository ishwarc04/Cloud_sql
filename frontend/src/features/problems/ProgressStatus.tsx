import type { ProblemStatus } from './types'

const labels: Record<ProblemStatus, string> = {
  NOT_STARTED: 'Not started',
  ATTEMPTED: 'Attempted',
  SOLVED: 'Solved',
}

export function ProgressStatus({ status, compact = false }: { status: ProblemStatus; compact?: boolean }) {
  return <span className={`progress-status status-${status.toLowerCase()} ${compact ? 'is-compact' : ''}`}><span />{labels[status]}</span>
}
