import './shadcn.css'

export function Card({ children, className = '', ...props }) {
  return (
    <div className={`shadcn-card ${className}`.trim()} {...props}>
      {children}
    </div>
  )
}

export function CardHeader({ children, className = '', ...props }) {
  return (
    <div className={`shadcn-card-header ${className}`.trim()} {...props}>
      {children}
    </div>
  )
}

export function CardTitle({ children, className = '', ...props }) {
  return (
    <h3 className={`shadcn-card-title ${className}`.trim()} {...props}>
      {children}
    </h3>
  )
}

export function CardDescription({ children, className = '', ...props }) {
  return (
    <p className={`shadcn-card-description ${className}`.trim()} {...props}>
      {children}
    </p>
  )
}

export function CardContent({ children, className = '', ...props }) {
  return (
    <div className={`shadcn-card-content ${className}`.trim()} {...props}>
      {children}
    </div>
  )
}

export function CardFooter({ children, className = '', ...props }) {
  return (
    <div className={`shadcn-card-footer ${className}`.trim()} {...props}>
      {children}
    </div>
  )
}
