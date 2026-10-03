// A fixed, server-configured upstream keeps HttpOnly sessions on the frontend origin.
export default async function handler(request, response) {
  response.setHeader('Cache-Control', 'no-store')
  response.setHeader('X-Content-Type-Options', 'nosniff')
  const path = request.query?.path
  if (typeof path !== 'string' || !/^(auth\/(signup|login|logout|me)|health|dashboard|problems(?:\/\d+(?:\/(execute|submit))?)?|databases(?:\/[a-f0-9-]+(?:\/(execute|schema))?)?|admin\/(overview|databases))$/.test(path)) {
    return response.status(404).json({ error: 'API route not found.' })
  }
  if (!['GET', 'POST', 'DELETE', 'OPTIONS', 'HEAD'].includes(request.method)) {
    return response.status(405).json({ error: 'Method not allowed.' })
  }
  let upstream
  try {
    upstream = new URL(process.env.BACKEND_API_URL || process.env.VITE_API_BASE_URL)
    if (upstream.protocol !== 'https:' || upstream.username || upstream.password || upstream.pathname !== '/' || upstream.search || upstream.hash) throw new Error('Invalid upstream')
  } catch {
    return response.status(503).json({ error: 'The API connection is not configured.' })
  }
  const headers = new Headers()
  for (const name of ['content-type', 'cookie', 'origin', 'x-cloudsql-request', 'access-control-request-method', 'access-control-request-headers']) {
    if (typeof request.headers[name] === 'string') headers.set(name, request.headers[name])
  }
  try {
    const result = await fetch(new URL(`/api/${path}`, upstream), {
      method: request.method, headers, redirect: 'manual', signal: AbortSignal.timeout(55000),
      ...(!['GET', 'HEAD'].includes(request.method) && request.body != null ? { body: typeof request.body === 'string' ? request.body : JSON.stringify(request.body) } : {}),
    })
    // Only forward application and cookie headers; never upstream redirects or cache policies.
    for (const name of ['content-type', 'access-control-allow-origin', 'access-control-allow-credentials', 'access-control-allow-methods', 'access-control-allow-headers', 'vary']) {
      const value = result.headers.get(name)
      if (value) response.setHeader(name, value)
    }
    const cookies = result.headers.getSetCookie()
    if (cookies.length) response.setHeader('Set-Cookie', cookies)
    response.status(result.status).send(Buffer.from(await result.arrayBuffer()))
  } catch {
    response.status(502).json({ error: 'Unable to reach the API. Please try again.' })
  }
}
