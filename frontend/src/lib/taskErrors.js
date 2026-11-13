import { TaskApiError } from './tasks.js'

/** Message for a failed task action, plus whether the list should be reloaded to match the server. */
export function describeTaskError(error) {
  if (!(error instanceof TaskApiError)) {
    return { message: error?.message || 'Đã xảy ra lỗi. Vui lòng thử lại.', reload: false, unauthenticated: false }
  }
  if (error.status === 401) {
    return { message: 'Bạn cần đăng nhập để cập nhật công việc.', reload: false, unauthenticated: true }
  }
  if (error.status === 403 && error.code === 'CSRF_INVALID') {
    return { message: 'Phiên làm việc đã hết hạn. Hãy tải lại trang rồi thử lại.', reload: false, unauthenticated: false }
  }
  if (error.status === 405) {
    return {
      message: 'Chức năng này chưa khả dụng trên máy chủ (đang phát triển).',
      reload: false,
      unauthenticated: false,
    }
  }
  if (error.status === 404) {
    return {
      message: 'Không tìm thấy công việc. Công việc có thể đã bị xóa; danh sách đã được tải lại.',
      reload: true,
      unauthenticated: false,
    }
  }
  return { message: error.message || 'Đã xảy ra lỗi. Vui lòng thử lại.', reload: false, unauthenticated: false }
}
