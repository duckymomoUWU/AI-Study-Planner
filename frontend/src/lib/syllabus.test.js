import test from 'node:test'
import assert from 'node:assert/strict'
import { syllabusFileInput, parseSyllabusDocument, SyllabusApiError, MAX_FILE_BYTES } from './syllabus.js'
import { clearCsrfTokenCache } from './auth.js'

test('TXT/MD is text; PDF and DOCX are binary base64', async () => {
  const text = await syllabusFileInput(new File(['Subject: Databases'], 'course.md'))
  assert.equal(text.content, 'Subject: Databases')
  assert.equal(text.mimeType, 'text/plain')
  for (const [name, prefix] of [['course.pdf', '%PDF-1.7'], ['course.docx', 'PK-test']]) {
    const input = await syllabusFileInput(new File([prefix], name))
    assert.equal(atob(input.base64), prefix)
    assert.equal(input.content, undefined)
    assert.ok(input.mimeType.includes(name.endsWith('pdf') ? 'pdf' : 'wordprocessingml'))
  }
})

test('invalid, empty or oversized files are rejected before network requests', async () => {
  await assert.rejects(syllabusFileInput(new File([], 'empty.txt')))
  await assert.rejects(syllabusFileInput(new File(['binary'], 'bad.exe')))
  await assert.rejects(syllabusFileInput({ name: 'large.pdf', size: MAX_FILE_BYTES + 1 }))
  await assert.rejects(syllabusFileInput(new File(['x'.repeat(30001)], 'large.txt')))
})

test('syllabus uses authenticated v1 POST and returns explicit extraction mode without saving a goal', async t => {
  clearCsrfTokenCache()
  const calls = []
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    calls.push(url)
    if (url.endsWith('/csrf')) return Response.json({ data: { token: 'token' } })
    assert.equal(url, '/api/v1/goals/parse-syllabus')
    assert.equal(options.method, 'POST')
    assert.equal(options.headers['X-CSRF-TOKEN'], 'token')
    assert.equal(options.credentials, 'same-origin')
    assert.equal(JSON.parse(options.body).content, 'Subject: Databases')
    return Response.json({ data: { subject: 'Databases', topics: 'SQL JOIN', extractionMethod: 'local' } })
  })
  assert.equal((await parseSyllabusDocument({ content: 'Subject: Databases' })).extractionMethod, 'local')
  assert.equal(calls.length, 2)
})

test('provider/auth/validation failures remain errors, not fabricated fields', async t => {
  for (const status of [400, 401, 403, 503, 504]) {
    clearCsrfTokenCache()
    t.mock.method(globalThis, 'fetch', async url => url.endsWith('/csrf')
      ? Response.json({ data: { token: 'token' } })
      : Response.json({ error: { code: 'FAILURE', message: 'failed' } }, { status }))
    await assert.rejects(parseSyllabusDocument({ content: 'text' }), e => e instanceof SyllabusApiError && e.status === status)
  }
})
