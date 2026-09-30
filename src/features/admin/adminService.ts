import type { AdminDatabase, AdminOverview } from './types'
import { apiUrl } from '../../app/api'

async function request<T>(path: string): Promise<T> {
  const response = await fetch(apiUrl(path))
  const payload = await response.json().catch(() => null) as T | { error?: string } | null
  if (!response.ok) {
    const message = payload && typeof payload === 'object' && 'error' in payload && payload.error
      ? payload.error : `Request failed with status ${response.status}.`
    throw new Error(message)
  }
  return payload as T
}

export const adminService = {
  overview: () => request<AdminOverview>('/api/admin/overview'),
  databases: () => request<AdminDatabase[]>('/api/admin/databases'),
}
