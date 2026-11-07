import './shadcn.css'

export function Label({ children, className = '', htmlFor, ...props }) {
  return (
    <label
      htmlFor={htmlFor}
      className={`shadcn-label ${className}`.trim()}
      {...props}
    >
      {children}
    </label>
  )
}
