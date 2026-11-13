import { test } from 'node:test'
import assert from 'node:assert/strict'
import { getSafeRedirectRoute, extractSafeRedirect } from './navigation.js'

test('getSafeRedirectRoute accepts valid allowlisted internal routes', () => {
  assert.equal(getSafeRedirectRoute('goals'), 'goals')
  assert.equal(getSafeRedirectRoute('timer'), 'timer')
  assert.equal(getSafeRedirectRoute('recall'), 'recall')
  assert.equal(getSafeRedirectRoute('#timer'), 'timer')
  assert.equal(getSafeRedirectRoute('/daily'), 'daily')
  assert.equal(getSafeRedirectRoute('#/overview'), 'overview')
  assert.equal(getSafeRedirectRoute('landing'), 'landing')
  assert.equal(getSafeRedirectRoute('#timer?taskId=123'), 'timer')
})

test('getSafeRedirectRoute blocks Open Redirect and external URLs, falling back to goals', () => {
  // External URLs
  assert.equal(getSafeRedirectRoute('https://evil.com'), 'goals')
  assert.equal(getSafeRedirectRoute('http://evil.com'), 'goals')
  assert.equal(getSafeRedirectRoute('//attacker.org/steal'), 'goals')
  assert.equal(getSafeRedirectRoute('javascript:alert(1)'), 'goals')
  assert.equal(getSafeRedirectRoute('data:text/html,<script>'), 'goals')

  // Disallowed internal or random names
  assert.equal(getSafeRedirectRoute('admin'), 'goals')
  assert.equal(getSafeRedirectRoute('unknown_route'), 'goals')
  assert.equal(getSafeRedirectRoute(''), 'goals')
  assert.equal(getSafeRedirectRoute(null), 'goals')
  assert.equal(getSafeRedirectRoute(undefined), 'goals')
})

test('extractSafeRedirect parses and sanitizes redirect param correctly', () => {
  assert.equal(extractSafeRedirect('?redirect=timer'), 'timer')
  assert.equal(extractSafeRedirect('#login?redirect=timer'), 'timer')
  assert.equal(extractSafeRedirect('#register?redirect=daily'), 'daily')
  assert.equal(extractSafeRedirect('?redirect=https://evil.com'), 'goals')
  assert.equal(extractSafeRedirect('?redirect=//google.com'), 'goals')
  assert.equal(extractSafeRedirect('#auth?redirect=javascript:void(0)'), 'goals')
  assert.equal(extractSafeRedirect(''), 'goals')
  assert.equal(extractSafeRedirect(null), 'goals')
})

