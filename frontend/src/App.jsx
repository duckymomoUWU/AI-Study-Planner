import { useCallback, useEffect, useRef, useState } from 'react'
import OverviewPage from './pages/OverviewPage.jsx'
import GoalsPage from './pages/GoalsPage.jsx'
import StudyPlanPage from './pages/StudyPlanPage.jsx'
import AuthPage from './pages/AuthPage.jsx'
import LandingPage from './pages/LandingPage.jsx'
import StudyTimerPage from './pages/StudyTimerPage.jsx'
import { getCurrentUser, logout } from './lib/auth.js'
import './App.css'
import './theme.css'

function getInitialUser() {
  if (typeof window === 'undefined') return null
  try {
    const raw = localStorage.getItem('ai_study_planner_user')
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

function parseHash(user = null) {
  if (typeof window === 'undefined') return { page: 'landing', taskId: null }
  const rawHash = window.location.hash || ''
  const [hashPath, queryString] = rawHash.split('?')
  const clean = hashPath.toLowerCase()

  let taskId = null
  if (queryString) {
    const params = new URLSearchParams(queryString)
    taskId = params.get('taskId')
  }

  if (clean.startsWith('#timer/')) {
    taskId = clean.slice(7)
    return { page: 'timer', taskId }
  }
  if (clean === '#timer') return { page: 'timer', taskId }
  if (clean === '#landing') return { page: 'landing', taskId: null }
  if (clean === '#auth' || clean === '#login' || clean === '#register') return { page: 'auth', taskId: null }
  if (clean === '#goals') return { page: 'goals', taskId: null }
  if (clean === '#daily' || clean === '#tasks') return { page: 'daily', taskId: null }
  if (clean === '#overview') return { page: 'overview', taskId: null }

  // Default for empty hash or '/'
  if (clean === '' || clean === '#') {
    return { page: user ? 'overview' : 'landing', taskId: null }
  }

  return { page: user ? 'overview' : 'landing', taskId: null }
}

export default function App() {
  const [currentUser, setCurrentUser] = useState(getInitialUser)
  const initialRoute = parseHash(currentUser)
  const [activePage, setActivePageState] = useState(initialRoute.page) // 'landing' | 'overview' | 'goals' | 'daily' | 'auth' | 'timer'
  const [timerTaskId, setTimerTaskId] = useState(initialRoute.taskId)
  const [dailyDate, setDailyDate] = useState(null)
  const [goalEditRequest, setGoalEditRequest] = useState(null)
  const handleGoalEditRequest = useCallback(() => setGoalEditRequest(null), [])
  const [isCheckingAuth, setIsCheckingAuth] = useState(true)
  const [isSidebarCollapsed, setIsSidebarCollapsed] = useState(false)
  const [showUserMenu, setShowUserMenu] = useState(false)
  const userMenuRef = useRef(null)

  function setActivePage(page, taskId = null) {
    setActivePageState(page)
    setTimerTaskId(taskId)
    setShowUserMenu(false)
    if (page === 'landing') {
      window.location.hash = '#landing'
    } else if (page === 'overview') {
      window.location.hash = ''
    } else if (page === 'timer') {
      window.location.hash = taskId ? `#timer?taskId=${encodeURIComponent(taskId)}` : '#timer'
    } else {
      window.location.hash = `#${page}`
    }
  }

  // Hash change listener
  useEffect(() => {
    function onHashChange() {
      const parsed = parseHash(currentUser)
      setActivePageState(parsed.page)
      setTimerTaskId(parsed.taskId)
    }
    window.addEventListener('hashchange', onHashChange)
    return () => window.removeEventListener('hashchange', onHashChange)
  }, [currentUser])

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

  // Check auth session
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
            // If on empty hash and logged out, switch to landing
            const rawHash = window.location.hash || ''
            if (rawHash === '' || rawHash === '#') {
              setActivePageState('landing')
            }
          }
        }
      } catch {
        if (!ignore) {
          setCurrentUser(null)
          localStorage.removeItem('ai_study_planner_user')
          const rawHash = window.location.hash || ''
          if (rawHash === '' || rawHash === '#') {
            setActivePageState('landing')
          }
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
    setTimerTaskId(null)
    setShowUserMenu(false)
    setActivePage('landing')
  }

  function handleAuthSuccess(user) {
    if (user) {
      localStorage.setItem('ai_study_planner_user', JSON.stringify(user))
    }
    setCurrentUser(user)
    // LND-01-B: after auth success open goals without losing session
    setActivePage('goals')
  }

  // Navigation handlers complying with contract
  const openLogin = () => {
    window.location.hash = '#login'
    setActivePageState('auth')
  }

  const openGoalsOrRegister = () => {
    if (currentUser) {
      setActivePage('goals')
    } else {
      window.location.hash = '#register'
      setActivePageState('auth')
    }
  }

  const openAuth = () => {
    window.location.hash = '#login'
    setActivePageState('auth')
  }

  const openGoals = () => {
    setActivePage('goals')
  }

  // LND-01-A: Guest or #landing route renders LandingPage without workspace sidebar
  if (activePage === 'landing') {
    return (
      <>
        <a className="skip-link" href="#main">Đến nội dung chính</a>
        <LandingPage
          currentUser={currentUser}
          onLogin={openLogin}
          onStart={openGoalsOrRegister}
        />
      </>
    )
  }

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
              onClick={(e) => {
                e.preventDefault()
                setActivePage(currentUser ? 'overview' : 'landing')
              }}
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
              className={`nav-link ${activePage === 'timer' ? 'nav-link--active' : ''}`}
              onClick={() => setActivePage('timer', null)}
              title="Phiên học"
            >
              <span className="nav-icon" aria-hidden="true">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <circle cx="12" cy="12" r="10" />
                  <polyline points="12 6 12 12 16 14" />
                </svg>
              </span>
              <span className="nav-text">Phiên học</span>
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
              {activePage === 'overview' && 'Không gian học tập'}
              {activePage === 'auth' && 'Xác thực tài khoản'}
              {activePage === 'timer' && 'Phiên học'}
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
                          onClick={() => setActivePage('timer', null)}
                        >
                          <span>⏱️</span> Phiên học
                        </button>
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
                onNavigateAuth={openAuth}
              />
            ) : activePage === 'daily' ? (
              <StudyPlanPage
                initialDate={dailyDate}
                onEditGoal={(id, section) => { setGoalEditRequest({ id, section }); setActivePage('goals') }}
                currentUser={currentUser}
                onBack={() => setActivePage('overview')}
                onNavigateGoals={openGoals}
                onNavigateAuth={openAuth}
                onStartTimer={(taskId) => setActivePage('timer', taskId)}
              />
            ) : activePage === 'timer' ? (
              <StudyTimerPage
                key={timerTaskId || 'timer-default'}
                currentUser={currentUser}
                initialTaskId={timerTaskId}
                onNavigateAuth={openAuth}
                onNavigateGoals={openGoals}
              />
            ) : (
              <OverviewPage
                key={currentUser?.id || currentUser?.email || 'guest'}
                currentUser={currentUser}
                checkingAuth={isCheckingAuth}
                onGoals={openGoals}
                onSchedule={() => { setDailyDate(null); setActivePage('daily') }}
                onLogin={openAuth}
                onTimer={(taskId) => setActivePage('timer', taskId)}
              />
            )}
          </main>
        </div>
      </div>
    </>
  )
}
