import { useEffect, useState } from 'react'
import { getBackendHealth } from '../lib/api.js'

export function useBackendHealth() {
  const [attempt, setAttempt] = useState(0)
  const [health, setHealth] = useState({ state: 'checking', message: '' })

  useEffect(() => {
    const controller = new AbortController()
    let active = true
    const timeout = setTimeout(() => controller.abort(), 5000)
    getBackendHealth({ signal: controller.signal })
      .then(() => {
        if (active) setHealth({ state: 'up', message: 'Backend và kiểm tra sức khỏe đang phản hồi UP.' })
      })
      .catch(() => {
        if (active) setHealth({ state: 'down', message: 'Chưa xác nhận được kết nối. Kiểm tra Spring Boot, PostgreSQL và cấu hình proxy.' })
      })
      .finally(() => clearTimeout(timeout))
    return () => {
      active = false
      clearTimeout(timeout)
      controller.abort()
    }
  }, [attempt])

  function retry() {
    setHealth({ state: 'checking', message: '' })
    setAttempt(value => value + 1)
  }
  return { ...health, retry }
}
