// Production uses the Vercel proxy so sessions stay on the frontend origin.
const baseUrl = import.meta.env.PROD ? '' : (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '')

export function apiUrl(path: string): string {
  return `${baseUrl}${path}`
}

export async function apiFetch(path: string, options: RequestInit = {}): Promise<Response> {
  const response = await fetch(apiUrl(path), { ...options, credentials: 'include', headers: { ...Object.fromEntries(new Headers(options.headers)), 'X-CloudSQL-Request': '1' } })
  if (response.status === 401 && !path.startsWith('/api/auth/')) window.dispatchEvent(new Event('cloudsql-session-expired'))
  return response
}
export async function apiRequest<T>(path: string, options?: RequestInit): Promise<T> {
  const response = await apiFetch(path, options)
  if (response.status === 204) return undefined as T
  const payload = await response.json().catch(() => null)
  if (!response.ok) throw new Error(payload?.error ?? 'Unable to complete the request. Please try again.')
  return payload as T
}
