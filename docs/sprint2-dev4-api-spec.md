# Đặc tả API cho Sprint 2 — US-01 (Mục tiêu) & US-03 (Công việc theo ngày)

> **Tài liệu hướng dẫn dành riêng cho Developer phụ trách branch `dev4-sprint2`**  
> Tham chiếu Jira: **ASP-9 (US-01)**, **ASP-11 (US-03)**, **ASP-25**, **ASP-26**  
> Cơ sở xác thực: Dựa trên module bảo mật session cookie và CSRF của Dev 1 (ASP-12).

---

## I. TỔNG QUAN USER STORIES TRONG SPRINT 2

### 1. US-01 / ASP-9 — Quản lý mục tiêu ôn tập (Goal Management)
* **Mô tả:** *"Là sinh viên, tôi muốn tạo, xem, sửa, xóa mục tiêu ôn tập để lưu nội dung cần ôn."*
* **Tiêu chí chấp nhận (Acceptance Criteria):**
  1. **Các trường bắt buộc:** Môn học (`subject`), Chủ đề (`topics`), Ngày bắt đầu (`startDate`), Ngày thi (`examDate`), Thời lượng học mỗi ngày (`minutesPerDay` từ 15 đến 480 phút).
  2. **Ràng buộc thời gian:** `examDate > startDate`; khoảng cách `examDate - startDate <= 90` ngày; `examDate` không ở quá khứ so với thời gian hiện tại (`Asia/Ho_Chi_Minh`).
  3. **Bảo mật & Quyền sở hữu:** Chỉ truy cập mục tiêu của chính tài khoản đăng nhập (lấy `userId` từ server session, cấm nhận từ client). Nếu ID không tồn tại hoặc thuộc người khác, trả về **404** để tránh rò rỉ thông tin.
  4. **Cập nhật có kiểm soát phiên bản (Optimistic Locking):** Khi `PUT /api/goals/{id}`, client phải gửi `version`. Trả về **409** (`stale_goal_version`) nếu version lệch. Không được thay đổi ngày thi hoặc giảm ngân sách dẫn đến xung đột với các task đã lên lịch (`goal_tasks_conflict`).
  5. **Xóa có xác nhận & Cascade:** Xác nhận trên UI trước khi gọi `DELETE`. Xóa mục tiêu sẽ cascade xóa toàn bộ công việc (`study_tasks`) và bản nháp kế hoạch (`plan_drafts`).

---

### 2. US-03 / ASP-11 — Xem công việc theo ngày (Daily Tasks View)
* **Mô tả:** *"Là sinh viên, tôi muốn xem công việc theo ngày để biết hôm nay (hoặc ngày được chọn) cần làm gì."*
* **Tiêu chí chấp nhận (Acceptance Criteria):**
  1. **Xem theo ngày linh hoạt:** Cho phép người dùng chọn một ngày cụ thể trên lịch/date picker; mặc định hiển thị ngày hôm nay.
  2. **Hiển thị chi tiết công việc:** Liệt kê các task có `scheduled_date` khớp với ngày chọn, gồm: tiêu đề (`title`), môn học (`goalSubject`), thời lượng (`estimatedMinutes`), trạng thái hoàn thành (`completed: true/false`), nguồn gốc (`source: 'manual' | 'ai'`).
  3. **Thống kê tổng lượng trong ngày:**
     - Tổng thời gian học dự kiến trong ngày (`sum(estimatedMinutes)`).
     - So sánh với ngân sách thời gian của môn học (`minutesPerDay`).
     - Tỷ lệ hoàn thành công việc trong ngày (ví dụ: `2/5 việc xong`).
  4. **Tương tác trực tiếp:** Cho phép người dùng click đánh dấu hoàn thành/chưa hoàn thành ngay trên giao diện danh sách ngày.
  5. **Xử lý công việc trễ hạn:** Nếu ngày trong quá khứ có việc chưa hoàn thành (`scheduledDate < today` và `completed == false`), đánh dấu trực quan là "Quá hạn / Trễ".

---

## II. DANH SÁCH API CẦN THIẾT CHO BRANCH `dev4-sprint2`

### Nhóm 1: API Quản lý mục tiêu (US-01 / ASP-9)

#### 1.1. Lấy danh sách mục tiêu
* **Method & Path:** `GET /api/goals`
* **Xác thực:** Bắt buộc đăng nhập (Session cookie `JSESSIONID`).
* **Response `200 OK`:**
```json
{
  "items": [
    {
      "id": "3f3ac7a4-13a9-4763-b271-0e1c9f0e641d",
      "subject": "Giải tích 2",
      "topics": "Tích phân; phương trình vi phân",
      "startDate": "2026-11-09",
      "examDate": "2026-12-05",
      "minutesPerDay": 90,
      "timezone": "Asia/Ho_Chi_Minh",
      "version": 0,
      "createdAt": "2026-11-07T08:00:00Z"
    }
  ]
}
```
* **Lỗi:** `401` (`unauthenticated`) nếu chưa đăng nhập.

---

#### 1.2. Tạo mục tiêu mới
* **Method & Path:** `POST /api/goals`
* **Headers:** `Content-Type: application/json`, `X-CSRF-TOKEN: <csrf_token>`
* **Request Body:**
```json
{
  "subject": "Giải tích 2",
  "topics": "Tích phân; phương trình vi phân",
  "startDate": "2026-11-09",
  "examDate": "2026-12-05",
  "minutesPerDay": 90
}
```
* **Response `201 Created`:** Trả về đối tượng Goal vừa tạo (kèm `id` và `version: 0`).
* **Lỗi:**
  * `400` (`validation_failed`): Trường rỗng, ngày thi $\le$ ngày bắt đầu, khoảng cách $> 90$ ngày, hoặc `minutesPerDay` ngoài khoảng $[15, 480]$.
  * `401` (`unauthenticated`): Chưa đăng nhập.
  * `403` (`forbidden_or_csrf`): Thiếu hoặc sai CSRF token.

---

#### 1.3. Lấy chi tiết một mục tiêu
* **Method & Path:** `GET /api/goals/{goalId}`
* **Response `200 OK`:** Trả về đối tượng Goal.
* **Lỗi:** `404` (`goal_not_found`) nếu ID không tồn tại hoặc thuộc user khác; `401` nếu chưa đăng nhập.

---

#### 1.4. Cập nhật mục tiêu
* **Method & Path:** `PUT /api/goals/{goalId}`
* **Headers:** `Content-Type: application/json`, `X-CSRF-TOKEN: <csrf_token>`
* **Request Body:**
```json
{
  "subject": "Giải tích 2 - Nâng cao",
  "topics": "Tích phân mặt; chuỗi Fourier",
  "startDate": "2026-11-09",
  "examDate": "2026-12-10",
  "minutesPerDay": 120,
  "version": 0
}
```
* **Response `200 OK`:** Trả về đối tượng Goal cập nhật (version tăng lên `1`).
* **Lỗi:**
  * `400` (`validation_failed`): Dữ liệu không hợp lệ.
  * `404` (`goal_not_found`): Không tìm thấy mục tiêu.
  * `409` (`stale_goal_version`): Version client gửi không khớp version hiện tại trên database.
  * `409` (`goal_tasks_conflict`): Ngày thi mới làm task cũ rơi vào/sau ngày thi, hoặc phút/ngày mới nhỏ hơn tổng phút task đã xếp trong 1 ngày nào đó.

---

#### 1.5. Xóa mục tiêu
* **Method & Path:** `DELETE /api/goals/{goalId}`
* **Headers:** `X-CSRF-TOKEN: <csrf_token>`
* **Response `204 No Content`**
* **Lỗi:** `404` (`goal_not_found`), `401` (`unauthenticated`), `403` (`forbidden_or_csrf`).

---

### Nhóm 2: API Xem và quản lý công việc theo ngày (US-03 / ASP-11)

#### 2.1. Lấy danh sách công việc theo ngày (Trọng tâm US-03)
* **Method & Path:** `GET /api/tasks?date={YYYY-MM-DD}`
* **Ví dụ:** `GET /api/tasks?date=2026-11-09` *(nếu không truyền `date`, backend mặc định lấy ngày hôm nay theo múi giờ `Asia/Ho_Chi_Minh`)*.
* **Query Params (tùy chọn):**
  * `date`: Chuỗi ngày `YYYY-MM-DD`.
  * `goalId`: Lọc riêng theo một mục tiêu nếu cần.
* **Response `200 OK`:**
```json
{
  "date": "2026-11-09",
  "totalEstimatedMinutes": 150,
  "completedCount": 1,
  "totalCount": 2,
  "items": [
    {
      "id": "e4f5a6b7-8901-2345-6789-0123456789ab",
      "goalId": "3f3ac7a4-13a9-4763-b271-0e1c9f0e641d",
      "goalSubject": "Giải tích 2",
      "title": "Ôn lại lý thuyết Tích phân đường",
      "scheduledDate": "2026-11-09",
      "estimatedMinutes": 60,
      "completed": true,
      "source": "manual",
      "createdAt": "2026-11-07T08:30:00Z"
    },
    {
      "id": "f5a6b7c8-9012-3456-7890-123456789abc",
      "goalId": "3f3ac7a4-13a9-4763-b271-0e1c9f0e641d",
      "goalSubject": "Giải tích 2",
      "title": "Làm bài tập tích phân mặt",
      "scheduledDate": "2026-11-09",
      "estimatedMinutes": 90,
      "completed": false,
      "source": "ai",
      "createdAt": "2026-11-07T08:30:00Z"
    }
  ]
}
```
* **Lỗi:** `400` (`invalid_date_format`), `401` (`unauthenticated`).

---

#### 2.2. Đánh dấu trạng thái hoàn thành công việc
* **Method & Path:** `PATCH /api/tasks/{taskId}/complete`
* **Headers:** `Content-Type: application/json`, `X-CSRF-TOKEN: <csrf_token>`
* **Request Body:**
```json
{
  "completed": true
}
```
* **Response `200 OK`:**
```json
{
  "id": "f5a6b7c8-9012-3456-7890-123456789abc",
  "completed": true,
  "updatedAt": "2026-11-09T10:00:00Z"
}
```
* **Lỗi:** `404` (`task_not_found`), `401` (`unauthenticated`), `403` (`forbidden_or_csrf`).

---

#### 2.3. Tạo công việc thủ công (Hỗ trợ thêm việc trực tiếp vào ngày xem)
* **Method & Path:** `POST /api/goals/{goalId}/tasks`
* **Headers:** `Content-Type: application/json`, `X-CSRF-TOKEN: <csrf_token>`
* **Request Body:**
```json
{
  "title": "Làm 5 bài tập ôn thi",
  "scheduledDate": "2026-11-09",
  "estimatedMinutes": 45
}
```
* **Response `201 Created`:** Trả về đối tượng Task vừa tạo (`source: "manual"`).
* **Lỗi:**
  * `400` (`task_date_out_of_range`): Ngày xếp việc nằm ngoài khoảng $[startDate, examDate)$.
  * `400` (`daily_budget_exceeded`): Tổng phút các task trong ngày đó vượt quá `minutesPerDay` của Goal.
  * `404` (`goal_not_found`): Goal không tồn tại hoặc không thuộc user.

---

#### 2.4. Lấy danh sách công việc bị trễ / quá hạn (Hỗ trợ theo dõi tiến độ)
* **Method & Path:** `GET /api/tasks/overdue`
* **Response `200 OK`:**
```json
{
  "items": [
    {
      "id": "c1d2e3f4-5678-9012-3456-789012345678",
      "goalId": "3f3ac7a4-13a9-4763-b271-0e1c9f0e641d",
      "goalSubject": "Giải tích 2",
      "title": "Đọc tài liệu chương 2",
      "scheduledDate": "2026-11-06",
      "estimatedMinutes": 60,
      "completed": false,
      "source": "manual"
    }
  ]
}
```

---

## III. QUY TẮC BẮT BUỘC KHI CODE CHO DEV TRÊN BRANCH `dev4-sprint2`

1. **Xác thực và CSRF Token (Quy chuẩn Dev 1):**
   * Tất cả các request HTTP từ frontend phải có cấu hình `credentials: 'same-origin'`.
   * Mọi request thay đổi dữ liệu (`POST`, `PUT`, `PATCH`, `DELETE`) **bắt buộc phải kèm header**:
     ```javascript
     import { getCsrfToken } from '../lib/auth.js'

     const csrfToken = await getCsrfToken()
     headers['X-CSRF-TOKEN'] = csrfToken
     ```
   * Nếu không gửi CSRF token, Spring Security sẽ trả về ngay lập tức lỗi `403 Forbidden` (`forbidden_or_csrf`).

2. **Xử lý phiên đăng nhập và mã lỗi 401:**
   * Khi gọi bất kỳ API nào nhận về HTTP status `401`, giao diện frontend phải tự động chuyển người dùng về tab `#login` hoặc hiển thị thông báo yêu cầu đăng nhập.

3. **Cấu trúc dữ liệu Database có sẵn:**
   * Không sửa đổi cấu trúc migration SQL ban đầu (`V1__initial_schema.sql`). Bảng `study_goals` và `study_tasks` đã được định nghĩa đầy đủ với ràng buộc khóa ngoại `ON DELETE CASCADE`.
   * Tầng backend đã có sẵn `GoalService.java` và `GoalJdbcRepository.java` (chứa toàn bộ logic kiểm tra 90 ngày, timezone, optimistic locking), Developer chỉ cần viết thêm `GoalController.java` và `TaskController.java` để map HTTP sang các service này.

4. **Kiểm thử bắt buộc trước khi bàn giao:**
   * Chạy `npm test` ở frontend (viết unit test cho `tasks.js` và `goals.js`).
   * Chạy `mvn test` ở backend để bảo đảm các bài test hồi quy không bị gãy.
