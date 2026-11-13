import { useEffect, useRef, useState } from 'react'
import { Alert, AlertDescription } from '../ui/alert.jsx'
import { Button } from '../ui/button.jsx'
import { Input } from '../ui/input.jsx'
import { Label } from '../ui/label.jsx'
import {
  TEXT_LIMIT, TITLE_LIMIT, describeRecallError, fileToBase64, validatePdfFile, validateTextSource,
} from '../../lib/recall.js'

/** Adds a source: pasted text or a text PDF. Saving the source is its own step; cards are made afterwards. */
export default function SourceForm({ api, goalId, disabled, onCreated, onUnauthenticated }) {
  const [mode, setMode] = useState('text')
  const [title, setTitle] = useState('')
  const [content, setContent] = useState('')
  const [file, setFile] = useState(null)
  const [fileInputKey, setFileInputKey] = useState(0)
  const [errors, setErrors] = useState({})
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const submitRef = useRef(null)
  const failed = useRef(false)

  useEffect(() => {
    if (!saving && failed.current) { failed.current = false; submitRef.current?.focus() }
  }, [saving])

  async function submit(event) {
    event.preventDefault()
    if (saving || disabled) return
    const found = mode === 'text' ? validateTextSource({ title, content }) : validatePdfFile(file, title)
    setErrors(found)
    setError('')
    if (Object.keys(found).length) {
      const first = ['title', 'content', 'file'].find(name => found[name])
      document.getElementById(`recall-source-${first}`)?.focus()
      return
    }
    setSaving(true)
    try {
      const material = mode === 'text'
        ? await api.createTextMaterial({ goalId, title: title.trim(), content })
        : await api.createPdfMaterial({
          goalId, title: title.trim(), fileName: file.name, base64: await fileToBase64(file),
        })
      setTitle(''); setContent(''); setFile(null); setFileInputKey(value => value + 1)
      onCreated(material)
    } catch (err) {
      const info = describeRecallError(err)
      if (info.kind === 'unauthenticated') onUnauthenticated()
      setError(info.message)
      failed.current = true
    } finally {
      setSaving(false)
    }
  }

  const fieldError = name => errors[name] && <p className="recall-field-error" id={`recall-source-${name}-error`}>{errors[name]}</p>

  return (
    <form className="recall-form" onSubmit={submit} noValidate aria-busy={saving}>
      <fieldset className="recall-choice" disabled={saving}>
        <legend>Nguồn tài liệu</legend>
        <label><input type="radio" name="recall-source-mode" checked={mode === 'text'} onChange={() => { setMode('text'); setErrors({}) }} /> Dán văn bản</label>
        <label><input type="radio" name="recall-source-mode" checked={mode === 'pdf'} onChange={() => { setMode('pdf'); setErrors({}) }} /> Tải PDF có văn bản</label>
      </fieldset>

      <div className="recall-field">
        <Label htmlFor="recall-source-title">Tên nguồn</Label>
        <Input id="recall-source-title" value={title} maxLength={TITLE_LIMIT + 20} hasError={Boolean(errors.title)}
          aria-describedby={errors.title ? 'recall-source-title-error' : undefined} disabled={saving}
          onChange={event => setTitle(event.target.value)} placeholder="Ví dụ: Chương 3 — Kiểm thử" />
        {fieldError('title')}
      </div>

      {mode === 'text' ? (
        <div className="recall-field">
          <Label htmlFor="recall-source-content">Nội dung cần ôn</Label>
          <textarea id="recall-source-content" className={`shadcn-input recall-textarea${errors.content ? ' shadcn-input--error' : ''}`}
            rows={8} value={content} disabled={saving} onChange={event => setContent(event.target.value)}
            aria-describedby={`recall-source-content-help${errors.content ? ' recall-source-content-error' : ''}`}
            placeholder="Dán đoạn ghi chú hoặc giáo trình bạn muốn ôn" />
          <p className="recall-help" id="recall-source-content-help">
            {content.length.toLocaleString('vi-VN')} / {TEXT_LIMIT.toLocaleString('vi-VN')} ký tự.
          </p>
          {fieldError('content')}
        </div>
      ) : (
        <div className="recall-field">
          <Label htmlFor="recall-source-file">Tệp PDF</Label>
          <input key={fileInputKey} id="recall-source-file" className="recall-file" type="file" accept="application/pdf,.pdf"
            disabled={saving} onChange={event => setFile(event.target.files?.[0] ?? null)}
            aria-describedby={`recall-source-file-help${errors.file ? ' recall-source-file-error' : ''}`} />
          <p className="recall-help" id="recall-source-file-help">
            Tối đa 5 MiB, 100 trang, PDF có chữ. Tệp quét ảnh chưa được hỗ trợ.
          </p>
          {file && <p className="recall-help">Đã chọn: <strong>{file.name}</strong></p>}
          {fieldError('file')}
        </div>
      )}

      {error && <Alert variant="destructive"><AlertDescription>{error}</AlertDescription></Alert>}
      <div className="recall-actions">
        <Button type="submit" ref={submitRef} isLoading={saving} disabled={disabled}>{saving ? 'Đang lưu nguồn…' : 'Lưu nguồn tài liệu'}</Button>
      </div>
    </form>
  )
}
