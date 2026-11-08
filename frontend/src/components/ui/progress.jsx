import './shadcn.css'

/**
 * shadcn/ui Progress (https://ui.shadcn.com/docs/components/progress) without the Radix dependency:
 * a track with an indicator, exposed to assistive technology as role="progressbar".
 */
export function Progress({ value = 0, max = 100, className = '', 'aria-label': ariaLabel, ...props }) {
  const safe = Math.min(Math.max(Number.isFinite(value) ? value : 0, 0), max)
  const percent = max === 0 ? 0 : (safe / max) * 100

  return (
    <div
      role="progressbar"
      aria-valuemin={0}
      aria-valuemax={max}
      aria-valuenow={safe}
      aria-label={ariaLabel}
      className={`shadcn-progress ${className}`.trim()}
      {...props}
    >
      <div className="shadcn-progress-indicator" style={{ width: `${percent}%` }} />
    </div>
  )
}
