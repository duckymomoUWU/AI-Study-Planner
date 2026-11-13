export function formatStudyTime(seconds) {
  const minutes = Math.floor(seconds / 60), rest = seconds % 60
  return rest ? `${minutes ? `${minutes} phút ` : ''}${rest} giây` : `${minutes} phút`
}

export async function getTaskStudyTimes(taskIds, { signal } = {}) {
  const ids = [...new Set(taskIds)].sort(), totals = {}
  for (let i = 0; i < ids.length; i += 100) {
    const batch = ids.slice(i, i + 100)
    const r = await fetch(`/api/v1/study-sessions/task-totals?taskIds=${encodeURIComponent(batch.join(','))}`, { credentials: 'same-origin', headers: { Accept: 'application/json' }, signal })
    const body = await r.json().catch(() => null)
    if (!r.ok) throw Object.assign(new Error('Chưa tải được thời gian đã học.'), { status: r.status })
    if (!Array.isArray(body?.data) || body.data.length !== batch.length) throw new Error('Dữ liệu thời gian không hợp lệ.')
    for (const row of body.data) {
      if (!batch.includes(row.taskId) || Object.hasOwn(totals, row.taskId) || !Number.isSafeInteger(row.totalFocusSeconds) || row.totalFocusSeconds < 0) throw new Error('Dữ liệu thời gian không hợp lệ.')
      totals[row.taskId] = row
    }
  }
  return totals
}
