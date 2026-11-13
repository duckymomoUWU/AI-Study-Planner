import './shadcn.css'

/** shadcn/ui Badge (https://ui.shadcn.com/docs/components/badge). Variants: default, secondary, outline, success. */
export function Badge({ children, variant = 'secondary', className = '', ...props }) {
  return (
    <span className={`shadcn-badge shadcn-badge--${variant} ${className}`.trim()} {...props}>
      {children}
    </span>
  )
}
