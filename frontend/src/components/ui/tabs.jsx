import './shadcn.css'

export function Tabs({ children, className = '', ...props }) {
  return (
    <div className={`shadcn-tabs ${className}`.trim()} {...props}>
      {children}
    </div>
  )
}

export function TabsList({ children, className = '', ...props }) {
  return (
    <div
      role="tablist"
      className={`shadcn-tabs-list ${className}`.trim()}
      {...props}
    >
      {children}
    </div>
  )
}

export function TabsTrigger({
  children,
  className = '',
  active = false,
  onClick,
  id,
  controls,
  ...props
}) {
  return (
    <button
      type="button"
      role="tab"
      id={id}
      aria-selected={active}
      aria-controls={controls}
      className={`shadcn-tabs-trigger ${active ? 'shadcn-tabs-trigger--active' : ''} ${className}`.trim()}
      onClick={onClick}
      {...props}
    >
      {children}
    </button>
  )
}

export function TabsContent({
  children,
  className = '',
  active = false,
  id,
  labelledBy,
  ...props
}) {
  if (!active) return null

  return (
    <div
      role="tabpanel"
      id={id}
      aria-labelledby={labelledBy}
      className={`shadcn-tabs-content ${className}`.trim()}
      {...props}
    >
      {children}
    </div>
  )
}
