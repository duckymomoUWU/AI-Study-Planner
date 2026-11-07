import { useEffect, useState } from 'react'
import BackendStatus from './components/BackendStatus.jsx'
import GoalsPage from './pages/GoalsPage.jsx'
import DailyTasksPage from './pages/DailyTasksPage.jsx'
import AuthPage from './pages/AuthPage.jsx'
import { getCurrentUser, logout } from './lib/auth.js'
import './App.css'

const repository = 'https://github.com/duckymomoUWU/AI-Study-Planner'
const stages = [
  { number: '01', title: 'Đặt mục tiêu', text: 'Xác định môn học, chủ đề, ngày thi và thời gian bạn có.', sprint: 'Sprint 1' },
  { number: '02', title: 'Lên kế hoạch', text: 'AI đề xuất. Bạn chỉnh sửa và quyết định lịch học phù hợp.', sprint: 'Sprint 2' },
  { number: '03', title: 'Học từng ngày', text: 'Xem việc cần làm và theo dõi những bước đã hoàn thành.', sprint: 'Sprint 3' },
]

function getPageFromHash() {
  if (typeof window === 'undefined') return 'overview'
  const hash = window.location.hash.toLowerCase()
  if (hash === '#auth' || hash === '#login' || hash === '#register') return 'auth'
  if (hash === '#goals') return 'goals'
  if (hash === '#daily' || hash === '#tasks') return 'daily'
  return 'overview'
}

export default function App() {
  const [activePage, setActivePageState] = useState(getPageFromHash) // 'overview' | 'goals' | 'daily' | 'auth'
  const [currentUser, setCurrentUser] = useState(null)
  const [isCheckingAuth, setIsCheckingAuth] = useState(true)

  function setActivePage(page) {
    setActivePageState(page)
    if (page === 'overview') {
      window.location.hash = ''
    } else {
      window.location.hash = `#${page}`
    }
  }

  useEffect(() => {
    function onHashChange() {
      setActivePageState(getPageFromHash())
    }
    window.addEventListener('hashchange', onHashChange)
    return () => window.removeEventListener('hashchange', onHashChange)
  }, [])

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
              <span aria-hidden="true">◎</span> Mục tiêu (US-01)
            </button>
            <button
              type="button"
              className={`nav-link ${activePage === 'daily' ? 'nav-link--active' : ''}`}
              onClick={() => setActivePage('daily')}
            >
              <span aria-hidden="true">📅</span> Lịch học hôm nay (US-03)
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
              {activePage === 'goals' && 'Quản lý mục tiêu (US-01)'}
              {activePage === 'daily' && 'Lịch học theo ngày (US-03)'}
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
              <span className="version">Sprint 2 · UI Demo</span>
            </div>
          </header>

          <main id="main" tabIndex={-1}>
            {activePage === 'auth' ? (
              <AuthPage
                initialTab={window.location.hash === '#register' ? 'register' : 'login'}
                onAuthSuccess={handleAuthSuccess}
                onBack={() => setActivePage('overview')}
              />
            ) : activePage === 'goals' ? (
              <GoalsPage
                onBack={() => setActivePage('overview')}
                onNavigateDaily={() => setActivePage('daily')}
              />
            ) : activePage === 'daily' ? (
              <DailyTasksPage
                onBack={() => setActivePage('overview')}
                onNavigateGoals={() => setActivePage('goals')}
              />
            ) : (
              <>
                <div className="page-heading">
                  <p className="eyebrow">AI STUDY PLANNER</p>
                  <h1>Mỗi buổi học,<br />một bước tiến.</h1>
                  <p>Biến nội dung cần ôn thành những việc cụ thể mỗi ngày.<br className="desktop-break" /> Lên kế hoạch vừa sức, chủ động với kỳ thi của bạn.</p>
                </div>

                <section className="hero-card" aria-labelledby="hero-title">
                  <div>
                    <span className="hero-label">SPRINT 2 GIAO DIỆN MỚI</span>
                    <h2 id="hero-title">Bắt đầu từ mục tiêu<br />và việc làm mỗi ngày.</h2>
                    <p>Thiết lập môn học cần ôn thi và theo dõi sát sao từng việc cần hoàn thành trong hôm nay.</p>
                    <div style={{ display: 'flex', gap: '0.75rem', flexWrap: 'wrap' }}>
                      <button className="button button--primary" type="button" onClick={() => setActivePage('goals')}>
                        Quản lý mục tiêu (US-01) <span aria-hidden="true">→</span>
                      </button>
                      <button className="button button--secondary" type="button" onClick={() => setActivePage('daily')}>
                        Lịch học hôm nay (US-03) <span aria-hidden="true">→</span>
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
                      <span className="planned">Sprint 2 Đang triển khai</span>
                    </article>
                  ))}
                </div>

                <div className="bottom-grid">
                  <BackendStatus />
                  <section className="scope-card" aria-labelledby="scope-title">
                    <span className="eyebrow">TIẾN ĐỘ SPRINT 2</span>
                    <h2 id="scope-title">Giao diện US-01 & US-03 đã sẵn sàng.</h2>
                    <p>Giao diện Quản lý mục tiêu (US-01) và Lịch học theo ngày (US-03) đã được hiện thực hoàn chỉnh trên branch <code>dev4-sprint2-UI</code> với các endpoint placeholder.</p>
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
