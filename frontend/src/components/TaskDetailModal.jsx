import { useEffect, useState } from 'react'
import { Badge } from './ui/badge.jsx'
import { Checkbox } from './ui/checkbox.jsx'
import * as realRecallApi from '../lib/recall.js'
import {
  getTaskRecallLink,
  setTaskRecallLink,
  removeTaskRecallLink,
  validateAndSyncTaskRecallLink,
} from '../lib/taskRecallLinks.js'
import './TaskDetailModal.css'

const MOCK_REQUESTED = import.meta.env.DEV && typeof window !== 'undefined'
  && new URLSearchParams(window.location.search).has('recallMock')

export default function TaskDetailModal({
  task,
  subject,
  userId,
  onClose,
  onToggleCompleted,
  onNavigateRecall,
  onNavigateTimer,
  onLinkUpdated,
}) {
  const [decks, setDecks] = useState([])
  const [selectedDeckId, setSelectedDeckId] = useState('')
  const [loadingDecks, setLoadingDecks] = useState(true)
  const [deckError, setDeckError] = useState('')
  const [currentLink, setCurrentLink] = useState(() => getTaskRecallLink(task?.id, userId))
  const [linkNotice, setLinkNotice] = useState('')

  useEffect(() => {
    let current = true
    async function loadDecks() {
      if (!task?.goalId) {
        setLoadingDecks(false)
        return
      }
      setLoadingDecks(true)
      setDeckError('')
      try {
        let api = realRecallApi
        if (MOCK_REQUESTED) {
          const module = await import('../lib/recallMock.js')
          api = module.createRecallMock()
        }
        const sets = await api.listDecks({ goalId: task.goalId })
        if (current) {
          setDecks(sets || [])
          const validated = validateAndSyncTaskRecallLink(task.id, task.goalId, sets, userId)
          setCurrentLink(validated)
          if (validated && sets?.some(d => d.id === validated.deckId)) {
            setSelectedDeckId(validated.deckId)
          } else if (sets && sets.length > 0) {
            // Prefer first ACTIVE deck, otherwise first deck
            const activeOne = sets.find(d => d.status === 'ACTIVE')
            setSelectedDeckId(activeOne ? activeOne.id : sets[0].id)
          }
        }
      } catch (err) {
        if (current) {
          setDeckError(err.message || 'Không thể tải danh sách bộ flashcard của môn học.')
        }
      } finally {
        if (current) {
          setLoadingDecks(false)
        }
      }
    }

    loadDecks()
    return () => { current = false }
  }, [task?.id, task?.goalId, userId])

  if (!task) return null

  const selectedDeck = decks.find(d => d.id === selectedDeckId) || decks[0]
  const isSelectedDeckLinked = Boolean(currentLink && selectedDeck && currentLink.deckId === selectedDeck.id)

  function handleLink(deckToLink) {
    const target = deckToLink || selectedDeck
    if (!target || !task) return
    const link = setTaskRecallLink(task.id, {
      deckId: target.id,
      deckTitle: target.title,
      cardCount: target.cardCount,
      status: target.status,
      goalId: task.goalId,
    }, userId)
    setCurrentLink(link)
    setLinkNotice(`Đã liên kết bộ flashcard "${target.title}" với công việc này.`)
    onLinkUpdated?.(task.id, link)
    setTimeout(() => setLinkNotice(''), 3500)
  }

  function handleUnlink() {
    if (!task) return
    removeTaskRecallLink(task.id, userId)
    setCurrentLink(null)
    setLinkNotice('Đã gỡ liên kết bộ flashcard khỏi công việc này.')
    onLinkUpdated?.(task.id, null)
    setTimeout(() => setLinkNotice(''), 3500)
  }

  function handleOpenRecall(deckToOpen) {
    const target = deckToOpen || selectedDeck
    if (!target || !task) return
    // Always persist link when opening from task so it stays connected
    if (!currentLink || currentLink.deckId !== target.id) {
      const link = setTaskRecallLink(task.id, {
        deckId: target.id,
        deckTitle: target.title,
        cardCount: target.cardCount,
        status: target.status,
        goalId: task.goalId,
      }, userId)
      setCurrentLink(link)
      onLinkUpdated?.(task.id, link)
    }
    onClose()
    onNavigateRecall?.(task.goalId, target.id)
  }

  return (
    <div className="modal-overlay" role="dialog" aria-modal="true" aria-labelledby="task-detail-modal-title">
      <div className="task-detail-card">
        <div className="task-detail-header">
          <div>
            <span className="task-detail-eyebrow">CHI TIẾT CÔNG VIỆC</span>
            <h2 id="task-detail-modal-title">{task.title}</h2>
          </div>
          <button type="button" className="modal-close-btn" onClick={onClose} aria-label="Đóng chi tiết">
            ✕
          </button>
        </div>

        {/* Task Info Section */}
        <div className="task-detail-section task-info-box">
          <div className="task-info-grid">
            <div className="task-info-item">
              <span className="task-info-label">Môn học liên kết:</span>
              <span className="task-subject-tag-prominent">
                📚 {subject || 'Mục tiêu học tập'}
              </span>
            </div>

            <div className="task-info-item">
              <span className="task-info-label">Ngày học:</span>
              <strong>{task.scheduledDate}</strong>
            </div>

            <div className="task-info-item">
              <span className="task-info-label">Thời lượng dự kiến:</span>
              <strong>{task.estimatedMinutes} phút</strong>
            </div>

            <div className="task-info-item">
              <span className="task-info-label">Nguồn tạo:</span>
              <span>{task.source === 'ai' ? '✦ AI đề xuất' : 'Thủ công'}</span>
            </div>
          </div>

          <div className="task-status-row">
            <label className="task-status-checkbox-label">
              <Checkbox
                checked={Boolean(task.completed)}
                onCheckedChange={next => onToggleCompleted?.(task, next)}
                aria-label="Đánh dấu hoàn thành công việc"
              />
              <span className={task.completed ? 'task-status-text--done' : 'task-status-text'}>
                {task.completed ? '✓ Đã hoàn thành công việc này' : 'Chưa hoàn thành công việc'}
              </span>
            </label>
            {onNavigateTimer && (
              <button
                type="button"
                className="button button--secondary button--sm"
                onClick={() => { onClose(); onNavigateTimer(task) }}
              >
                ◷ Vào phiên tập trung
              </button>
            )}
          </div>
          <p className="task-independence-note">
            * Trạng thái hoàn thành của công việc và kết quả đánh giá thẻ ôn tập được duy trì độc lập.
          </p>
        </div>

        {/* Recall Flashcard Section */}
        <div className="task-detail-section task-recall-section">
          <div className="task-recall-head">
            <div className="task-recall-title-wrap">
              <h3>✦ Bộ thẻ flashcard của môn {subject || 'này'}</h3>
              {currentLink && (
                <span className="task-linked-badge" title={`Đã liên kết với: ${currentLink.deckTitle}`}>
                  🔗 Đã liên kết ({currentLink.deckTitle})
                </span>
              )}
            </div>
            <span className="task-recall-help-text">Ôn tập chủ động bằng câu hỏi - đáp án bám sát tài liệu</span>
          </div>

          {linkNotice && (
            <div className="task-link-notice" role="status">
              {linkNotice}
            </div>
          )}

          {loadingDecks ? (
            <div className="task-recall-loading" role="status">
              <span className="task-recall-spinner">⏳</span>
              <p>Đang tìm kiếm bộ flashcard của môn học…</p>
            </div>
          ) : deckError ? (
            <div className="task-recall-error" role="alert">
              <p>{deckError}</p>
              <button
                type="button"
                className="button button--secondary button--sm"
                onClick={() => {
                  setLoadingDecks(true)
                  realRecallApi.listDecks({ goalId: task.goalId })
                    .then(sets => { setDecks(sets || []); setDeckError('') })
                    .catch(e => setDeckError(e.message || 'Lỗi tải bộ thẻ.'))
                    .finally(() => setLoadingDecks(false))
                }}
              >
                Thử lại
              </button>
            </div>
          ) : decks.length === 0 ? (
            <div className="task-recall-empty">
              <span className="task-recall-empty-icon">🎴</span>
              <h4>Chưa có bộ flashcard nào cho môn học này</h4>
              <p>
                Để ôn tập hiệu quả cho công việc này, bạn có thể tải đề cương, tài liệu (PDF hoặc văn bản)
                để AI tạo bộ thẻ câu hỏi tự động.
              </p>
              <button
                type="button"
                className="button button--primary button--sm"
                onClick={() => {
                  onClose()
                  onNavigateRecall?.(task.goalId)
                }}
              >
                + Thêm tài liệu & tạo bộ thẻ ngay ↗
              </button>
            </div>
          ) : (
            <div className="task-recall-content">
              {decks.length > 1 && (
                <div className="task-deck-selector-row">
                  <label htmlFor="modal-deck-select">
                    Môn học có <strong>{decks.length} bộ flashcard</strong>. Chọn bộ thẻ phù hợp:
                  </label>
                  <select
                    id="modal-deck-select"
                    className="modal-input task-deck-select"
                    value={selectedDeckId}
                    onChange={e => setSelectedDeckId(e.target.value)}
                  >
                    {decks.map(d => (
                      <option key={d.id} value={d.id}>
                        {d.title} ({d.cardCount} thẻ · {d.status === 'ACTIVE' ? 'Đang dùng' : 'Bản nháp'})
                        {currentLink?.deckId === d.id ? ' [Đang liên kết]' : ''}
                      </option>
                    ))}
                  </select>
                </div>
              )}

              {selectedDeck && (
                <div className={`selected-deck-card ${isSelectedDeckLinked ? 'selected-deck-card--linked' : ''}`}>
                  <div className="selected-deck-header">
                    <div>
                      <div className="selected-deck-title-row">
                        <h4 className="selected-deck-title">{selectedDeck.title}</h4>
                        {isSelectedDeckLinked && (
                          <Badge variant="success">✓ Đang liên kết</Badge>
                        )}
                      </div>
                      <p className="selected-deck-meta">
                        {selectedDeck.cardCount} thẻ hỏi-đáp · Tạo ngày {new Intl.DateTimeFormat('vi-VN').format(new Date(selectedDeck.createdAt))}
                      </p>
                    </div>
                    <Badge variant={selectedDeck.status === 'ACTIVE' ? 'success' : 'outline'}>
                      {selectedDeck.status === 'ACTIVE' ? 'Đang dùng' : 'Bản nháp'}
                    </Badge>
                  </div>

                  <p className="selected-deck-hint">
                    {selectedDeck.status === 'ACTIVE'
                      ? 'Bộ thẻ đã sẵn sàng ôn tập. Bạn có thể mở trực tiếp để ôn tập chủ động ngay bây giờ.'
                      : 'Bộ thẻ đang ở trạng thái bản nháp do AI tạo. Bạn có thể xem trước, chỉnh sửa câu hỏi/đáp án và xác nhận dùng.'}
                  </p>

                  <div className="selected-deck-actions">
                    {isSelectedDeckLinked ? (
                      <button
                        type="button"
                        className="button button--secondary button--sm"
                        onClick={handleUnlink}
                        title="Hủy liên kết bộ thẻ này khỏi công việc"
                      >
                        Hủy liên kết
                      </button>
                    ) : (
                      <button
                        type="button"
                        className="button button--secondary button--sm"
                        onClick={() => handleLink(selectedDeck)}
                        title="Liên kết bộ flashcard này với công việc"
                      >
                        🔗 Liên kết với công việc
                      </button>
                    )}
                    <button
                      type="button"
                      className="button button--primary button--sm"
                      onClick={() => handleOpenRecall(selectedDeck)}
                      title="Mở ôn tập bộ thẻ này trong Recall"
                    >
                      {selectedDeck.status === 'ACTIVE' ? 'Ôn tập ngay ↗' : 'Xem bản nháp & ôn tập ↗'}
                    </button>
                  </div>
                </div>
              )}
            </div>
          )}
        </div>

        <div className="task-detail-footer">
          <button type="button" className="button button--secondary" onClick={onClose}>
            Đóng
          </button>
        </div>
      </div>
    </div>
  )
}
