import { execFileSync } from 'node:child_process'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const getField = (body, name, options) => {
  const match = body.match(new RegExp('^' + name + ':\\s*(.+?)\\s*$', 'im'))
  if (!match) return null
  const value = match[1].toLowerCase()
  return options.includes(value) ? value : 'invalid'
}

const hasExplanation = (body, name) => {
  const match = body.match(new RegExp('^' + name + ':\\s*(.+?)\\s*$', 'im'))
  return Boolean(match && match[1].trim().length >= 8)
}

export function assessTraceability({ title, body = '', changedFiles = [] }) {
  const relevantCode = changedFiles.some(path =>
    /^backend\/(src\/|pom\.xml$)/.test(path) ||
    /^frontend\/(src\/|package\.json$)/.test(path)
  )
  const relevantRequirements = changedFiles.some(path =>
    path === 'requirements.md' || /^docs\/product\/.*\.md$/.test(path)
  )
  if (!relevantCode && !relevantRequirements) {
    return { relevantCode, relevantRequirements, problems: [] }
  }

  const problems = []
  if (!/\bASP-\d+\b/i.test(title)) {
    problems.push('PR title must include an actual Jira key, e.g. [ASP-9].')
  }
  const requirementsImpact = getField(body, 'Requirements impact', ['none', 'updated', 'needs-po-decision'])
  const codeImpact = getField(body, 'Code impact', ['none', 'implemented', 'implementation-needed', 'no-behavior-change'])
  if (!requirementsImpact || requirementsImpact === 'invalid') {
    problems.push('Declare Requirements impact: none | updated | needs-po-decision in the PR body.')
  }
  if (!codeImpact || codeImpact === 'invalid') {
    problems.push('Declare Code impact: none | implemented | implementation-needed | no-behavior-change in the PR body.')
  }
  if (relevantRequirements && requirementsImpact === 'none') {
    problems.push('Requirements or product contract changed: Requirements impact cannot be none.')
  }
  if (relevantCode && codeImpact === 'none') {
    problems.push('Application code changed: Code impact cannot be none.')
  }
  if (relevantCode && requirementsImpact === 'none' && !hasExplanation(body, 'Requirements rationale')) {
    problems.push('Code changed without requirements changes: add Requirements rationale: explaining why AC/docs remain valid.')
  }
  if (relevantRequirements && !relevantCode && codeImpact === 'none' && !hasExplanation(body, 'Code rationale')) {
    problems.push('Requirements changed without code: add Code rationale: explaining why no implementation is needed.')
  }
  if (relevantCode && !hasExplanation(body, 'Test evidence')) {
    problems.push('Code changed: add Test evidence: with commands/results or a clear not-run reason.')
  }
  return { relevantCode, relevantRequirements, problems }
}

function main() {
  const base = process.env.GITHUB_BASE_REF
  if (!base || !/^[A-Za-z0-9_.\/-]+$/.test(base)) {
    throw new Error('GITHUB_BASE_REF is required and must be a valid branch name.')
  }
  execFileSync('git', ['fetch', '--no-tags', 'origin', base], { stdio: 'pipe' })
  const changedFiles = execFileSync('git', ['diff', '--name-only', 'origin/' + base + '...HEAD'], {
    encoding: 'utf8',
  }).trim().split('\n').filter(Boolean)
  const result = assessTraceability({
    title: process.env.PR_TITLE || '',
    body: process.env.PR_BODY || '',
    changedFiles,
  })
  console.log('Relevant app code: ' + result.relevantCode + '; requirements: ' + result.relevantRequirements)
  console.log('Changed files: ' + (changedFiles.join(', ') || '(none)'))
  if (result.problems.length) {
    for (const problem of result.problems) console.error('::error::' + problem)
    process.exitCode = 1
  } else {
    console.log('PR traceability declaration passed. This does not prove Jira/requirements/code equivalence.')
  }
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main()
}
