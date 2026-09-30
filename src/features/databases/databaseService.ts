import type { DatabaseListResponse, DatabaseWorkspace, WorkspaceQueryResult, WorkspaceSchema } from './types'
import { apiUrl } from '../../app/api'

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const response = await fetch(apiUrl(path), options)
  if (response.status === 204) return undefined as T
  const payload = await response.json().catch(() => null) as T | { error?: string } | null
  if (!response.ok) {
    const message = payload && typeof payload === 'object' && 'error' in payload && payload.error
      ? payload.error
      : `Request failed with status ${response.status}.`
    throw new Error(message)
  }
  return payload as T
}

const jsonRequest = (method: string, body: unknown): RequestInit => ({
  method,
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify(body),
})

export const databaseService = {
  list: () => request<DatabaseListResponse>('/api/databases'),
  get: (id: string) => request<DatabaseWorkspace>(`/api/databases/${id}`),
  create: (name: string) => request<DatabaseWorkspace>('/api/databases', jsonRequest('POST', { name })),
  delete: (id: string) => request<void>(`/api/databases/${id}`, { method: 'DELETE' }),
  execute: (id: string, query: string) => request<WorkspaceQueryResult>(`/api/databases/${id}/execute`, jsonRequest('POST', { query })),
  schema: (id: string) => request<WorkspaceSchema>(`/api/databases/${id}/schema`),
}
