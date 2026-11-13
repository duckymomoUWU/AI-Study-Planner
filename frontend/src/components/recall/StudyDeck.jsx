import { useEffect, useMemo, useRef, useState } from 'react'
import { Alert, AlertDescription } from '../ui/alert.jsx'
import { Button } from '../ui/button.jsx'
import { Progress } from '../ui/progress.jsx'
import DeleteDeck from './DeleteDeck.jsx'
import { deckProgress, describeRecallError, firstUnreviewedIndex, isStudyComplete, newKey } from '../../lib/recall.js'

/**
 * Study mode for an active deck: the question first, the answer on request, then the student's own
 * "remembered / forgot" rating. The server stores the self-rating; nothing here is graded by AI.
 * A rating that fails to save stays pending with its Idempotency-Key, so retrying cannot record it twice.
 */
export default function StudyDeck({ api, deck, initialCards, onDeleted, onUnauthenticated }) {
  const [cards, setCards] = useState(initialCards)
  const [queue, setQueue] = useState(() => initialCards.map(card => card.id))
  const [position, setPosition] = useState(() => (isStudyComplete(initialCards) ? initialCards.length : firstUnreviewedIndex(initialCards)))
  const [revealed, setRevealed] = useState(false)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [pending, setPending] = useState(null) // { cardId, rating, key } of a rating that is not saved yet
  const [announcement, setAnnouncement] = useState('')

  const questionRef = useRef(null)
  const answerRef = useRef(null)
  const summaryRef = useRef(null)
  const retryRef = useRef(null)
  const mounted = useRef(false)

  const byId = useMemo(() => new Map(cards.map(card => [card.id, card])), [cards])
  const progress = deckProgress(cards)
  const complete = isStudyComplete(cards)
  const current = byId.get(queue[position])
  const finished = !current

  // Keyboard and screen-reader focus follows the study flow: the new question, then the revealed
  // answer, then the summary. The first render leaves focus alone (the page focuses the deck title).
  useEffect(() => {
    if (!mounted.current) return
    if (finished) summaryRef.current?.focus()
    else questionRef.current?.focus()
  }, [position, finished, queue])
  useEffect(() => {
    if (mounted.current && revealed) answerRef.current?.focus()
  }, [revealed])
  useEffect(() => {
    if (mounted.current && error && pending) retryRef.current?.focus()
  }, [error, pending])
  useEffect(() => { mounted.current = true }, [])

  function goTo(next) {
    setPosition(next); setRevealed(false); setError(''); setPending(null)
  }

  async function rate(rating) {
    if (saving || !current) return
    const attempt = pending && pending.cardId === current.id && pending.rating === rating
      ? pending : { cardId: current.id, rating, key: newKey() }
    setPending(attempt); setSaving(true); setError('')
    try {
      const result = await api.reviewCard(current.id, rating, attempt.key)
      setCards(previous => previous.map(card => card.id === current.id
        ? { ...card, lastRating: rating, lastReviewedAt: result.reviewedAt ?? new Date().toISOString(), reviewCount: card.reviewCount + 1 }
        : card))
      setPending(null)
      setAnnouncement(rating === 'REMEMBERED' ? 'Đã lưu: nhớ.' : 'Đã lưu: chưa nhớ.')
      setRevealed(false)
      setPosition(value => value + 1)
    } catch (err) {
      const info = describeRecallError(err)
      if (info.kind === 'unauthenticated') onUnauthenticated()
      setError(info.kind === 'retry'
        ? `Chưa lưu được đánh giá. ${info.message}`
        : info.message)
    } finally {
      setSaving(false)
    }
  }

  function restart(ids) {
    setQueue(ids); setPosition(0); setRevealed(false); setError(''); setPending(null)
  }

  const percent = progress.totalCards ? Math.round((progress.reviewedCards / progress.totalCards) * 100) : 0
  const forgottenIds = cards.filter(card => card.lastRating === 'FORGOT').map(card => card.id)

  return (
    <div className="recall-study">
      <div className="recall-study-top">
        <p className="recall-study-count">
          {finished ? 'Đã ôn xong lượt này' : `Thẻ ${position + 1} / ${queue.length}`}
        </p>
        <Progress value={progress.reviewedCards} max={Math.max(progress.totalCards, 1)} aria-label="Số thẻ đã tự đánh giá" />
        <p className="recall-help">
          Đã đánh giá {progress.reviewedCards} / {progress.totalCards} thẻ ({percent}%) · Nhớ {progress.rememberedCards} · Chưa nhớ {progress.forgottenCards}
        </p>
      </div>
      <p className="recall-visually-hidden" role="status" aria-live="polite">{announcement}</p>

      {finished ? (
        <section className="recall-summary" aria-labelledby="recall-summary-title">
          <h3 id="recall-summary-title" ref={summaryRef} tabIndex={-1}>{complete ? 'Bạn đã tự đánh giá tất cả thẻ' : 'Chưa đánh giá hết các thẻ'}</h3>
          <p>Nhớ {progress.rememberedCards} thẻ, chưa nhớ {progress.forgottenCards} thẻ. Đây là điểm tự đánh giá của bạn, không phải kết quả chấm bài.</p>
          <div className="recall-actions">
            {forgottenIds.length > 0 && <Button onClick={() => restart(forgottenIds)}>Ôn lại {forgottenIds.length} thẻ chưa nhớ</Button>}
            <Button variant={forgottenIds.length ? 'outline' : 'default'} onClick={() => restart(cards.map(card => card.id))}>Ôn lại tất cả</Button>
          </div>
        </section>
      ) : (
        <article className="recall-flashcard" aria-labelledby="recall-question">
          <p className="recall-label">Câu hỏi</p>
          <h3 id="recall-question" ref={questionRef} tabIndex={-1}>{current.question}</h3>
          {current.lastRating && (
            <p className="recall-help">Lần đánh giá gần nhất: {current.lastRating === 'REMEMBERED' ? 'Nhớ' : 'Chưa nhớ'}.</p>
          )}
          {!revealed ? (
            <Button onClick={() => setRevealed(true)}>Xem đáp án</Button>
          ) : (
            <div id="recall-answer" className="recall-answer">
              <p className="recall-label" ref={answerRef} tabIndex={-1}>Đáp án</p>
              <p className="recall-answer-text">{current.answer}</p>
              {current.source?.quote && (
                <blockquote className="recall-quote">“{current.source.quote}”<footer>{current.source.page ? `Trang ${current.source.page}` : 'Đoạn dán vào'}</footer></blockquote>
              )}
              <p className="recall-label" id="recall-rate-label">Bạn có nhớ câu trả lời trước khi xem đáp án không?</p>
              <div className="recall-actions" role="group" aria-labelledby="recall-rate-label">
                <Button variant="outline" disabled={saving} isLoading={saving && pending?.rating === 'FORGOT'} onClick={() => rate('FORGOT')}>Chưa nhớ</Button>
                <Button disabled={saving} isLoading={saving && pending?.rating === 'REMEMBERED'} onClick={() => rate('REMEMBERED')}>Nhớ</Button>
              </div>
            </div>
          )}
          {error && (
            <Alert variant="destructive"><AlertDescription>
              <p>{error}</p>
              {pending && <Button size="sm" variant="outline" ref={retryRef} disabled={saving} onClick={() => rate(pending.rating)}>Thử lưu lại</Button>}
            </AlertDescription></Alert>
          )}
          <div className="recall-actions recall-actions--nav">
            <Button variant="ghost" size="sm" disabled={saving || position === 0} onClick={() => goTo(position - 1)}>← Thẻ trước</Button>
            <Button variant="ghost" size="sm" disabled={saving || position >= queue.length - 1} onClick={() => goTo(position + 1)}>Thẻ sau →</Button>
          </div>
        </article>
      )}

      <div className="recall-study-footer">
        <DeleteDeck api={api} deck={deck} disabled={saving} onDeleted={onDeleted} onUnauthenticated={onUnauthenticated} />
      </div>
    </div>
  )
}
