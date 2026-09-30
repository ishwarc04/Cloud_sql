export interface ProblemSummary {
  id: number
  slug: string
  title: string
  difficulty: 'Easy' | 'Medium' | 'Hard'
  topic: string
  status: ProblemStatus
  attempts: number
}

export type ProblemStatus = 'NOT_STARTED' | 'ATTEMPTED' | 'SOLVED'

export interface ColumnPreview {
  name: string
  type: string
}

export interface TablePreview {
  name: string
  columns: ColumnPreview[]
  sampleRows: unknown[][]
}

export interface ProblemDetail extends ProblemSummary {
  description: string
  starterQuery: string
  tables: TablePreview[]
}

export interface QueryResult {
  columns: string[]
  rows: unknown[][]
  executionTimeMs: number
  error: string | null
}

export interface SubmissionResult extends QueryResult {
  correct: boolean
  message: string
  status: ProblemStatus
  attempts: number
}
