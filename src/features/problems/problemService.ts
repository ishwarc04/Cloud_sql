import type { ProblemDetail, ProblemSummary, QueryResult, SubmissionResult } from './types'
import { apiFetch } from '../../app/api'

class ApiError extends Error {
  readonly status: number

  constructor(message: string, status: number) {
    super(message)
    this.status = status
  }
}

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const response = await apiFetch(path, options)
  const payload = await response.json().catch(() => null) as T | { error?: string } | null

  if (!response.ok) {
    const message = payload && typeof payload === 'object' && 'error' in payload && payload.error
      ? payload.error
      : `Request failed with status ${response.status}.`
    throw new ApiError(message, response.status)
  }

  return payload as T
}

export const problemService = {
  list: () => request<ProblemSummary[]>('/api/problems'),
  get: (id: number) => request<ProblemDetail>(`/api/problems/${id}`),
  execute: (id: number, query: string) => request<QueryResult>(`/api/problems/${id}/execute`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ query }),
  }),
  submit: (id: number, query: string) => request<SubmissionResult>(`/api/problems/${id}/submit`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ query }),
  }),
}
