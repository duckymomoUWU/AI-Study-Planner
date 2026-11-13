const STORAGE_KEY = 'study_task_recall_links_v1'

let memoryStore = {}

export function _resetMemoryStoreForTesting() {
  memoryStore = {}
}

export function getStorageKey(userId) {
  return userId ? `${STORAGE_KEY}_${userId}` : STORAGE_KEY
}

function getStore(key) {
  if (typeof window !== 'undefined' && window.localStorage) {
    try {
      const raw = window.localStorage.getItem(key)
      return raw ? JSON.parse(raw) : {}
    } catch {
      return {}
    }
  }
  return memoryStore[key] || {}
}

function setStore(key, data) {
  if (typeof window !== 'undefined' && window.localStorage) {
    try {
      window.localStorage.setItem(key, JSON.stringify(data))
    } catch {
      // ignore quota errors
    }
  }
  memoryStore[key] = data
}

export function getAllTaskRecallLinks(userId) {
  return getStore(getStorageKey(userId))
}

export function getTaskRecallLink(taskId, userId) {
  if (!taskId) return null
  const all = getAllTaskRecallLinks(userId)
  return all[taskId] || null
}

export function setTaskRecallLink(taskId, { deckId, deckTitle, cardCount, status, goalId }, userId) {
  if (!taskId || !deckId) return null
  const key = getStorageKey(userId)
  const all = { ...getStore(key) }
  const link = {
    taskId,
    deckId,
    deckTitle: deckTitle || 'Bộ flashcard ôn tập',
    cardCount: Number(cardCount) || 0,
    status: status || 'DRAFT',
    goalId: goalId || null,
    linkedAt: new Date().toISOString(),
  }
  all[taskId] = link
  setStore(key, all)

  if (typeof window !== 'undefined' && typeof window.dispatchEvent === 'function') {
    try {
      window.dispatchEvent(new CustomEvent('task-recall-link-changed', {
        detail: { taskId, link, action: 'set' },
      }))
    } catch {
      // ignore
    }
  }
  return link
}

export function removeTaskRecallLink(taskId, userId) {
  if (!taskId) return
  const key = getStorageKey(userId)
  const all = { ...getStore(key) }
  if (!all[taskId]) return
  delete all[taskId]
  setStore(key, all)

  if (typeof window !== 'undefined' && typeof window.dispatchEvent === 'function') {
    try {
      window.dispatchEvent(new CustomEvent('task-recall-link-changed', {
        detail: { taskId, link: null, action: 'remove' },
      }))
    } catch {
      // ignore
    }
  }
}

/**
 * Validates a task's link against current task properties and available decks.
 * If invalid (e.g. goal changed or deck deleted), the link is removed.
 * Returns valid link or null.
 */
export function validateAndSyncTaskRecallLink(taskId, currentGoalId, availableDecks = null, userId) {
  const link = getTaskRecallLink(taskId, userId)
  if (!link) return null

  // If goal changed, the deck from old goal is no longer suitable
  if (currentGoalId && link.goalId && link.goalId !== currentGoalId) {
    removeTaskRecallLink(taskId, userId)
    return null
  }

  // If available decks list is provided, verify existence
  if (Array.isArray(availableDecks)) {
    const matched = availableDecks.find(d => d.id === link.deckId)
    if (!matched) {
      removeTaskRecallLink(taskId, userId)
      return null
    }
    // Update deck details if changed
    if (matched.title !== link.deckTitle || matched.status !== link.status || matched.cardCount !== link.cardCount) {
      return setTaskRecallLink(taskId, {
        deckId: matched.id,
        deckTitle: matched.title,
        cardCount: matched.cardCount,
        status: matched.status,
        goalId: matched.goalId || currentGoalId,
      }, userId)
    }
  }

  return link
}

/**
 * Listen for changes to recall links (from other components or tabs)
 */
export function listenTaskRecallLinks(callback) {
  if (typeof window === 'undefined') return () => {}

  const handleCustom = event => {
    callback(event?.detail)
  }

  const handleStorage = event => {
    if (event.key && event.key.startsWith(STORAGE_KEY)) {
      callback({ action: 'storage-sync' })
    }
  }

  window.addEventListener('task-recall-link-changed', handleCustom)
  window.addEventListener('storage', handleStorage)

  return () => {
    window.removeEventListener('task-recall-link-changed', handleCustom)
    window.removeEventListener('storage', handleStorage)
  }
}

