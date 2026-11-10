import { useEffect, useRef, useState } from 'react'
import { parseSyllabusDocument, syllabusFileInput, MAX_TEXT_LENGTH } from '../lib/syllabus.js'
import './SyllabusUpload.css'

export default function SyllabusUpload({ onApply, disabled }) {
  const [open, setOpen] = useState(false)
  const [text, setText] = useState('')
  const [name, setName] = useState('')
  const [result, setResult] = useState(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const active = useRef(null)
  useEffect(() => () => active.current?.abort(), [])

  async function extract(input) {
    active.current?.abort()
    const controller = new AbortController()
    active.current = controller
    setBusy(true); setError(''); setResult(null)
    try { setResult(await parseSyllabusDocument(input, { signal: controller.signal })) }
    catch (err) {
      if (err.name !== 'AbortError') setError(err.status === 503
        ? 'Chưa cấu hình hoặc chưa kết nối được Gemini để đọc tài liệu này. PDF cần GEMINI_API_KEY và GEMINI_MODEL ở backend. Bạn vẫn có thể điền mục tiêu thủ công.'
        : err.status === 401 ? 'Bạn cần đăng nhập để tải và đọc tài liệu.' : err.message)
    } finally { if (active.current === controller) { active.current = null; setBusy(false) } }
  }

  async function choose(event) {
    const file = event.target.files?.[0]
    event.target.value = ''
    if (!file) return
    setName(file.name); setResult(null); setError(''); setBusy(true)
    try { await extract(await syllabusFileInput(file)) }
    catch (err) { setError(err.message); setBusy(false) }
  }

  return <section className="syllabus-upload" aria-label="Tải tài liệu đề cương">
    <button type="button" className="button button--secondary" aria-expanded={open} onClick={() => setOpen(!open)}>
      {open ? 'Đóng công cụ tải tài liệu' : 'Tải tài liệu / đề cương để điền nhanh'}
    </button>
    {open && <div className="syllabus-upload-body">
      <p>Chọn PDF, DOCX, TXT, MD (tối đa 5 MB) hoặc dán nội dung. Kết quả chỉ điền biểu mẫu khi bạn đồng ý; chưa tạo mục tiêu hay công việc.</p>
      <label className="syllabus-file-label">Chọn tài liệu
        <input aria-label="Chọn tài liệu đề cương" type="file" accept=".pdf,.docx,.txt,.md,.text" disabled={disabled || busy} onChange={choose} />
      </label>
      {name && <small>Tệp: {name}</small>}
      <label>Nội dung đề cương
        <textarea aria-label="Nội dung đề cương" rows={4} value={text} maxLength={MAX_TEXT_LENGTH} disabled={disabled || busy} onChange={e => setText(e.target.value)} placeholder="Dán nội dung đề cương tại đây…" />
      </label>
      <button type="button" className="button button--secondary" disabled={disabled || busy || !text.trim()} onClick={() => { setName('Văn bản đã dán'); extract({ content: text, fileName: 'de_cuong.txt', mimeType: 'text/plain' }) }}>Đọc nội dung đã dán</button>
      {busy && <p role="status">Đang đọc tài liệu…</p>}
      {error && <p role="alert">{error}</p>}
      {result && <div className="syllabus-result">
        <p role="status">{result.extractionMethod === 'gemini' ? 'Đã trích xuất bằng Gemini.' : 'Đã trích xuất cục bộ từ văn bản (không dùng Gemini).'} Hãy kiểm tra thông tin đề xuất.</p>
        <dl><dt>Môn học</dt><dd>{result.subject}</dd><dt>Chủ đề</dt><dd>{result.topics}</dd><dt>Ngày học / ngày thi</dt><dd>{result.startDate} → {result.examDate}</dd><dt>Thời gian</dt><dd>{result.minutesPerDay} phút/ngày</dd></dl>
        <button type="button" className="button button--primary" disabled={disabled} onClick={() => { onApply(result); setResult(null); setOpen(false) }}>Điền vào biểu mẫu để kiểm tra</button>
      </div>}
    </div>}
  </section>
}
