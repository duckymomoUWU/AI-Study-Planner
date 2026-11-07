import './shadcn.css'

export function Alert({
  children,
  className = '',
  variant = 'default',
  ...props
}) {
  const isDestructive = variant === 'destructive'
  const isSuccess = variant === 'success'

  return (
    <div
      role="alert"
      className={`shadcn-alert ${isDestructive ? 'shadcn-alert--destructive' : ''} ${isSuccess ? 'shadcn-alert--success' : ''} ${className}`.trim()}
      {...props}
    >
      <div className="shadcn-alert-icon" aria-hidden="true">
        {isDestructive && (
          <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <circle cx="12" cy="12" r="10" />
            <line x1="12" y1="8" x2="12" y2="12" />
            <line x1="12" y1="16" x2="12.01" y2="16" />
          </svg>
        )}
        {isSuccess && (
          <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14" />
            <polyline points="22 4 12 14.01 9 11.01" />
          </svg>
        )}
        {!isDestructive && !isSuccess && (
          <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <circle cx="12" cy="12" r="10" />
            <line x1="12" y1="16" x2="12" y2="12" />
            <line x1="12" y1="8" x2="12.01" y2="8" />
          </svg>
        )}
      </div>
      <div className="shadcn-alert-content">
        {children}
      </div>
    </div>
  )
}

export function AlertTitle({ children, className = '', ...props }) {
  return (
    <h5 className={`shadcn-alert-title ${className}`.trim()} {...props}>
      {children}
    </h5>
  )
}

export function AlertDescription({ children, className = '', ...props }) {
  return (
    <div className={`shadcn-alert-description ${className}`.trim()} {...props}>
      {children}
    </div>
  )
}
