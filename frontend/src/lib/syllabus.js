import { getCsrfToken } from './auth.js'

export class SyllabusApiError extends Error {
  constructor(message, status, code) {
    super(message); this.name = 'SyllabusApiError'; this.status = status; this.code = code
  }
}
const DOCX = 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'
export const MAX_FILE_BYTES = 5 * 1024 * 1024
export const MAX_TEXT_LENGTH = 30000

export async function syllabusFileInput(file) {
  if (!file || file.size === 0) throw new Error('Tệp rỗng. Vui lòng chọn tài liệu có nội dung.')
  if (file.size > MAX_FILE_BYTES) throw new Error('Tệp tối đa 5 MB.')
  const extension = file.name.split('.').pop().toLowerCase()
  if (['txt', 'md', 'text'].includes(extension)) {
    const content = await file.text()
    if (!content.trim()) throw new Error('Tệp không có nội dung văn bản.')
    if (content.length > MAX_TEXT_LENGTH) throw new Error('Nội dung tối đa 30.000 ký tự.')
    return { content, fileName: file.name, mimeType: 'text/plain' }
  }
  if (!['pdf', 'docx'].includes(extension)) throw new Error('Chỉ hỗ trợ PDF, DOCX, TXT và MD.')
  const bytes = new Uint8Array(await file.arrayBuffer())
  let binary = ''
  for (let i = 0; i < bytes.length; i += 8192) binary += String.fromCharCode(...bytes.subarray(i, i + 8192))
  return { base64: btoa(binary), fileName: file.name, mimeType: extension === 'pdf' ? 'application/pdf' : DOCX }
}

export async function parseSyllabusDocument(input, { signal } = {}) {
  const token = await getCsrfToken()
  const response = await fetch('/api/v1/goals/parse-syllabus', {
    method: 'POST', credentials: 'same-origin', signal,
    headers: { Accept: 'application/json', 'Content-Type': 'application/json', 'X-CSRF-TOKEN': token },
    body: JSON.stringify(input),
  })
  const payload = await response.json().catch(() => null)
  if (!response.ok) throw new SyllabusApiError(payload?.error?.message || `HTTP ${response.status}`, response.status, payload?.error?.code)
  const data = payload?.data
  if (!data || typeof data.subject !== 'string' || typeof data.topics !== 'string' || !['gemini', 'local'].includes(data.extractionMethod))
    throw new SyllabusApiError('Phản hồi trích xuất tài liệu không hợp lệ.', 502, 'INVALID_RESPONSE')
  return data
}
