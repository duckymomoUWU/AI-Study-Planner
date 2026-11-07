import './shadcn.css'

export function Input({
  className = '',
  hasError = false,
  type = 'text',
  ...props
}) {
  return (
    <input
      type={type}
      className={`shadcn-input ${hasError ? 'shadcn-input--error' : ''} ${className}`.trim()}
      {...props}
    />
  )
}
