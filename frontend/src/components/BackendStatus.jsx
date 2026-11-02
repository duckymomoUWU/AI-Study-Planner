import { useBackendHealth } from '../hooks/useBackendHealth.js'

const labels = { checking: 'Đang kiểm tra', up: 'Đã kết nối', down: 'Chưa kết nối' }

export default function BackendStatus() {
  const health = useBackendHealth()
  return (
    <section className="connection-card" aria-labelledby="connection-title">
      <div className="section-heading">
        <h2 id="connection-title">Kết nối hệ thống</h2>
        <span className={`status status--${health.state}`}><i aria-hidden="true" />{labels[health.state]}</span>
      </div>
      <p role="status" aria-live="polite">
        {health.state === 'checking' ? 'Đang gửi yêu cầu kiểm tra tới Spring Boot…' : health.message}
      </p>
      <button className="button button--secondary" onClick={health.retry} disabled={health.state === 'checking'}>
        Kiểm tra lại kết nối <span aria-hidden="true">↗</span>
      </button>
    </section>
  )
}
