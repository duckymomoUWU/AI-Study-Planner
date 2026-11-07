import { useEffect, useState } from 'react'
import BackendStatus from './components/BackendStatus.jsx'
import GoalsPage from './pages/GoalsPage.jsx'
import AuthPage from './pages/AuthPage.jsx'
import { getCurrentUser, logout } from './lib/auth.js'
import './App.css'

const repository = 'https://github.com/duckymomoUWU/AI-Study-Planner'
const stages = [
  { number: '01', title: 'Đặt mục tiêu', text: 'Xác định môn học, chủ đề, ngày thi và thời gian bạn có.', sprint: 'Sprint 1' },
  { number: '02', title: 'Lên kế hoạch', text: 'AI đề xuất. Bạn chỉnh sửa và quyết định lịch học phù hợp.', sprint: 'Sprint 2' },
  { number: '03', title: 'Học từng ngày', text: 'Xem việc cần làm và theo dõi những bước đã hoàn thành.', sprint: 'Sprint 3' },
]

export default function App() {
  const [activePage, setActivePage] = useState('overview') // 'overview' | 'goals' | 'auth'
  const [currentUser, setCurrentUser] = useState(null)
  const [isCheckingAuth, setIsCheckingAuth] = useState(true)

  useEffect(() => {
    let ignore = false
    async function checkSession() {
      try {
        const user = await getCurrentUser()
        if (!ignore) {
          setCurrentUser(user)
        }
      } catch {
        if (!ignore) {
          setCurrentUser(null)
        }
      } finally {
        if (!ignore) {
          setIsCheckingAuth(false)
        }
      }
    }
    checkSession()
    return () => {
      ignore = true
    }
  }, [])

  async function handleLogout() {
    try {
      await logout()
    } catch {
      // Ignored
    }
    setCurrentUser(null)
    setActivePage('overview')
  }

  function handleAuthSuccess(user) {
    setCurrentUser(user)
    setActivePage('goals')
  }

  return (
    <>
      <a className="skip-link" href="#main">Đến nội dung chính</a>
      <div className="app-shell">
        <aside className="sidebar" aria-label="Thông tin dự án">
          <a className="brand" href="/" aria-label="AI Study Planner — trang chủ" onClick={(e) => { e.preventDefault(); setActivePage('overview'); }}>
            <span className="brand-mark" aria-hidden="true">s<span>✦</span></span>
            <span>study<span className="brand-light">planner</span><small>HỌC CÓ KẾ HOẠCH</small></span>
          </a>
          <nav aria-label="Điều hướng">
            <button
              type="button"
              className={`nav-link ${activePage === 'overview' ? 'nav-link--active' : ''}`}
              onClick={() => setActivePage('overview')}
            >
              <span aria-hidden="true">◫</span> Tổng quan
            </button>
            <button
              type="button"
              className={`nav-link ${activePage === 'goals' ? 'nav-link--active' : ''}`}
              onClick={() => setActivePage('goals')}
            >
              <span aria-hidden="true">◎</span> Mục tiêu
            </button>
            {!currentUser && (
              <button
                type="button"
                className={`nav-link ${activePage === 'auth' ? 'nav-link--active' : ''}`}
                onClick={() => setActivePage('auth')}
              >
                <span aria-hidden="true">🔒</span> Đăng nhập / Đăng ký
              </button>
            )}
            <a className="nav-link" href={`${repository}/wiki`}><span aria-hidden="true">▤</span> Tài liệu dự án <span className="nav-arrow" aria-hidden="true">↗</span></a>
            <a className="nav-link" href={`${repository}/issues`}><span aria-hidden="true">☷</span> Backlog <span className="nav-arrow" aria-hidden="true">↗</span></a>
          </nav>
          <div className="sidebar-note">
            <span aria-hidden="true">✦</span>
            <p>Một kế hoạch rõ ràng.<br />Một khởi đầu nhẹ nhàng.</p>
            <small>AI hỗ trợ, bạn quyết định.</small>
          </div>
        </aside>

        <div className="workspace">
          <header className="topbar">
            <span>
              {activePage === 'goals' && 'Quản lý mục tiêu'}
              {activePage === 'overview' && 'Không gian học tập'}
              {activePage === 'auth' && 'Xác thực tài khoản'}
            </span>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
              {!isCheckingAuth && (
                currentUser ? (
                  <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', fontSize: '0.875rem' }}>
                    <span style={{ color: '#475569', fontWeight: 500 }}>
                      👤 {currentUser.email}
                    </span>
                    <button
                      type="button"
                      className="button button--secondary button--sm"
                      onClick={handleLogout}
                      style={{ padding: '0.25rem 0.625rem', fontSize: '0.75rem' }}
                    >
                      Đăng xuất
                    </button>
                  </div>
                ) : (
                  <button
                    type="button"
                    className="button button--secondary button--sm"
                    onClick={() => setActivePage('auth')}
                    style={{ padding: '0.25rem 0.625rem', fontSize: '0.75rem' }}
                  >
                    Đăng nhập
                  </button>
                )
              )}
              <span className="version">Bản khởi tạo · MVP</span>
            </div>
          </header>

          <main id="main" tabIndex={-1}>
            {activePage === 'auth' ? (
              <AuthPage
                onAuthSuccess={handleAuthSuccess}
                onBack={() => setActivePage('overview')}
              />
            ) : activePage === 'goals' ? (
              <GoalsPage onBack={() => setActivePage('overview')} />
            ) : (
              <>
                <div className="page-heading">
                  <p className="eyebrow">AI STUDY PLANNER</p>
                  <h1>Mỗi buổi học,<br />một bước tiến.</h1>
                  <p>Biến nội dung cần ôn thành những việc cụ thể mỗi ngày.<br className="desktop-break" /> Lên kế hoạch vừa sức, chủ động với kỳ thi của bạn.</p>
                </div>

                <section className="hero-card" aria-labelledby="hero-title">
                  <div>
                    <span className="hero-label">TỪ MỤC TIÊU ĐẾN HÀNH ĐỘNG</span>
                    <h2 id="hero-title">Bắt đầu từ một<br />kế hoạch rõ ràng.</h2>
                    <p>Nhập nội dung, thời hạn và quỹ thời gian.<br />Cùng xây dựng một hành trình ôn tập phù hợp.</p>
                    <div style={{ display: 'flex', gap: '0.75rem', flexWrap: 'wrap' }}>
                      <button className="button button--primary" type="button" onClick={() => setActivePage('goals')}>
                        Quản lý mục tiêu <span aria-hidden="true">→</span>
                      </button>
                      {!currentUser && (
                        <button className="button button--secondary" type="button" onClick={() => setActivePage('auth')}>
                          Đăng nhập ngay
                        </button>
                      )}
                    </div>
                  </div>
                  <div className="plan-illustration" aria-hidden="true">
                    <div className="orb orb--one" />
                    <div className="orb orb--two" />
                    <div className="paper">
                      <div className="paper-top">
                        <span>KẾ HOẠCH ÔN TẬP</span>
                        <b>✦</b>
                      </div>
                      <div className="paper-line wide" />
                      <div className="paper-task"><span>✓</span><i /></div>
                      <div className="paper-task"><span>✓</span><i /></div>
                      <div className="paper-task muted"><span>○</span><i /></div>
                      <div className="paper-progress"><i /></div>
                      <small>Từng bước một, mỗi ngày.</small>
                    </div>
                  </div>
                </section>

                <div className="section-heading roadmap-heading">
                  <h2>Hành trình của sản phẩm</h2>
                  <span>3 bước để học chủ động</span>
                </div>
                <div className="stages">
                  {stages.map(stage => (
                    <article className="stage" key={stage.number}>
                      <div className="stage-top">
                        <span className="stage-number">{stage.number}</span>
                        <span className="sprint">{stage.sprint}</span>
                      </div>
                      <h3>{stage.title}</h3>
                      <p>{stage.text}</p>
                      <span className="planned">Dự kiến phát triển</span>
                    </article>
                  ))}
                </div>

                <div className="bottom-grid">
                  <BackendStatus />
                  <section className="scope-card" aria-labelledby="scope-title">
                    <span className="eyebrow">BƯỚC KHỞI ĐẦU</span>
                    <h2 id="scope-title">Nền tảng đã sẵn sàng để phát triển.</h2>
                    <p>Đăng nhập/đăng ký đã được kết nối với backend Spring Security session cookie. Mục tiêu và đề xuất AI sẽ được lưu trữ an toàn theo từng tài khoản.</p>
                    <a className="text-link" href={`${repository}/wiki/Project-Overview`}>Xem phạm vi và kiến trúc <span aria-hidden="true">→</span></a>
                  </section>
                </div>

                <footer>Đều đặn mỗi ngày, tiến gần mục tiêu. <span>AI hỗ trợ lập kế hoạch · Bạn làm chủ việc học</span></footer>
              </>
            )}
          </main>
        </div>
      </div>
    </>
  )
}
