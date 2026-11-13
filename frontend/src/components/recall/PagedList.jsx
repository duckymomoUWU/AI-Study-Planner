import { useEffect, useRef, useState } from 'react'
import { Button } from '../ui/button.jsx'

export const PAGE_SIZE = 10

/**
 * A list that shows PAGE_SIZE items first and reveals more on request, so a hundred sources or decks
 * do not become a page tens of thousands of pixels tall. The count is announced, and keyboard focus
 * moves to the first newly shown item instead of being lost when the button goes away.
 */
export default function PagedList({ items, renderItem, className, noun, pageSize = PAGE_SIZE }) {
  const [shown, setShown] = useState(pageSize)
  const listRef = useRef(null)
  const focusFrom = useRef(null)

  useEffect(() => {
    if (focusFrom.current == null) return
    const target = listRef.current?.children[focusFrom.current]
    focusFrom.current = null
    if (target) {
      target.setAttribute('tabindex', '-1')
      target.focus()
    }
  }, [shown])

  const visible = items.slice(0, shown)
  const remaining = items.length - visible.length
  return (
    <>
      <ul ref={listRef} className={className}>{visible.map(renderItem)}</ul>
      {items.length > pageSize && (
        <div className="recall-pager">
          <p className="recall-help" role="status">Đang hiển thị {visible.length} / {items.length} {noun}</p>
          {remaining > 0 && (
            <Button variant="outline" size="sm" onClick={() => { focusFrom.current = visible.length; setShown(value => value + pageSize) }}>
              Xem thêm {Math.min(pageSize, remaining)} {noun}
            </Button>
          )}
        </div>
      )}
    </>
  )
}
