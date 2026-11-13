import { useEffect, useState } from 'react'
import { getTaskStudyTimes } from '../lib/taskStudyTime.js'

export default function useTaskStudyTimes(tasks, revision = '') {
  const idsKey = [...new Set(tasks.map(task => task.id))].sort().join(',')
  const key = `${idsKey}:${revision}`
  const [reload, setReload] = useState(0)
  const [state, setState] = useState({ key: '', totals: {}, error: false })
  useEffect(() => {
    const focus = () => setReload(value => value + 1)
    window.addEventListener('focus', focus)
    return () => window.removeEventListener('focus', focus)
  }, [])
  useEffect(() => {
    const controller = new AbortController()
    getTaskStudyTimes(idsKey ? idsKey.split(',') : [], { signal: controller.signal })
      .then(totals => { if (!controller.signal.aborted) setState({ key, totals, error: false }) })
      .catch(() => { if (!controller.signal.aborted) setState({ key, totals: {}, error: true }) })
    return () => controller.abort()
  }, [idsKey, key, reload])
  return state.key === key ? state : { totals: {}, error: false }
}
