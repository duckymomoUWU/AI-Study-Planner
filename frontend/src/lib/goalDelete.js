/**
 * Delete flow: nothing is sent unless the user confirms.
 * Returns 'cancelled' (no request made) or 'deleted'; errors from `remove` propagate.
 */
export async function confirmAndDeleteGoal(goal, { confirm, remove }) {
  const message = `Bạn có chắc muốn xóa mục tiêu "${goal.subject}"? Các công việc và bản nháp kế hoạch liên quan cũng sẽ bị xóa. Hành động này không thể hoàn tác.`
  if (!confirm(message)) {
    return 'cancelled'
  }
  await remove(goal.id)
  return 'deleted'
}
