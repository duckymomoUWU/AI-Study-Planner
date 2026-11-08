import { useState } from 'react'
import { Card, CardHeader, CardTitle, CardDescription, CardContent, CardFooter } from '../components/ui/card.jsx'
import { Button } from '../components/ui/button.jsx'
import { Input } from '../components/ui/input.jsx'
import { Label } from '../components/ui/label.jsx'
import { Alert, AlertTitle, AlertDescription } from '../components/ui/alert.jsx'
import { Tabs, TabsList, TabsTrigger, TabsContent } from '../components/ui/tabs.jsx'
import { login, register, AuthApiError } from '../lib/auth.js'
import { clearPlaceholderGoals } from '../lib/goals.js'
import './AuthPage.css'

export default function AuthPage({ onAuthSuccess, onBack, initialTab = 'login' }) {
  const [tab, setTab] = useState(initialTab) // 'login' | 'register'

  // Form states
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')

  // UI status
  const [isLoading, setIsLoading] = useState(false)
  const [generalError, setGeneralError] = useState(null)
  const [fieldErrors, setFieldErrors] = useState({})
  const [successMessage, setSuccessMessage] = useState(null)

  function resetErrors() {
    setGeneralError(null)
    setFieldErrors({})
    setSuccessMessage(null)
  }

  function handleTabChange(newTab) {
    setTab(newTab)
    resetErrors()
  }

  async function handleLogin(e) {
    e.preventDefault()
    resetErrors()

    const errors = {}
    if (!email.trim()) errors.email = 'Vui lòng nhập email.'
    if (!password) errors.password = 'Vui lòng nhập mật khẩu.'

    if (Object.keys(errors).length > 0) {
      setFieldErrors(errors)
      return
    }

    setIsLoading(true)
    try {
      const user = await login(email.trim(), password)
      if (onAuthSuccess) {
        onAuthSuccess(user)
      }
    } catch (err) {
      if (err instanceof AuthApiError) {
        if (err.code === 'INVALID_CREDENTIALS') {
          setGeneralError('Email hoặc mật khẩu không chính xác.')
        } else if (err.code === 'CSRF_INVALID') {
          setGeneralError('Phiên làm việc đã hết hạn hoặc token CSRF không hợp lệ. Vui lòng thử lại.')
        } else {
          setGeneralError(err.message || 'Đăng nhập không thành công.')
        }
      } else {
        setGeneralError('Không thể kết nối đến máy chủ. Vui lòng kiểm tra kết nối mạng.')
      }
    } finally {
      setIsLoading(false)
    }
  }

  async function handleRegister(e) {
    e.preventDefault()
    resetErrors()

    const errors = {}
    if (!email.trim()) {
      errors.email = 'Vui lòng nhập email.'
    } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim())) {
      errors.email = 'Email không đúng định dạng.'
    }

    if (!password) {
      errors.password = 'Vui lòng nhập mật khẩu.'
    } else if (password.length < 6) {
      errors.password = 'Mật khẩu phải có ít nhất 6 ký tự.'
    }

    if (password !== confirmPassword) {
      errors.confirmPassword = 'Mật khẩu xác nhận không khớp.'
    }

    if (Object.keys(errors).length > 0) {
      setFieldErrors(errors)
      return
    }

    setIsLoading(true)
    try {
      await register(email.trim(), password)
      setSuccessMessage('Đăng ký tài khoản thành công! Đang tự động đăng nhập...')

      // New account creation: Ensure no mockup or leftover placeholder data exists
      clearPlaceholderGoals()
      try {
        localStorage.removeItem('ai_study_planner_calendar_tasks_v2')
        localStorage.removeItem('ai_study_planner_calendar_tasks_user_v2')
      } catch {
        // ignore
      }

      // Auto-login after successful registration
      const user = await login(email.trim(), password)
      if (onAuthSuccess) {
        onAuthSuccess(user)
      }
    } catch (err) {
      if (err instanceof AuthApiError) {
        if (err.code === 'EMAIL_ALREADY_EXISTS') {
          setGeneralError('Email này đã được sử dụng. Vui lòng chọn email khác hoặc đăng nhập.')
        } else if (err.code === 'VALIDATION_ERROR' && err.fieldErrors?.length) {
          const map = {}
          for (const item of err.fieldErrors) {
            map[item.field] = item.message
          }
          setFieldErrors(map)
        } else {
          setGeneralError(err.message || 'Đăng ký không thành công.')
        }
      } else {
        setGeneralError('Không thể kết nối đến máy chủ. Vui lòng kiểm tra kết nối mạng.')
      }
    } finally {
      setIsLoading(false)
    }
  }

  return (
    <div className="auth-container">
      <div className="auth-wrapper">
        {onBack && (
          <Button
            variant="ghost"
            size="sm"
            className="auth-back-btn"
            onClick={onBack}
          >
            ← Quay lại trang chủ
          </Button>
        )}

        <Card>
          <CardHeader>
            <CardTitle>{tab === 'login' ? 'Đăng nhập' : 'Tạo tài khoản'}</CardTitle>
            <CardDescription>
              {tab === 'login'
                ? 'Nhập email và mật khẩu để tiếp tục quản lý kế hoạch học tập.'
                : 'Đăng ký tài khoản sinh viên để lưu trữ mục tiêu ôn tập của bạn.'}
            </CardDescription>
          </CardHeader>

          <CardContent>
            <Tabs>
              <TabsList>
                <TabsTrigger
                  id="tab-login"
                  controls="tabpanel-login"
                  active={tab === 'login'}
                  onClick={() => handleTabChange('login')}
                >
                  Đăng nhập
                </TabsTrigger>
                <TabsTrigger
                  id="tab-register"
                  controls="tabpanel-register"
                  active={tab === 'register'}
                  onClick={() => handleTabChange('register')}
                >
                  Tạo tài khoản
                </TabsTrigger>
              </TabsList>

              {generalError && (
                <Alert variant="destructive">
                  <AlertTitle>Lỗi</AlertTitle>
                  <AlertDescription>{generalError}</AlertDescription>
                </Alert>
              )}

              {successMessage && (
                <Alert variant="success">
                  <AlertTitle>Thành công</AlertTitle>
                  <AlertDescription>{successMessage}</AlertDescription>
                </Alert>
              )}

              <TabsContent active={tab === 'login'} id="tabpanel-login" labelledBy="tab-login">
                <form onSubmit={handleLogin} noValidate>
                  <div className="shadcn-form-item">
                    <Label htmlFor="login-email">Email</Label>
                    <Input
                      id="login-email"
                      type="email"
                      autoComplete="username"
                      placeholder="student@hcmut.edu.vn"
                      value={email}
                      onChange={(e) => setEmail(e.target.value)}
                      hasError={Boolean(fieldErrors.email)}
                    />
                    {fieldErrors.email && (
                      <span className="shadcn-form-error">{fieldErrors.email}</span>
                    )}
                  </div>

                  <div className="shadcn-form-item">
                    <Label htmlFor="login-password">Mật khẩu</Label>
                    <Input
                      id="login-password"
                      type="password"
                      autoComplete="current-password"
                      placeholder="••••••••"
                      value={password}
                      onChange={(e) => setPassword(e.target.value)}
                      hasError={Boolean(fieldErrors.password)}
                    />
                    {fieldErrors.password && (
                      <span className="shadcn-form-error">{fieldErrors.password}</span>
                    )}
                  </div>

                  <Button
                    type="submit"
                    fullWidth
                    isLoading={isLoading}
                    className="mt-2"
                  >
                    {isLoading ? 'Đang đăng nhập...' : 'Đăng nhập'}
                  </Button>
                </form>
              </TabsContent>

              <TabsContent active={tab === 'register'} id="tabpanel-register" labelledBy="tab-register">
                <form onSubmit={handleRegister} noValidate>
                  <div className="shadcn-form-item">
                    <Label htmlFor="register-email">Email sinh viên</Label>
                    <Input
                      id="register-email"
                      type="email"
                      autoComplete="username"
                      placeholder="student@hcmut.edu.vn"
                      value={email}
                      onChange={(e) => setEmail(e.target.value)}
                      hasError={Boolean(fieldErrors.email)}
                    />
                    {fieldErrors.email && (
                      <span className="shadcn-form-error">{fieldErrors.email}</span>
                    )}
                  </div>

                  <div className="shadcn-form-item">
                    <Label htmlFor="register-password">Mật khẩu</Label>
                    <Input
                      id="register-password"
                      type="password"
                      autoComplete="new-password"
                      placeholder="Tối thiểu 6 ký tự"
                      value={password}
                      onChange={(e) => setPassword(e.target.value)}
                      hasError={Boolean(fieldErrors.password)}
                    />
                    <span className="auth-password-hint">Tối thiểu 6 ký tự.</span>
                    {fieldErrors.password && (
                      <span className="shadcn-form-error">{fieldErrors.password}</span>
                    )}
                  </div>

                  <div className="shadcn-form-item">
                    <Label htmlFor="register-confirm">Xác nhận mật khẩu</Label>
                    <Input
                      id="register-confirm"
                      type="password"
                      autoComplete="new-password"
                      placeholder="Nhập lại mật khẩu"
                      value={confirmPassword}
                      onChange={(e) => setConfirmPassword(e.target.value)}
                      hasError={Boolean(fieldErrors.confirmPassword)}
                    />
                    {fieldErrors.confirmPassword && (
                      <span className="shadcn-form-error">{fieldErrors.confirmPassword}</span>
                    )}
                  </div>

                  <Button
                    type="submit"
                    fullWidth
                    isLoading={isLoading}
                    className="mt-2"
                  >
                    {isLoading ? 'Đang tạo tài khoản...' : 'Tạo tài khoản mới'}
                  </Button>
                </form>
              </TabsContent>
            </Tabs>
          </CardContent>

          <CardFooter>
            <div className="auth-footer-text">
              {tab === 'login' ? (
                <>
                  <span>Chưa có tài khoản?</span>
                  <Button
                    variant="link"
                    size="sm"
                    onClick={() => handleTabChange('register')}
                  >
                    Đăng ký ngay
                  </Button>
                </>
              ) : (
                <>
                  <span>Đã có tài khoản?</span>
                  <Button
                    variant="link"
                    size="sm"
                    onClick={() => handleTabChange('login')}
                  >
                    Đăng nhập ngay
                  </Button>
                </>
              )}
            </div>
          </CardFooter>
        </Card>
      </div>
    </div>
  )
}
