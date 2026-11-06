import { test } from 'node:test'
import assert from 'node:assert/strict'
import { assessTraceability } from './check-pr-traceability.mjs'

const title = '[ASP-22] Goal API contract'

test('ignores non-product docs and workflow files', () => {
  assert.deepEqual(assessTraceability({ title: 'Docs cleanup', changedFiles: ['docs/README.md'] }).problems, [])
})

test('rejects change without Jira key and impact fields', () => {
  const result = assessTraceability({ title: 'Feature', body: '', changedFiles: ['backend/src/main/java/Goal.java'] })
  assert.equal(result.problems.length, 4)
})

test('accepts code-only change with explicit no requirements impact reason and tests', () => {
  const body = 'Requirements impact: none\nRequirements rationale: Internal refactor, no product behavior changed.\nCode impact: no-behavior-change\nTest evidence: Maven unit tests passed in CI.'
  assert.deepEqual(assessTraceability({ title, body, changedFiles: ['backend/src/main/java/Goal.java'] }).problems, [])
})

test('requires code impact plan when requirements change', () => {
  const body = 'Requirements impact: updated\nCode impact: implementation-needed'
  assert.deepEqual(assessTraceability({ title, body, changedFiles: ['requirements.md'] }).problems, [])
})

test('requires rationale for requirements-only change with no code follow up', () => {
  const body = 'Requirements impact: updated\nCode impact: none'
  assert.equal(assessTraceability({ title, body, changedFiles: ['requirements.md'] }).problems.length, 1)
  assert.deepEqual(assessTraceability({ title, body: body + '\nCode rationale: Documentation clarification with no behavior change.', changedFiles: ['requirements.md'] }).problems, [])
})

test('rejects code changes declared as none, even if requirements status is updated', () => {
  const body = 'Requirements impact: updated\nCode impact: none\nTest evidence: Unit tests not run pending implementation.'
  assert.ok(assessTraceability({ title, body, changedFiles: ['backend/pom.xml', 'requirements.md'] }).problems.some(x => x.includes('cannot be none')))
})

test('rejects code changes without honest test evidence', () => {
  const body = 'Requirements impact: updated\nCode impact: implemented'
  assert.ok(assessTraceability({ title, body, changedFiles: ['frontend/src/App.jsx'] }).problems.some(x => x.includes('Test evidence')))
})
