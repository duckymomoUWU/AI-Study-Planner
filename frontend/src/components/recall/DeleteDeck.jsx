import { useEffect, useRef, useState } from 'react'
import { Alert, AlertDescription } from '../ui/alert.jsx'
import { Button } from '../ui/button.jsx'
import { describeRecallError } from '../../lib/recall.js'

/** Delete with an explicit confirmation that says what is lost and what is kept. */
export default function DeleteDeck({ api, deck, disabled, onDeleted, onUnauthenticated }) {
  const [confirming, setConfirming] = useState(false)
  const [deleting, setDeleting] = useState(false)
  const [error, setError] = useState('')
  const keepRef = useRef(null)
  const openRef = useRef(null)
  const wasConfirming = useRef(false)

  // Opening the confirmation moves focus to the safe choice; closing it returns focus to the opener.
  useEffect(() => {
    if (confirming) keepRef.current?.focus()
    else if (wasConfirming.current) openRef.current?.focus()
    wasConfirming.current = confirming
  }, [confirming])

  async function remove() {
    if (deleting) return
    setDeleting(true); setError('')
    try {
      await api.deleteDeck(deck.id)
      onDeleted()
    } catch (err) {
      const info = describeRecallError(err)
      if (info.kind === 'unauthenticated') onUnauthenticated()
      setError(info.message)
    } finally {
      setDeleting(false)
    }
  }

  if (!confirming) {
    return <Button variant="outline" ref={openRef} disabled={disabled} onClick={() => setConfirming(true)}>Xóa bộ thẻ</Button>
  }
  return (
    <Alert variant="destructive">
      <AlertDescription>
        <p>Xóa bộ thẻ “{deck.title}” cùng toàn bộ thẻ và kết quả ôn của nó? Thao tác này không thể hoàn tác. Nguồn tài liệu và mục tiêu không bị ảnh hưởng.</p>
        {error && <p className="recall-field-error">{error}</p>}
        <div className="recall-actions">
          <Button variant="outline" size="sm" isLoading={deleting} onClick={remove}>Xóa bộ thẻ</Button>
          <Button variant="ghost" size="sm" ref={keepRef} disabled={deleting} onClick={() => setConfirming(false)}>Giữ lại</Button>
        </div>
      </AlertDescription>
    </Alert>
  )
}
