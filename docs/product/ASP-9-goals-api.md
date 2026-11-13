# Mục tiêu — API v1

Các route dưới `/api/v1/goals`, dùng session/CSRF/ownership theo
[quy tắc chung](../backend-api-rules.md). Ngày/phút theo [requirements](../../requirements.md).

## API và dữ liệu

| Method | Route | Kết quả |
| --- | --- | --- |
| POST | `/goals` | 201, Goal |
| GET | `/goals?page=1&limit=20` | 200, Goal[] + meta |
| GET | `/goals/{id}` | 200, Goal |
| PATCH | `/goals/{id}` | 200, Goal |
| DELETE | `/goals/{id}` | 204 |

Goal gồm `id,subject,topics,startDate,examDate,minutesPerDay,timezone,version,createdAt`
và `planningScope` khi có cấu hình. Không trả user_id/password. List theo createdAt DESC/id ASC.

Create nhận các trường bắt buộc `subject,topics,startDate,examDate,minutesPerDay`,
optional `timezone,planningScope`. PATCH nhận ít nhất một trường được sửa và
`expectedVersion`; không nhận id/userId/source/createdAt/version do client đặt.

- Subject/topics trim và không rỗng; subject tối đa 100 ký tự Unicode sau trim.
- Ngày thi sau ngày bắt đầu, không trong quá khứ, tối đa 90 ngày sau ngày bắt đầu.
- Tạo mới: ngày bắt đầu không trước hôm nay trong timezone của goal. Sửa goal đang học
  được giữ ngày bắt đầu cũ; không dời qua các việc đã lưu để làm lịch sử sai ngày.
- Timezone là IANA hợp lệ, tối đa 64 ký tự, mặc định Asia/Ho_Chi_Minh.
- minutesPerDay là số nguyên 15–480, mức mong muốn của môn; không phải trần hard của task thủ công.
- planningScope lưu chủ đề/ưu tiên/nguồn và ghi đè ngày cho AI; chi tiết tại
  [scoped planning](syllabus-revision-plan.md) và [preferences](study-time-preferences.md).

## Cập nhật, version và xóa

Goal version bảo vệ cập nhật cũ. Mismatch trả 409 GOAL_VERSION_CONFLICT; mutation hợp lệ
của goal/task tăng version theo hợp đồng. Validation lỗi không thay dữ liệu/version.

Rút ngày thi hoặc đổi start/planning scope làm việc đã lưu không hợp lệ bị từ chối.
Giảm mức mong muốn riêng của môn không xóa/dời task hoặc chặn chỉ vì tổng task vượt mức đó.
Giới hạn cứng thuộc [quỹ chung](shared-study-budget.md).

Xóa goal cascade tasks, plan drafts và dữ liệu Recall liên quan. Timer giữ lịch sử,
nullable task/goal ID và tên snapshot. Calendar đã liên kết enqueue xóa remote theo contract.

## Lỗi và kiểm chứng

400 VALIDATION_ERROR cho required/type/date/timezone/range/unknown field/too_long;
401 thiếu session; 403 CSRF; 404 goal thiếu/ngoại tài khoản; 409 GOAL_VERSION_CONFLICT
hoặc GOAL_UPDATE_CONFLICT khi dữ liệu hiện hữu bị phá vỡ. Field errors ở details;
đầu vào sai được báo 400 trước khi xét conflict dữ liệu hiện hữu.

Code: GoalRequest/GoalPolicy/GoalPlanning/GoalService/GoalJdbcRepository và migrations V1/V5.
Test: GoalControllerTest, GoalApiPostgresIntegrationTest, GoalServicePostgresIntegrationTest,
GoalPolicyTest, GoalPlanningTest và frontend goals/goalErrors/goalDelete tests.
[Bằng chứng ASP-9 lịch sử](../archive/ASP-9-verification.md); kết quả hiện tại ở [status](../status.md).
