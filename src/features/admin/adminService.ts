import type { AdminAnalytics, AdminDatabase, AdminOverview, AdminUserDetail } from './types'
import { apiFetch, apiRequest } from '../../app/api'

async function request<T>(path: string): Promise<T> {
  const response = await apiFetch(path)
  const payload = await response.json().catch(() => null) as T | { error?: string } | null
  if (!response.ok) {
    const message = payload && typeof payload === 'object' && 'error' in payload && payload.error
      ? payload.error : `Request failed with status ${response.status}.`
    throw new Error(message)
  }
  return payload as T
}

export const adminService = {
  analytics: () => request<AdminAnalytics>('/api/admin/analytics'),
  user: (id: string) => request<AdminUserDetail>(`/api/admin/users/${encodeURIComponent(id)}`),
  overview: () => request<AdminOverview>('/api/admin/overview'),
  databases: () => request<AdminDatabase[]>('/api/admin/databases'),
  createUser: (input: { name: string; email: string; password: string }) => apiRequest<{ id: string; name: string; email: string; role: 'USER' }>('/api/admin/users', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(input) }),
  createQuestion: (input: CreateQuestion) => apiRequest<{ id: number; title: string }>('/api/admin/problems', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(input) }),
}

export interface CreateQuestion {
  title: string; difficulty: string; category: string; description: string; starterQuery: string; solutionQuery: string
  tables: { name: string; columns: { name: string; type: string }[]; sampleRows: (string | number | boolean | null)[][] }[]
}
