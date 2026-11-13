import { useEffect, useRef, useState } from 'react'
import { Alert, AlertDescription } from '../ui/alert.jsx'
import { Button } from '../ui/button.jsx'
import { Input } from '../ui/input.jsx'
import { Label } from '../ui/label.jsx'
import { COUNT_DEFAULT, COUNT_MAX, COUNT_MIN, TITLE_LIMIT, clampCount, describeRecallError, newKey } from '../../lib/recall.js'

/**
 * Makes a draft deck from a saved source. A failure keeps the source; pressing the button again re-sends the
 * same Idempotency-Key for the same inputs, so a timeout cannot create two decks. Changing the inputs
 * or finishing successfully starts a new attempt with a new key.
 */
export default function GenerateForm({ api, material, onGenerated, onUnauthenticated }) {
  const [title, setTitle] = useState(material.title)
  const [count, setCount] = useState(String(COUNT_DEFAULT))
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const attempt = useRef({ signature: '', key: '' })
  const titleRef = useRef(null)
  const submitRef = useRef(null)
  const failed = useRef(false)

  // The form opens right after a source is saved: put the keyboard focus on its first field.
  useEffect(() => { titleRef.current?.focus() }, [])
  // The button is disabled while generating, which drops focus; bring it back after a failure.
  useEffect(() => {
    if (!busy && failed.current) { failed.current = false; submitRef.current?.focus() }
  }, [busy])

  async function submit(event) {
    event.preventDefault()
    if (busy) return
    const cleanTitle = title.trim()
    if (!cleanTitle || cleanTitle.length > TITLE_LIMIT) {
      setError(`Tên bộ thẻ cần từ 1 đến ${TITLE_LIMIT} ký tự.`)
      return
    }
    const cards = clampCount(count)
    setCount(String(cards))
    const signature = `${material.id}|${cleanTitle}|${cards}`
    if (attempt.current.signature !== signature) attempt.current = { signature, key: newKey() }
    setBusy(true); setError('')
    try {
      const deck = await api.generateDeck({ materialId: material.id, title: cleanTitle, count: cards }, attempt.current.key)
      attempt.current = { signature: '', key: '' }
      onGenerated(deck, cards)
    } catch (err) {
      const info = describeRecallError(err)
      if (info.kind === 'unauthenticated') onUnauthenticated()
      setError(info.message)
      failed.current = true
    } finally {
      setBusy(false)
    }
  }

  const id = `recall-generate-${material.id}`
  return (
    <form className="recall-form recall-form--inline" onSubmit={submit} noValidate aria-busy={busy}>
      <div className="recall-field">
        <Label htmlFor={`${id}-title`}>Tên bộ thẻ</Label>
        <Input id={`${id}-title`} ref={titleRef} value={title} disabled={busy} onChange={event => setTitle(event.target.value)} />
      </div>
      <div className="recall-field recall-field--short">
        <Label htmlFor={`${id}-count`}>Số thẻ</Label>
        <Input id={`${id}-count`} type="number" min={COUNT_MIN} max={COUNT_MAX} value={count} disabled={busy}
          onChange={event => setCount(event.target.value)} aria-describedby={`${id}-count-help`} />
        <p className="recall-help" id={`${id}-count-help`}>Từ {COUNT_MIN} đến {COUNT_MAX}.</p>
      </div>
      {error && <Alert variant="destructive"><AlertDescription>{error}</AlertDescription></Alert>}
      <div className="recall-actions">
        <Button type="submit" ref={submitRef} isLoading={busy}>{busy ? 'AI đang tạo bản nháp…' : error ? 'Thử tạo lại' : 'Tạo bản nháp thẻ'}</Button>
      </div>
    </form>
  )
}
