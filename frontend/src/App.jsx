import { useCallback, useEffect, useRef, useState } from 'react'
import OverviewPage from './pages/OverviewPage.jsx'
import GoalsPage from './pages/GoalsPage.jsx'
import StudyPlanPage from './pages/StudyPlanPage.jsx'
import AuthPage from './pages/AuthPage.jsx'
import WeeklyProgressPage from './pages/WeeklyProgressPage.jsx'
import LandingPage from './pages/LandingPage.jsx'
import TimerPage from './pages/TimerPage.jsx'
import { pageFromHash, taskFromHash } from './lib/navigation.js'
import ActiveRecallPage from './pages/ActiveRecallPage.jsx'
import { getCurrentUser, logout } from './lib/auth.js'
import './App.css'
import './theme.css'

const repository = 'https://github.com/duckymomoUWU/AI-Study-Planner'
function getPageFromHash() {
  return pageFromHash(typeof window === 'undefined' ? '' : window.location.hash)
}

export default function App() {
  const [activePage, setActivePageState] = useState(getPageFromHash) // 'overview' | 'goals' | 'daily' | 'auth'
  const [currentUser, setCurrentUser] = useState(null)
  const [timerTaskId, setTimerTaskId] = useState(() => taskFromHash(window.location.hash))
  const [dailyDate, setDailyDate] = useState(null)
  const [goalEditRequest, setGoalEditRequest] = useState(null)
  const handleGoalEditRequest = useCallback(() => setGoalEditRequest(null), [])
  const [isCheckingAuth, setIsCheckingAuth] = useState(true)
  const [isSidebarCollapsed, setIsSidebarCollapsed] = useState(false)
  const [showUserMenu, setShowUserMenu] = useState(false)
  const userMenuRef = useRef(null)

  function setActivePage(page, taskId) {
    setActivePageState(['login', 'register'].includes(page) ? 'auth' : page)
    if (page === 'timer') setTimerTaskId(taskId || null)
    setShowUserMenu(false)
    if (page === 'home') {
      window.location.hash = ''
    } else {
      window.location.hash = `#${page}${page === 'timer' && taskId ? `?task=${encodeURIComponent(taskId)}` : ''}`
    }
  }

  useEffect(() => {
    function onHashChange() {
      setActivePageState(getPageFromHash())
      setTimerTaskId(taskFromHash(window.location.hash))
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
    setGoalEditRequest(null)
    setShowUserMenu(false)
    setActivePage('home')
  }

  function handleAuthSuccess(user) {
    if (user) {
      localStorage.setItem('ai_study_planner_user', JSON.stringify(user))
    }
    setCurrentUser(user)
    setActivePage('goals')
  }

  if (activePage === 'home' && isCheckingAuth) return <main className="landing-page"><p role="status">Đang mở không gian học tập…</p></main>
  if (activePage === 'landing' || (activePage === 'home' && !currentUser)) return <LandingPage
    currentUser={currentUser} checkingAuth={isCheckingAuth}
    onStart={() => setActivePage(currentUser ? 'goals' : 'register')}
    onLogin={() => setActivePage('login')} onWorkspace={() => setActivePage('overview')}
  />

  return (
    <>
      <a className="skip-link" href="#main">Đến nội dung chính</a>
      <div className="app-shell">
        {/* Sidebar with Collapse capability */}
        <aside
          className={`sidebar ${isSidebarCollapsed ? 'sidebar--collapsed' : ''}`}
          aria-label="Điều hướng ứng dụng"
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
              className={`nav-link ${(activePage === 'overview' || activePage === 'home') ? 'nav-link--active' : ''}`}
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
              title="Quản lý mục tiêu"
            >
              <span className="nav-icon" aria-hidden="true">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <circle cx="12" cy="12" r="10" />
                  <circle cx="12" cy="12" r="6" />
                  <circle cx="12" cy="12" r="2" />
                </svg>
              </span>
              <span className="nav-text">Mục tiêu</span>
            </button>
            <button
              type="button"
              className={`nav-link ${activePage === 'daily' ? 'nav-link--active' : ''}`}
              onClick={() => setActivePage('daily')}
              title="Lịch học theo tuần"
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
            </button>
            <button
              type="button"
              className={`nav-link ${activePage === 'weekly' ? 'nav-link--active' : ''}`}
              onClick={() => setActivePage('weekly')}
              title="Tiến độ theo tuần"
            >
              <span className="nav-icon" aria-hidden="true">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <line x1="18" y1="20" x2="18" y2="10" />
                  <line x1="12" y1="20" x2="12" y2="4" />
                  <line x1="6" y1="20" x2="6" y2="14" />
                </svg>
              </span>
              <span className="nav-text">Tiến độ</span>
            </button>
            <button
              type="button"
              className={`nav-link ${activePage === 'recall' ? 'nav-link--active' : ''}`}
              onClick={() => setActivePage('recall')}
              title="Ôn tập chủ động bằng thẻ hỏi–đáp"
            >
              <span className="nav-icon" aria-hidden="true">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <rect x="3" y="5" width="18" height="14" rx="2" ry="2" />
                  <line x1="7" y1="10" x2="17" y2="10" />
                  <line x1="7" y1="14" x2="13" y2="14" />
                </svg>
              </span>
              <span className="nav-text">Ôn tập</span>
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
            <button type="button" className={`nav-link ${activePage === 'timer' ? 'nav-link--active' : ''}`} onClick={() => setActivePage('timer')} title="Phiên tập trung">
              <span className="nav-icon" aria-hidden="true">◷</span><span className="nav-text">Tập trung</span>
            </button>
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
              {activePage === 'goals' && 'Quản lý mục tiêu'}
              {activePage === 'daily' && 'Lịch học theo tuần'}
              {activePage === 'weekly' && 'Tiến độ theo tuần'}
              {(activePage === 'overview' || activePage === 'home') && 'Không gian học tập'}
              {activePage === 'timer' && 'Phiên tập trung'}
              {activePage === 'recall' && 'Ôn tập chủ động'}
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
                          <span>📅</span> Lịch học của tôi
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
            </div>
          </header>

          <main id="main" tabIndex={-1}>
            {activePage === 'auth' ? (
              <AuthPage
                key={window.location.hash === '#register' ? 'register' : 'login'}
                initialTab={window.location.hash === '#register' ? 'register' : 'login'}
                onAuthSuccess={handleAuthSuccess}
                onBack={() => setActivePage(currentUser ? 'overview' : 'landing')}
              />
            ) : activePage === 'goals' ? (
              <GoalsPage
                currentUser={currentUser}
                editRequest={goalEditRequest}
                onEditRequestHandled={handleGoalEditRequest}
                onBack={() => setActivePage('overview')}
                onNavigateDaily={date => { setDailyDate(typeof date === 'string' ? date : null); setActivePage('daily') }}
                onNavigateAuth={() => setActivePage('auth')}
              />
            ) : activePage === 'daily' ? (
              <StudyPlanPage
                onNavigateTimer={task => setActivePage('timer', task.id)}
                initialDate={dailyDate}
                onEditGoal={(id, section) => { setGoalEditRequest({ id, section }); setActivePage('goals') }}
                currentUser={currentUser}
                onBack={() => setActivePage('overview')}
                onNavigateGoals={() => setActivePage('goals')}
                onNavigateAuth={() => setActivePage('auth')}
              />
            ) : activePage === 'timer' ? (
              <TimerPage key={`${currentUser?.id || currentUser?.email || 'guest'}:${timerTaskId || ''}`}
                currentUser={currentUser} initialTaskId={timerTaskId}
                onLogin={() => setActivePage('login')} onSchedule={() => setActivePage('daily')} />
            ) : activePage === 'weekly' ? (
              <WeeklyProgressPage
                key={currentUser?.id || currentUser?.email || 'guest'}
                onBack={() => setActivePage('overview')}
                onNavigateDaily={() => setActivePage('daily')}
                onNavigateAuth={() => setActivePage('auth')}
              />
            ) : activePage === 'recall' ? (
              <ActiveRecallPage
                key={currentUser?.id || currentUser?.email || 'guest'}
                currentUser={currentUser}
                onNavigateAuth={() => setActivePage('auth')}
              />
            ) : (
              <OverviewPage
                key={currentUser?.id || currentUser?.email || 'guest'}
                currentUser={currentUser}
                checkingAuth={isCheckingAuth}
                onGoals={() => setActivePage('goals')}
                onSchedule={() => { setDailyDate(null); setActivePage('daily') }}
                onLogin={() => setActivePage('auth')}
              />
            )}
          </main>
        </div>
      </div>
    </>
  )
}
