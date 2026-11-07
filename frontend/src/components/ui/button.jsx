import './shadcn.css'

export function Button({
  children,
  className = '',
  variant = 'default',
  size = 'default',
  fullWidth = false,
  isLoading = false,
  disabled = false,
  type = 'button',
  ...props
}) {
  const variantClass = `shadcn-button--${variant}`
  const sizeClass = size !== 'default' ? `shadcn-button--${size}` : ''
  const widthClass = fullWidth ? 'shadcn-button--full' : ''

  return (
    <button
      type={type}
      disabled={disabled || isLoading}
      className={`shadcn-button ${variantClass} ${sizeClass} ${widthClass} ${className}`.trim()}
      {...props}
    >
      {isLoading && (
        <svg
          className="shadcn-spinner"
          xmlns="http://www.w3.org/2000/svg"
          width="16"
          height="16"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="2.5"
          strokeLinecap="round"
          strokeLinejoin="round"
          aria-hidden="true"
        >
          <path d="M21 12a9 9 0 1 1-6.219-8.56" />
        </svg>
      )}
      <span>{children}</span>
    </button>
  )
}
