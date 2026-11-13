import { useEffect, useRef, useState } from 'react'
import { Alert, AlertDescription } from '../ui/alert.jsx'
import { Badge } from '../ui/badge.jsx'
import { Button } from '../ui/button.jsx'
import { Label } from '../ui/label.jsx'
import DeleteDeck from './DeleteDeck.jsx'
import { ANSWER_LIMIT, QUESTION_LIMIT, describeRecallError, sourceLabel, validateCardText } from '../../lib/recall.js'

function SourceQuote({ source, kind }) {
  if (!source?.quote) return null
  return (
    <blockquote className="recall-quote">
      “{source.quote}”
      <footer>{sourceLabel(source, kind)}</footer>
    </blockquote>
  )
}

function CardEditor({ card, api, disabled, sourceKind, focusEditOnMount, onSaved, onError, onUnauthenticated }) {
  const [editing, setEditing] = useState(false)
  const [question, setQuestion] = useState(card.question)
  const [answer, setAnswer] = useState(card.answer)
  const [errors, setErrors] = useState({})
  const [saving, setSaving] = useState(false)
  const editRef = useRef(null)
  const questionRef = useRef(null)
  const wasEditing = useRef(false)

  // Keep keyboard focus where the student is working: into the form when it opens, back on the
  // button when it is cancelled, and on the new button after a save re-creates this card.
  useEffect(() => {
    if (editing) questionRef.current?.focus()
    else if (wasEditing.current) editRef.current?.focus()
    wasEditing.current = editing
  }, [editing])
  useEffect(() => { if (focusEditOnMount) editRef.current?.focus() }, [focusEditOnMount])

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
      await onSaved(card.id)
    } catch (err) {
      const info = describeRecallError(err)
      if (info.kind === 'unauthenticated') onUnauthenticated()
      onError(info.message)
      if (info.kind === 'reload') await onSaved(card.id)
    } finally {
      setSaving(false)
    }
  }

  if (!editing) {
    return (
      <li className={`recall-draft-card${card.qualityWarning ? ' recall-draft-card--warning' : ''}`}>
        <div className="recall-draft-head">
          <h3>{card.question}</h3>
          <div className="recall-draft-badges">
            {card.qualityWarning && (
              <Badge variant="outline" className="recall-badge--warning">⚠️ Cần xem lại</Badge>
            )}
            {card.editedByUser && !card.qualityWarning && (
              <Badge variant="outline" className="recall-badge--success">Bạn đã sửa (Đạt)</Badge>
            )}
            {card.editedByUser && card.qualityWarning && (
              <Badge variant="outline">Bạn đã sửa</Badge>
            )}
          </div>
        </div>
        <p className="recall-draft-answer">{card.answer}</p>
        <SourceQuote source={card.source} kind={sourceKind} />
        {card.qualityWarning && (
          <div className="recall-quality-warning" role="alert">
            <span className="recall-warning-icon">⚠️</span>
            <div className="recall-warning-text">
              <strong>Đánh giá chất lượng:</strong> {card.qualityWarning}
              <p className="recall-warning-hint">Hãy đọc kỹ và bấm "Sửa thẻ" để hoàn thiện câu hỏi hoặc đáp án bám sát tài liệu nguồn trước khi xác nhận.</p>
            </div>
          </div>
        )}
        {card.editedByUser && !card.qualityWarning && (
          <p className="recall-help" style={{ color: 'var(--success-color, #15803d)' }}>✓ Thẻ đã được kiểm tra chất lượng và đạt yêu cầu.</p>
        )}
        <div className="recall-actions recall-card-actions">
          <Button variant="outline" size="sm" ref={editRef} disabled={disabled} onClick={() => {
            setQuestion(card.question); setAnswer(card.answer); setErrors({}); setEditing(true)
          }}>Sửa thẻ</Button>
        </div>
      </li>
    )
  }

  return (
    <li className={`recall-draft-card${card.qualityWarning ? ' recall-draft-card--warning' : ''}`}>
      <form className="recall-form" onSubmit={save} noValidate aria-busy={saving}>
        {card.qualityWarning && (
          <div className="recall-quality-warning recall-quality-warning--compact" role="status">
            <span>⚠️</span> <span>Cảnh báo chất lượng hiện tại: {card.qualityWarning}</span>
          </div>
        )}
        <div className="recall-field">
          <Label htmlFor={`q-${card.id}`}>Câu hỏi</Label>
          <textarea id={`q-${card.id}`} ref={questionRef} className={`shadcn-input recall-textarea${errors.question ? ' shadcn-input--error' : ''}`}
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
        <SourceQuote source={card.source} kind={sourceKind} />
        <div className="recall-actions">
          <Button type="submit" size="sm" isLoading={saving}>{saving ? 'Đang lưu…' : 'Lưu thẻ'}</Button>
          <Button type="button" size="sm" variant="ghost" disabled={saving} onClick={() => setEditing(false)}>Hủy sửa</Button>
        </div>
      </form>
    </li>
  )
}

/** A draft deck: read every card with its source, fix wording, then confirm it for study or delete it. */
export default function DeckEditor({ api, deck, cards, sourceKind, notice, onReload, onActivated, onDeleted, onUnauthenticated }) {
  const [error, setError] = useState('')
  const [activating, setActivating] = useState(false)
  const [savedId, setSavedId] = useState('')
  const activateRef = useRef(null)
  const activateFailed = useRef(false)

  useEffect(() => {
    if (!activating && activateFailed.current) { activateFailed.current = false; activateRef.current?.focus() }
  }, [activating])

  async function activate() {
    if (activating) return
    if (warnedCardsCount > 0) {
      setError(`Không thể xác nhận bộ thẻ khi còn ${warnedCardsCount} flashcard chưa đạt chất lượng. Vui lòng kiểm tra và sửa các thẻ có cảnh báo trước khi xác nhận.`)
      activateFailed.current = true
      return
    }
    setActivating(true); setError('')
    try {
      onActivated(await api.activateDeck(deck.id, deck.version))
    } catch (err) {
      const info = describeRecallError(err)
      if (info.kind === 'unauthenticated') onUnauthenticated()
      setError(info.message)
      activateFailed.current = true
      if (info.kind === 'reload') await onReload()
    } finally {
      setActivating(false)
    }
  }

  const warnedCardsCount = cards.filter(c => Boolean(c.qualityWarning)).length

  return (
    <div className="recall-draft">
      <Alert role="status"><AlertDescription>
        Đây là bản nháp do AI tạo từ nguồn của bạn. Hãy đọc, sửa nếu cần, rồi xác nhận để bắt đầu ôn. Bộ thẻ chưa được dùng cho đến khi bạn xác nhận.
      </AlertDescription></Alert>
      {warnedCardsCount > 0 && (
        <Alert role="status" className="recall-warning-summary"><AlertDescription>
          ⚠️ Còn <strong>{warnedCardsCount} thẻ</strong> chưa đạt tiêu chuẩn chất lượng (không có căn cứ trong tài liệu nguồn, sai ngữ nghĩa, mơ hồ hoặc trùng lặp). Bạn cần bấm "Sửa thẻ" để hoàn thiện tất cả các thẻ có cảnh báo trước khi xác nhận bộ thẻ.
        </AlertDescription></Alert>
      )}
      {notice && <Alert role="status"><AlertDescription>{notice}</AlertDescription></Alert>}
      {error && <Alert variant="destructive"><AlertDescription>{error}</AlertDescription></Alert>}
      <ol className="recall-draft-list">
        {cards.map(card => (
          <CardEditor key={`${card.id}:${card.version}`} card={card} api={api} disabled={activating} sourceKind={sourceKind}
            focusEditOnMount={card.id === savedId}
            onSaved={async id => { setSavedId(id); await onReload() }} onError={setError} onUnauthenticated={onUnauthenticated} />
        ))}
      </ol>
      <div className="recall-actions">
        <Button
          ref={activateRef}
          isLoading={activating}
          disabled={cards.length === 0 || warnedCardsCount > 0}
          title={warnedCardsCount > 0 ? 'Vui lòng sửa các thẻ có cảnh báo trước khi xác nhận bộ thẻ' : undefined}
          onClick={activate}
        >
          {activating ? 'Đang xác nhận…' : 'Xác nhận dùng bộ thẻ này'}
        </Button>
        <DeleteDeck api={api} deck={deck} disabled={activating} onDeleted={onDeleted} onUnauthenticated={onUnauthenticated} />
      </div>
    </div>
  )
}
