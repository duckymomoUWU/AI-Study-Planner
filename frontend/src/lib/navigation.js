export function pageFromHash(hash = '') {
  const route = hash.toLowerCase().split('?')[0]
  if (['#auth', '#login', '#register'].includes(route)) return 'auth'
  if (route === '#goals') return 'goals'
  if (['#daily', '#tasks'].includes(route)) return 'daily'
  if (['#weekly', '#progress'].includes(route)) return 'weekly'
  if (route === '#recall') return 'recall'
  if (route === '#timer') return 'timer'
  if (['#landing', '#landing-main', '#landing-steps', '#landing-features', '#landing-faq'].includes(route)) return 'landing'
  if (route === '#overview') return 'overview'
  return 'home'
}

export function taskFromHash(hash = '') {
  if (pageFromHash(hash) !== 'timer') return null
  const value = new URLSearchParams(hash.split('?')[1] || '').get('task')
  return /^[\da-f]{8}-[\da-f]{4}-[\da-f]{4}-[\da-f]{4}-[\da-f]{12}$/i.test(value || '') ? value : null
}

export function recallParamsFromHash(hash = '') {
  if (pageFromHash(hash) !== 'recall') return { goalId: null, deckId: null }
  const params = new URLSearchParams(hash.split('?')[1] || '')
  const goal = params.get('goal')
  const deck = params.get('deck')
  const uuidRegex = /^[\da-f]{8}-[\da-f]{4}-[\da-f]{4}-[\da-f]{4}-[\da-f]{12}$/i
  return {
    goalId: uuidRegex.test(goal || '') ? goal : null,
    deckId: uuidRegex.test(deck || '') ? deck : null,
  }
}
