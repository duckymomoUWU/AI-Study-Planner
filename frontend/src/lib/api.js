// Relative paths use Vite's development proxy, or the deployment reverse proxy.
export async function getBackendHealth({ signal } = {}) {
  const response = await fetch('/actuator/health', {
    headers: { Accept: 'application/json' },
    signal,
    credentials: 'same-origin',
  })
  if (!response.ok) {
    throw new Error(`Backend trả về HTTP ${response.status}.`)
  }
  const data = await response.json()
  if (typeof data?.status !== 'string') {
    throw new Error('Phản hồi không đúng định dạng health của Spring Boot.')
  }
  if (data.status !== 'UP') {
    throw new Error('Backend chưa báo trạng thái UP.')
  }
  return data
}
