import { GoalApiError } from './goals.js'

/**
 * Turns a goal API failure into what the UI needs:
 * - message: text for the page-level alert
 * - fieldErrors: { field: [messages] } so every message for a field can be shown
 * - conflicts: messages for tasks that block an edit (HTTP 409 GOAL_UPDATE_CONFLICT)
 */
export function describeGoalError(error) {
  if (!(error instanceof GoalApiError)) {
    return { message: error?.message || 'Đã xảy ra lỗi. Vui lòng thử lại.', fieldErrors: {}, conflicts: [] }
  }

  const fieldErrors = {}
  for (const item of error.fieldErrors) {
    if (!item?.field || !item?.message) continue
    ;(fieldErrors[item.field] ||= []).push(item.message)
  }

  if (error.status === 401) {
    return { message: 'Bạn cần đăng nhập để quản lý mục tiêu cá nhân.', fieldErrors: {}, conflicts: [] }
  }
  if (error.status === 403 && error.code === 'CSRF_INVALID') {
    return { message: 'Phiên làm việc đã hết hạn. Hãy tải lại trang rồi thử lại.', fieldErrors: {}, conflicts: [] }
  }
  if (error.status === 404) {
    return {
      message: 'Không tìm thấy mục tiêu. Mục tiêu có thể đã bị xóa hoặc không thuộc tài khoản của bạn.',
      fieldErrors: {},
      conflicts: [],
    }
  }
  if (error.status === 409 && error.code === 'GOAL_UPDATE_CONFLICT') {
    return {
      message: error.message || 'Thay đổi làm công việc hiện có vi phạm ngày thi hoặc ngân sách thời gian.',
      fieldErrors,
      conflicts: error.fieldErrors.map(item => item?.message).filter(Boolean),
    }
  }
  if (error.status === 409) {
    return {
      message: 'Mục tiêu vừa được cập nhật ở nơi khác. Hãy tải lại danh sách trước khi sửa tiếp.',
      fieldErrors,
      conflicts: [],
    }
  }
  if (error.status === 400) {
    return {
      message: error.message || 'Dữ liệu mục tiêu không hợp lệ.',
      fieldErrors,
      conflicts: [],
    }
  }
  return {
    message: error.message || 'Đã xảy ra lỗi. Vui lòng thử lại.',
    fieldErrors,
    conflicts: [],
  }
}
