import { useCallback, useEffect, useRef, useState } from 'react'
import { Alert, AlertDescription } from '../components/ui/alert.jsx'
import { Badge } from '../components/ui/badge.jsx'
import { Button } from '../components/ui/button.jsx'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../components/ui/card.jsx'
import { Label } from '../components/ui/label.jsx'
import DeckEditor from '../components/recall/DeckEditor.jsx'
import GenerateForm from '../components/recall/GenerateForm.jsx'
import PagedList from '../components/recall/PagedList.jsx'
import SourceForm from '../components/recall/SourceForm.jsx'
import StudyDeck from '../components/recall/StudyDeck.jsx'
import { listGoals } from '../lib/goals.js'
import * as realApi from '../lib/recall.js'
import { describeRecallError } from '../lib/recall.js'
import './ActiveRecallPage.css'

// The sample-data mock exists only in `npm run dev` and only with ?recallMock=1; a production build never selects it.
const MOCK_REQUESTED = import.meta.env.DEV && typeof window !== 'undefined'
  && new URLSearchParams(window.location.search).has('recallMock')

const dateLabel = iso => new Intl.DateTimeFormat('vi-VN', { dateStyle: 'medium', timeZone: 'Asia/Ho_Chi_Minh' }).format(new Date(iso))

function useRecallApi() {
  const [api, setApi] = useState(MOCK_REQUESTED ? null : realApi)
  useEffect(() => {
    if (!MOCK_REQUESTED) return undefined
    let current = true
    const faults = (new URLSearchParams(window.location.search).get('recallFail') || '').split(',').filter(Boolean)
    import('../lib/recallMock.js').then(module => { if (current) setApi(module.createRecallMock({ faults })) })
    return () => { current = false }
  }, [])
  return api
}

export default function ActiveRecallPage({ currentUser, initialGoalId, initialDeckId, onNavigateAuth }) {
  const api = useRecallApi()
  const [goals, setGoals] = useState([])
  const [goalsLoaded, setGoalsLoaded] = useState(false)
  const [goalId, setGoalId] = useState(initialGoalId || '')
  const [pendingDeckId, setPendingDeckId] = useState(initialDeckId || null)
  const [materials, setMaterials] = useState([])
  const [decks, setDecks] = useState([])
  const [loaded, setLoaded] = useState(false)
  const [error, setError] = useState('')
  const [sessionExpired, setSessionExpired] = useState(false)
  const unauthenticated = !currentUser || sessionExpired
  const [revision, setRevision] = useState(0)
  const [expanded, setExpanded] = useState('')
  const [open, setOpen] = useState(null) // { deck, cards } of the deck being edited or studied
  const [opening, setOpening] = useState(false)
  const deckTitleRef = useRef(null)
  const pageTitleRef = useRef(null)
  const hadDeck = useRef(false)

  useEffect(() => {
    if (initialGoalId) setGoalId(initialGoalId)
  }, [initialGoalId])

  useEffect(() => {
    if (initialDeckId) setPendingDeckId(initialDeckId)
  }, [initialDeckId])

  // Opening, confirming or leaving a deck swaps the whole view; move focus to the new heading instead of losing it.
  const openDeckId = open?.deck.id
  const openStatus = open?.deck.status
  useEffect(() => {
    if (openDeckId) deckTitleRef.current?.focus()
    else if (hadDeck.current) pageTitleRef.current?.focus()
    hadDeck.current = Boolean(openDeckId)
  }, [openDeckId, openStatus])

  const markUnauthenticated = useCallback(() => setSessionExpired(true), [])
  const refresh = () => setRevision(value => value + 1)

  useEffect(() => {
    if (!currentUser) return undefined
    const controller = new AbortController()
    let current = true
    listGoals({ signal: controller.signal })
      .then(items => {
        if (!current) return
        setGoals(items)
        setGoalId(previous => (items.some(goal => goal.id === previous) ? previous : items[0]?.id ?? ''))
      })
      .catch(err => {
        if (!current || err.name === 'AbortError') return
        if (err.status === 401) setSessionExpired(true)
        else setError(err.message || 'Không tải được danh sách mục tiêu.')
      })
      .finally(() => { if (current) setGoalsLoaded(true) })
    return () => { current = false; controller.abort() }
  }, [currentUser])

  useEffect(() => {
    if (!api || !goalId || unauthenticated) return undefined
    const controller = new AbortController()
    let current = true
    Promise.all([
      api.listMaterials({ goalId }, { signal: controller.signal }),
      api.listDecks({ goalId }, { signal: controller.signal }),
    ]).then(([sources, sets]) => {
      if (!current) return
      setMaterials(sources); setDecks(sets); setError('')
    }).catch(err => {
      if (!current || err.name === 'AbortError') return
      const info = describeRecallError(err)
      if (info.kind === 'unauthenticated') setSessionExpired(true)
      else if (err.status === 404 || err.status === 405) setError('Chức năng ôn tập chưa khả dụng trên máy chủ này.')
      else setError(info.message)
    }).finally(() => { if (current) setLoaded(true) })
    return () => { current = false; controller.abort() }
  }, [api, goalId, revision, unauthenticated])

  useEffect(() => {
    if (!api || !pendingDeckId || open || opening || unauthenticated) return undefined
    let current = true
    setOpening(true)
    api.getDeck(pendingDeckId)
      .then(detail => {
        if (!current) return
        setOpen(detail)
        if (detail?.deck?.goalId) setGoalId(detail.deck.goalId)
        setPendingDeckId(null)
      })
      .catch(err => {
        if (!current) return
        const info = describeRecallError(err)
        setError(info.message)
        setPendingDeckId(null)
      })
      .finally(() => {
        if (current) setOpening(false)
      })
    return () => { current = false }
  }, [api, pendingDeckId, open, opening, unauthenticated])

  async function openDeck(deckId) {
    if (opening) return
    setOpening(true); setError('')
    try {
      setOpen(await api.getDeck(deckId))
      window.scrollTo({ top: 0 })
    } catch (err) {
      const info = describeRecallError(err)
      if (info.kind === 'unauthenticated') markUnauthenticated()
      setError(info.message)
      if (info.kind === 'reload') refresh()
    } finally {
      setOpening(false)
    }
  }

  async function reloadOpenDeck() {
    if (!open) return
    try {
      setOpen(await api.getDeck(open.deck.id))
    } catch (err) {
      setOpen(null)
      if (err.status === 401) markUnauthenticated()
      refresh()
    }
  }

  function closeDeck() {
    setOpen(null); refresh()
  }

  if (unauthenticated) {
    return (
      <section className="recall-page" aria-labelledby="recall-title">
        <header><h1 id="recall-title">Ôn tập chủ động</h1></header>
        <Card><CardContent className="recall-empty">
          <p>Đăng nhập để tạo bộ thẻ từ tài liệu của bạn và lưu kết quả tự đánh giá.</p>
          <Button onClick={onNavigateAuth}>Đăng nhập</Button>
        </CardContent></Card>
      </section>
    )
  }

  const loading = !api || !goalsLoaded || (Boolean(goalId) && !loaded)

  return (
    <section className="recall-page" aria-labelledby="recall-title">
      <header className="recall-header">
        <h1 id="recall-title" ref={pageTitleRef} tabIndex={-1}>Ôn tập chủ động</h1>
        <p>Biến tài liệu của bạn thành thẻ hỏi–đáp, lật đáp án rồi tự đánh giá mình nhớ hay chưa.</p>
      </header>

      {MOCK_REQUESTED && (
        <Alert role="status"><AlertDescription>
          Chế độ dữ liệu mẫu để phát triển: thẻ hiển thị không do máy chủ hay AI tạo và sẽ mất khi tải lại trang.
        </AlertDescription></Alert>
      )}
      {error && (
        <Alert variant="destructive"><AlertDescription>
          <p>{error}</p>
          <Button size="sm" variant="outline" onClick={() => { setError(''); refresh() }}>Thử lại</Button>
        </AlertDescription></Alert>
      )}

      {loading && !error ? <p role="status" className="recall-loading">Đang tải…</p> : null}

      {goalsLoaded && goals.length === 0 && !error && (
        <Card><CardContent className="recall-empty">
          <p>Bạn chưa có mục tiêu học tập nào. Hãy tạo mục tiêu trước để gắn tài liệu ôn tập.</p>
          <a className="recall-link" href="#goals">Tạo mục tiêu</a>
        </CardContent></Card>
      )}

      {goals.length > 0 && api && (
        <>
          <div className="recall-goal">
            <Label htmlFor="recall-goal">Mục tiêu</Label>
            <select id="recall-goal" className="shadcn-input recall-select" value={goalId} disabled={Boolean(open)}
              onChange={event => { setGoalId(event.target.value); setLoaded(false); setExpanded('') }}>
              {goals.map(goal => <option key={goal.id} value={goal.id}>{goal.subject}</option>)}
            </select>
          </div>

          {open ? (
            <div className="recall-deck-view">
              <div className="recall-deck-title">
                <Button variant="ghost" size="sm" onClick={closeDeck}>← Danh sách bộ thẻ</Button>
                <h2 ref={deckTitleRef} tabIndex={-1}>{open.deck.title}</h2>
                <Badge variant={open.deck.status === 'ACTIVE' ? 'success' : 'outline'}>
                  {open.deck.status === 'ACTIVE' ? 'Đang dùng' : 'Bản nháp'}
                </Badge>
              </div>
              {open.deck.status === 'DRAFT' ? (
                <DeckEditor key={open.deck.id} api={api} deck={open.deck} cards={open.cards}
                  onReload={reloadOpenDeck} onActivated={reloadOpenDeck} onDeleted={closeDeck}
                  onUnauthenticated={markUnauthenticated} />
              ) : (
                <StudyDeck key={open.deck.id} api={api} deck={open.deck} initialCards={open.cards}
                  onDeleted={closeDeck} onUnauthenticated={markUnauthenticated} />
              )}
            </div>
          ) : (
            <>
              <Card>
                <CardHeader>
                  <CardTitle>1. Thêm nguồn tài liệu</CardTitle>
                  <CardDescription>Dán văn bản hoặc tải PDF có chữ. Nguồn được lưu lại trước, rồi bạn mới nhờ AI tạo thẻ.</CardDescription>
                </CardHeader>
                <CardContent>
                  <SourceForm api={api} goalId={goalId} onUnauthenticated={markUnauthenticated}
                    onCreated={material => { setMaterials(previous => [material, ...previous]); setExpanded(material.id) }} />
                </CardContent>
              </Card>

              <Card>
                <CardHeader>
                  <CardTitle>2. Tạo bộ thẻ từ nguồn</CardTitle>
                  <CardDescription>AI chỉ dùng nguồn bạn chọn. Bản nháp luôn cần bạn xem và xác nhận.</CardDescription>
                </CardHeader>
                <CardContent>
                  {materials.length === 0 ? (
                    <p className="recall-help">Chưa có nguồn nào cho mục tiêu này. Hãy thêm nguồn ở bước 1.</p>
                  ) : (
                    <PagedList key={goalId} items={materials} noun="nguồn" className="recall-material-list"
                      renderItem={material => (
                        <li key={material.id} className="recall-material">
                          <div className="recall-material-head">
                            <div>
                              <strong>{material.title}</strong>
                              <p className="recall-help">
                                {material.kind === 'PDF' ? `PDF · ${material.pageCount ?? '?'} trang` : 'Văn bản'} · {material.characterCount?.toLocaleString('vi-VN')} ký tự · {dateLabel(material.createdAt)}
                              </p>
                            </div>
                            <Button size="sm" variant={expanded === material.id ? 'ghost' : 'outline'}
                              aria-expanded={expanded === material.id}
                              onClick={() => setExpanded(expanded === material.id ? '' : material.id)}>
                              {expanded === material.id ? 'Đóng' : 'Tạo thẻ'}
                            </Button>
                          </div>
                          {expanded === material.id && (
                            <GenerateForm api={api} material={material} onUnauthenticated={markUnauthenticated}
                              onGenerated={deck => { setExpanded(''); refresh(); openDeck(deck.id) }} />
                          )}
                        </li>
                      )} />
                  )}
                </CardContent>
              </Card>

              <Card>
                <CardHeader>
                  <CardTitle>Bộ thẻ của bạn</CardTitle>
                  <CardDescription>Mở một bộ thẻ để xem lại bản nháp hoặc ôn tập.</CardDescription>
                </CardHeader>
                <CardContent>
                  {decks.length === 0 ? (
                    <p className="recall-help">Bạn chưa có bộ thẻ nào cho mục tiêu này.</p>
                  ) : (
                    <PagedList key={goalId} items={decks} noun="bộ thẻ" className="recall-deck-list"
                      renderItem={deck => (
                        <li key={deck.id} className="recall-deck-item">
                          <div>
                            <strong>{deck.title}</strong>
                            <p className="recall-help">{deck.cardCount} thẻ · {dateLabel(deck.createdAt)}</p>
                          </div>
                          <Badge variant={deck.status === 'ACTIVE' ? 'success' : 'outline'}>
                            {deck.status === 'ACTIVE' ? 'Đang dùng' : 'Bản nháp'}
                          </Badge>
                          <Button size="sm" variant="outline" isLoading={opening} onClick={() => openDeck(deck.id)}>
                            {deck.status === 'ACTIVE' ? 'Ôn tập' : 'Xem bản nháp'}
                          </Button>
                        </li>
                      )} />
                  )}
                </CardContent>
              </Card>
            </>
          )}
        </>
      )}
    </section>
  )
}
