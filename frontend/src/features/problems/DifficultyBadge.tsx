import type { ProblemSummary } from './types'

export function DifficultyBadge({ difficulty }: Pick<ProblemSummary, 'difficulty'>) {
  return <span className={`difficulty difficulty-${difficulty.toLowerCase()}`}>{difficulty}</span>
}
