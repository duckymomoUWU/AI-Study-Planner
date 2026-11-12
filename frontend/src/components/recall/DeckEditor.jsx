import { useState } from 'react'
import { Alert, AlertDescription } from '../ui/alert.jsx'
import { Badge } from '../ui/badge.jsx'
import { Button } from '../ui/button.jsx'
import { Label } from '../ui/label.jsx'
import DeleteDeck from './DeleteDeck.jsx'
import { ANSWER_LIMIT, QUESTION_LIMIT, describeRecallError, validateCardText } from '../../lib/recall.js'

function SourceQuote({ source }) {
  if (!source?.quote) return null
  return (
    <blockquote className="recall-quote">
      “{source.quote}”
      <footer>{source.page ? `Trang ${source.page}` : 'Đoạn dán vào'}</footer>
    </blockquote>
  )
}

function CardEditor({ card, api, disabled, onSaved, onError, onUnauthenticated }) {
  const [editing, setEditing] = useState(false)
  const [question, setQuestion] = useState(card.question)
  const [answer, setAnswer] = useState(card.answer)
  const [errors, setErrors] = useState({})
  const [saving, setSaving] = useState(false)

  async function save(event) {
    event.preventDefault()
    if (saving) return
    const found = validateCardText({ question, answer })
    setErrors(found)
    if (Object.keys(found).length) return
    setSaving(true); onError('')
    try {
      await api.updateCard(card.id, { question: question.trim(), answer: answer.trim() }, card.version)
      setEditing(false)
      await onSaved()
    } catch (err) {
      const info = describeRecallError(err)
      if (info.kind === 'unauthenticated') onUnauthenticated()
      onError(info.message)
      if (info.kind === 'reload') await onSaved()
    } finally {
      setSaving(false)
    }
  }

  if (!editing) {
    return (
      <li className="recall-draft-card">
        <div className="recall-draft-head">
          <h3>{card.question}</h3>
          {card.editedByUser && <Badge variant="outline">Bạn đã sửa</Badge>}
        </div>
        <p className="recall-draft-answer">{card.answer}</p>
        <SourceQuote source={card.source} />
        {card.editedByUser && (
          <p className="recall-help">Nội dung bạn đã sửa chưa được đối chiếu lại với nguồn. Hãy tự kiểm tra trước khi dùng.</p>
        )}
        <Button variant="outline" size="sm" disabled={disabled} onClick={() => {
          setQuestion(card.question); setAnswer(card.answer); setErrors({}); setEditing(true)
        }}>Sửa thẻ</Button>
      </li>
    )
  }

  return (
    <li className="recall-draft-card">
      <form className="recall-form" onSubmit={save} noValidate aria-busy={saving}>
        <div className="recall-field">
          <Label htmlFor={`q-${card.id}`}>Câu hỏi</Label>
          <textarea id={`q-${card.id}`} className={`shadcn-input recall-textarea${errors.question ? ' shadcn-input--error' : ''}`}
            rows={2} value={question} disabled={saving} onChange={event => setQuestion(event.target.value)}
            aria-describedby={errors.question ? `q-${card.id}-error` : undefined} />
          <p className="recall-help">{question.length} / {QUESTION_LIMIT} ký tự.</p>
          {errors.question && <p className="recall-field-error" id={`q-${card.id}-error`}>{errors.question}</p>}
        </div>
        <div className="recall-field">
          <Label htmlFor={`a-${card.id}`}>Đáp án</Label>
          <textarea id={`a-${card.id}`} className={`shadcn-input recall-textarea${errors.answer ? ' shadcn-input--error' : ''}`}
            rows={3} value={answer} disabled={saving} onChange={event => setAnswer(event.target.value)}
            aria-describedby={errors.answer ? `a-${card.id}-error` : undefined} />
          <p className="recall-help">{answer.length} / {ANSWER_LIMIT} ký tự.</p>
          {errors.answer && <p className="recall-field-error" id={`a-${card.id}-error`}>{errors.answer}</p>}
        </div>
        <SourceQuote source={card.source} />
        <div className="recall-actions">
          <Button type="submit" size="sm" isLoading={saving}>{saving ? 'Đang lưu…' : 'Lưu thẻ'}</Button>
          <Button type="button" size="sm" variant="ghost" disabled={saving} onClick={() => setEditing(false)}>Hủy sửa</Button>
        </div>
      </form>
    </li>
  )
}

/** A draft deck: read every card with its source, fix wording, then confirm it for study or delete it. */
export default function DeckEditor({ api, deck, cards, onReload, onActivated, onDeleted, onUnauthenticated }) {
  const [error, setError] = useState('')
  const [activating, setActivating] = useState(false)

  async function activate() {
    if (activating) return
    setActivating(true); setError('')
    try {
      onActivated(await api.activateDeck(deck.id, deck.version))
    } catch (err) {
      const info = describeRecallError(err)
      if (info.kind === 'unauthenticated') onUnauthenticated()
      setError(info.message)
      if (info.kind === 'reload') await onReload()
    } finally {
      setActivating(false)
    }
  }

  return (
    <div className="recall-draft">
      <Alert><AlertDescription>
        Đây là bản nháp do AI tạo từ nguồn của bạn. Hãy đọc, sửa nếu cần, rồi xác nhận để bắt đầu ôn. Bộ thẻ chưa được dùng cho đến khi bạn xác nhận.
      </AlertDescription></Alert>
      {error && <Alert variant="destructive"><AlertDescription>{error}</AlertDescription></Alert>}
      <ol className="recall-draft-list">
        {cards.map(card => (
          <CardEditor key={`${card.id}:${card.version}`} card={card} api={api} disabled={activating}
            onSaved={onReload} onError={setError} onUnauthenticated={onUnauthenticated} />
        ))}
      </ol>
      <div className="recall-actions">
        <Button isLoading={activating} disabled={cards.length === 0} onClick={activate}>
          {activating ? 'Đang xác nhận…' : 'Xác nhận dùng bộ thẻ này'}
        </Button>
        <DeleteDeck api={api} deck={deck} disabled={activating} onDeleted={onDeleted} onUnauthenticated={onUnauthenticated} />
      </div>
    </div>
  )
}
