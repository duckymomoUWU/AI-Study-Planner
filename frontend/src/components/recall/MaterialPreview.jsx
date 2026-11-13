import { useState } from 'react'
import { Button } from '../ui/button.jsx'
import { describeRecallError } from '../../lib/recall.js'

/**
 * Shows the beginning of the text the server read from a source. A PDF can be read badly (broken diacritics, a
 * scan, columns in the wrong order), and the student otherwise only finds out from poor cards.
 */
export default function MaterialPreview({ api, material, onUnauthenticated }) {
  const [open, setOpen] = useState(false)
  const [loading, setLoading] = useState(false)
  const [detail, setDetail] = useState(null)
  const [error, setError] = useState('')
  const regionId = `recall-preview-${material.id}`

  async function toggle() {
    if (open) {
      setOpen(false)
      return
    }
    setOpen(true)
    if (detail || loading) return
    setLoading(true); setError('')
    try {
      setDetail(await api.getMaterial(material.id))
    } catch (err) {
      const info = describeRecallError(err)
      if (info.kind === 'unauthenticated') onUnauthenticated()
      setError(info.message)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="recall-preview-wrap">
      <Button size="sm" variant="ghost" aria-expanded={open} aria-controls={regionId} onClick={toggle}>
        {open ? 'Ẩn nội dung đã đọc' : 'Xem nội dung đã đọc'}
      </Button>
      {open && (
        <div id={regionId} className="recall-preview" aria-busy={loading}>
          {loading && <p className="recall-help" role="status">Đang tải…</p>}
          {error && <p className="recall-field-error">{error}</p>}
          {detail && (
            <>
              <p className="recall-help">
                {detail.kind === 'PDF' ? `PDF · ${detail.pageCount} trang · ` : ''}{detail.characterCount.toLocaleString('vi-VN')} ký tự.{' '}
                {detail.previewTruncated
                  ? 'Chỉ hiện phần đầu. Hãy kiểm tra chữ tiếng Việt có đúng dấu không trước khi tạo thẻ.'
                  : 'Đây là toàn bộ chữ máy chủ đã đọc được.'}
              </p>
              <pre className="recall-preview-text">{detail.preview}</pre>
            </>
          )}
        </div>
      )}
    </div>
  )
}
