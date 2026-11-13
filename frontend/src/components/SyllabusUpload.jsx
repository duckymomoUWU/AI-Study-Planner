import { useEffect, useRef, useState } from 'react'
import { parseSyllabusDocument, syllabusFileInput, MAX_TEXT_LENGTH } from '../lib/syllabus.js'
import { requestRevision, selectedOutlineTopics } from '../lib/planningScope.js'
import './SyllabusUpload.css'

export default function SyllabusUpload({ onApply, disabled }) {
  const [open, setOpen] = useState(false)
  const [text, setText] = useState('')
  const [name, setName] = useState('')
  const [result, setResult] = useState(null)
  const [selected, setSelected] = useState([])
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const active = useRef(null)
  const revision = useRef(requestRevision())
  useEffect(() => {
    const gate = revision.current
    return () => { gate.next(); active.current?.abort() }
  }, [])

  async function extract(readInput) {
    const ticket = revision.current.next()
    active.current?.abort()
    const controller = new AbortController()
    active.current = controller
    setBusy(true); setError(''); setResult(null)
    try {
      const input = await readInput()
      if (!revision.current.isCurrent(ticket)) return
      const value = await parseSyllabusDocument(input, { signal: controller.signal })
      if (!revision.current.isCurrent(ticket)) return
      setResult(value)
      setSelected(value.chapters.flatMap(chapter => chapter.topics.map(topic => topic.id)))
    } catch (err) {
      if (revision.current.isCurrent(ticket) && err.name !== 'AbortError') setError(err.status === 401
        ? 'Bạn cần đăng nhập để đọc tài liệu.' : `${err.message} Bạn có thể tải lại hoặc nhập chủ đề thủ công bên dưới.`)
    } finally { if (revision.current.isCurrent(ticket)) { active.current = null; setBusy(false) } }
  }

  function editTopic(id, field, value) {
    setResult(previous => ({ ...previous, chapters: previous.chapters.map(chapter => ({ ...chapter,
      topics: chapter.topics.map(topic => topic.id === id ? { ...topic, [field]: value } : topic),
    })) }))
  }

  const topics = result ? selectedOutlineTopics(result.chapters, selected) : []
  return <section className="syllabus-upload" aria-label="Tải tài liệu đề cương">
    <button type="button" className="button button--secondary" aria-expanded={open} onClick={() => setOpen(!open)}>
      {open ? 'Đóng công cụ tải tài liệu' : 'Tải đề cương và chọn chủ đề ôn thi'}
    </button>
    {open && <div className="syllabus-upload-body">
      <p>1. Tải PDF, DOCX, TXT, MD (tối đa 5 MB). 2. Rà soát chủ đề và ngày thi trong tài liệu. 3. Xác nhận rồi nhập quỹ thời gian của bạn bên dưới.</p>
      <label>Chọn tài liệu<input aria-label="Chọn tài liệu đề cương" type="file" accept=".pdf,.docx,.txt,.md,.text" disabled={disabled} onChange={event => {
        const file = event.target.files?.[0]; event.target.value = ''
        if (file) { setName(file.name); extract(() => syllabusFileInput(file)) }
      }} /></label>
      {name && <small>Tệp: {name}</small>}
      <label>Nội dung đề cương<textarea aria-label="Nội dung đề cương" rows={4} value={text} maxLength={MAX_TEXT_LENGTH} disabled={disabled} onChange={e => {
        revision.current.next(); active.current?.abort(); setBusy(false); setResult(null); setText(e.target.value)
      }} /></label>
      <button type="button" disabled={disabled || !text.trim()} onClick={() => { setName('Văn bản đã dán'); extract(() => ({ content: text, fileName: 'de_cuong.txt', mimeType: 'text/plain' })) }}>Đọc nội dung đã dán</button>
      {busy && <p role="status">Đang đọc tài liệu…</p>}
      {error && <p role="alert">{error}</p>}
      {result && <div className="syllabus-result">
        <p>{result.extractionMethod === 'gemini' ? 'AI đã trích chủ đề và máy chủ đã đối chiếu trích dẫn với tài liệu.' : `Đã đọc văn bản ${result.extractionMethod === 'local_pdf' ? 'trong PDF' : 'trong tài liệu'}.`} Chủ đề là đề xuất cần bạn xác nhận. Ngày thi và phút ôn không lấy từ thời lượng thi hay số tiết.</p>
        <label>Môn học<input value={result.subject} maxLength={100} onChange={e => setResult({ ...result, subject: e.target.value })} /></label>
        <label>Ngày thi ghi trong đề cương (kiểm tra và xác nhận)
          <input type="date" value={result.examDate || ''} onChange={e => setResult({ ...result, examDate: e.target.value })} />
        </label>
        {!result.examDate && <small>{result.examDateCandidates?.length ? 'Có nhiều ngày thi trong tài liệu. Hãy chọn đúng ngày của bạn bên dưới hoặc nhập lại.' : 'Chưa tìm thấy ngày thi cụ thể. Bạn có thể tự nhập; app không đoán ngày thi.'}</small>}
        {result.examDateCandidates?.map((candidate, index) => <div key={index}>
          <button type="button" onClick={() => setResult({ ...result, examDate: candidate.date })}>Chọn ngày {candidate.date}</button>
          <small>Nguồn: {candidate.source.page ? `trang ${candidate.source.page} · ` : ''}{candidate.source.section}</small>
        </div>)}
        {result.chapters.map(chapter => <fieldset key={chapter.id}>
          <legend>{chapter.title}</legend>
          {chapter.topics.map(topic => <div key={topic.id} className="goal-field">
            <label><input type="checkbox" checked={selected.includes(topic.id)} onChange={e => setSelected(previous => e.target.checked ? [...previous, topic.id] : previous.filter(id => id !== topic.id))} /> Ôn chủ đề này</label>
            <input aria-label={`Tên chủ đề ${topic.id}`} value={topic.title} maxLength={200} onChange={e => editTopic(topic.id, 'title', e.target.value)} />
            {topic.aliases?.length > 0 && <small>Tên song ngữ: {topic.aliases.join(' / ')}</small>}
            <small>Nguồn: {topic.sources.map(source => `${source.page ? `trang ${source.page}, ` : ''}${source.section}`).join('; ')}{topic.needsReview ? ' · Cần rà soát' : ''}</small>
            <label>Ưu tiên<select value={topic.priority || 'MEDIUM'} onChange={e => editTopic(topic.id, 'priority', e.target.value)}>
              <option value="HIGH">Cao</option><option value="MEDIUM">Trung bình (mặc định)</option><option value="LOW">Thấp</option>
            </select></label>
          </div>)}
        </fieldset>)}
        <button type="button" className="button button--primary" disabled={disabled || !topics.length || topics.some(topic => !topic.title.trim())} onClick={() => {
          onApply({ subject: result.subject, topics: topics.map(topic => topic.title).join('\n'), selectedTopics: topics, examDate: result.examDate || null })
          setResult(null); setOpen(false)
        }}>Xác nhận {topics.length} chủ đề để ôn tập</button>
      </div>}
    </div>}
  </section>
}
