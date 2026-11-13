import './shadcn.css'

/**
 * shadcn/ui Checkbox (https://ui.shadcn.com/docs/components/checkbox) without the Radix dependency:
 * a button with role="checkbox", `data-state` and a check indicator, so it is keyboard and screen-reader friendly.
 */
export function Checkbox({
  checked = false,
  onCheckedChange,
  disabled = false,
  className = '',
  ...props
}) {
  const state = checked ? 'checked' : 'unchecked'

  return (
    <button
      type="button"
      role="checkbox"
      aria-checked={checked}
      data-state={state}
      disabled={disabled}
      className={`shadcn-checkbox ${className}`.trim()}
      onClick={() => onCheckedChange?.(!checked)}
      {...props}
    >
      {checked && (
        <svg
          className="shadcn-checkbox-indicator"
          xmlns="http://www.w3.org/2000/svg"
          width="14"
          height="14"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="3"
          strokeLinecap="round"
          strokeLinejoin="round"
          aria-hidden="true"
        >
          <path d="M20 6 9 17l-5-5" />
        </svg>
      )}
    </button>
  )
}
