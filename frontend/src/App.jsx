import { useEffect, useRef, useState } from 'react'
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
  const [reviewDraft, setReviewDraft] = useState(null)
  const [currentUser, setCurrentUser] = useState(() => {
    if (typeof window !== 'undefined') {
      try {
        const saved = localStorage.getItem('ai_study_planner_user')
        if (saved) return JSON.parse(saved)
      } catch {
        // fallback
      }
    }
    return null
  })
  const [isCheckingAuth, setIsCheckingAuth] = useState(true)
  const [isSidebarCollapsed, setIsSidebarCollapsed] = useState(false)
  const [showUserMenu, setShowUserMenu] = useState(false)
  const userMenuRef = useRef(null)

  function setActivePage(page) {
    setActivePageState(page)
    setShowUserMenu(false)
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

  // Close user menu on outside click
  useEffect(() => {
    function handleClickOutside(event) {
      if (userMenuRef.current && !userMenuRef.current.contains(event.target)) {
        setShowUserMenu(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [])

  useEffect(() => {
    let ignore = false
    async function checkSession() {
      try {
        const user = await getCurrentUser()
        if (!ignore) {
          setCurrentUser(user)
          if (user) {
            localStorage.setItem('ai_study_planner_user', JSON.stringify(user))
          } else {
            localStorage.removeItem('ai_study_planner_user')
          }
        }
      } catch {
        if (!ignore) {
          setCurrentUser(null)
          localStorage.removeItem('ai_study_planner_user')
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
    localStorage.removeItem('ai_study_planner_user')
    setCurrentUser(null)
    setShowUserMenu(false)
    setActivePage('overview')
  }

  function handleAuthSuccess(user) {
    if (user) {
      localStorage.setItem('ai_study_planner_user', JSON.stringify(user))
    }
    setCurrentUser(user)
    setActivePage('goals')
  }

  function handleReviewDraft(draft) {
    setReviewDraft(draft)
    setActivePage('daily')
  }

  function handleFinishReview(result) {
    setReviewDraft(null)
    if (!result?.success) {
      setActivePage('goals')
    }
  }

  return (
    <>
      <a className="skip-link" href="#main">Đến nội dung chính</a>
      <div className="app-shell">
        {/* Sidebar with Collapse capability */}
        <aside
          className={`sidebar ${isSidebarCollapsed ? 'sidebar--collapsed' : ''}`}
          aria-label="Thông tin dự án"
        >
          <div className="sidebar-top">
            <a
              className="brand"
              href="/"
              aria-label="AI Study Planner — trang chủ"
              onClick={(e) => { e.preventDefault(); setActivePage('overview'); }}
            >
              <span className="brand-mark" aria-hidden="true">s<span>✦</span></span>
              {!isSidebarCollapsed && (
                <span>study<span className="brand-light">planner</span><small>HỌC CÓ KẾ HOẠCH</small></span>
              )}
            </a>
            <button
              type="button"
              className="sidebar-collapse-btn"
              onClick={() => setIsSidebarCollapsed(!isSidebarCollapsed)}
              title={isSidebarCollapsed ? 'Mở rộng menu (Sidebar)' : 'Thu gọn menu (Sidebar)'}
              aria-label={isSidebarCollapsed ? 'Mở rộng sidebar' : 'Thu gọn sidebar'}
            >
              {isSidebarCollapsed ? '⇥' : '⇤'}
            </button>
          </div>

          <nav aria-label="Điều hướng">
            <button
              type="button"
              className={`nav-link ${activePage === 'overview' ? 'nav-link--active' : ''}`}
              onClick={() => setActivePage('overview')}
              title="Tổng quan"
            >
              <span className="nav-icon" aria-hidden="true">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <rect x="3" y="3" width="7" height="9" rx="1" />
                  <rect x="14" y="3" width="7" height="5" rx="1" />
                  <rect x="14" y="12" width="7" height="9" rx="1" />
                  <rect x="3" y="16" width="7" height="5" rx="1" />
                </svg>
              </span>
              <span className="nav-text">Tổng quan</span>
            </button>
            <button
              type="button"
              className={`nav-link ${activePage === 'goals' ? 'nav-link--active' : ''}`}
              onClick={() => setActivePage('goals')}
              title="Quản lý mục tiêu (US-01)"
            >
              <span className="nav-icon" aria-hidden="true">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <circle cx="12" cy="12" r="10" />
                  <circle cx="12" cy="12" r="6" />
                  <circle cx="12" cy="12" r="2" />
                </svg>
              </span>
              <span className="nav-text">Mục tiêu</span>
              <span className="nav-badge">US-01</span>
            </button>
            <button
              type="button"
              className={`nav-link ${activePage === 'daily' ? 'nav-link--active' : ''}`}
              onClick={() => setActivePage('daily')}
              title="Lịch học theo ngày (US-03)"
            >
              <span className="nav-icon" aria-hidden="true">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <rect x="3" y="4" width="18" height="18" rx="2" ry="2" />
                  <line x1="16" y1="2" x2="16" y2="6" />
                  <line x1="8" y1="2" x2="8" y2="6" />
                  <line x1="3" y1="10" x2="21" y2="10" />
                </svg>
              </span>
              <span className="nav-text">Lịch học</span>
              <span className="nav-badge">US-03</span>
            </button>
            <a className="nav-link" href={`${repository}/wiki`} title="Tài liệu dự án">
              <span className="nav-icon" aria-hidden="true">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20" />
                  <path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z" />
                </svg>
              </span>
              <span className="nav-text">Tài liệu</span>
              <span className="nav-arrow" aria-hidden="true">↗</span>
            </a>
            <a className="nav-link" href={`${repository}/issues`} title="Backlog Jira / GitHub">
              <span className="nav-icon" aria-hidden="true">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <polyline points="9 11 12 14 22 4" />
                  <path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11" />
                </svg>
              </span>
              <span className="nav-text">Backlog</span>
              <span className="nav-arrow" aria-hidden="true">↗</span>
            </a>
          </nav>

          <div className="sidebar-note">
            <span aria-hidden="true">✦</span>
            <p>Một kế hoạch rõ ràng.<br />Một khởi đầu nhẹ nhàng.</p>
            <small>AI hỗ trợ, bạn quyết định.</small>
          </div>
        </aside>

        <div className="workspace">
          {/* Topbar Header */}
          <header className="topbar">
            <span>
              {activePage === 'goals' && 'Quản lý mục tiêu (US-01)'}
              {activePage === 'daily' && 'Lịch học theo ngày (US-03)'}
              {activePage === 'overview' && 'Không gian học tập'}
              {activePage === 'auth' && 'Xác thực tài khoản'}
            </span>

            {/* Top-Right Actions: Avatar Icon & Auth */}
            <div className="topbar-actions">
              {!isCheckingAuth && (
                currentUser ? (
                  <div className="user-menu-wrapper" ref={userMenuRef}>
                    <button
                      type="button"
                      className="avatar-btn"
                      onClick={() => setShowUserMenu(!showUserMenu)}
                      title={`Tài khoản: ${currentUser.email}`}
                      aria-label="Menu tài khoản người dùng"
                      aria-expanded={showUserMenu}
                    >
                      <span className="avatar-initial">
                        {currentUser.email ? currentUser.email.charAt(0).toUpperCase() : 'U'}
                      </span>
                    </button>

                    {showUserMenu && (
                      <div className="user-menu-dropdown" role="menu">
                        <div className="user-menu-header">
                          <span className="user-menu-email">{currentUser.email}</span>
                          <span className="user-menu-status">✓ Đã xác thực</span>
                        </div>
                        <div className="user-menu-divider" />
                        <button
                          type="button"
                          className="user-menu-item"
                          onClick={() => setActivePage('goals')}
                        >
                          <span>◎</span> Mục tiêu của tôi
                        </button>
                        <button
                          type="button"
                          className="user-menu-item"
                          onClick={() => setActivePage('daily')}
                        >
                          <span>📅</span> Lịch học hôm nay
                        </button>
                        <div className="user-menu-divider" />
                        <button
                          type="button"
                          className="user-menu-item user-menu-item--danger"
                          onClick={handleLogout}
                        >
                          <span>🚪</span> Đăng xuất
                        </button>
                      </div>
                    )}
                  </div>
                ) : (
                  <button
                    type="button"
                    className="avatar-btn avatar-btn--guest"
                    onClick={() => setActivePage('auth')}
                    title="Đăng nhập / Đăng ký tài khoản"
                    aria-label="Đăng nhập hoặc đăng ký tài khoản"
                  >
                    <span className="avatar-btn-icon" aria-hidden="true">
                      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                        <path d="M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2" />
                        <circle cx="12" cy="7" r="4" />
                      </svg>
                    </span>
                    <span>Đăng nhập</span>
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
                currentUser={currentUser}
                onBack={() => setActivePage('overview')}
                onNavigateDaily={() => setActivePage('daily')}
                onNavigateAuth={() => setActivePage('auth')}
                onReviewDraft={handleReviewDraft}
              />
            ) : activePage === 'daily' ? (
              <DailyTasksPage
                currentUser={currentUser}
                onBack={() => setActivePage('overview')}
                onNavigateGoals={() => setActivePage('goals')}
                onNavigateAuth={() => setActivePage('auth')}
                reviewDraft={reviewDraft}
                onFinishReview={handleFinishReview}
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
                    <div style={{ display: 'flex', gap: '0.75rem', flexWrap: 'wrap', alignItems: 'center' }}>
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
