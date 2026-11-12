# Xem tiến độ của mục tiêu (ASP-67 → ASP-70, ASP-113)

**Trạng thái (2026-11-07):** API và giao diện đã hiện thực trên nhánh `dev4-sprint3`, kiểm bằng test MockMvc, PostgreSQL thật và test client frontend (mục 6). Chưa có trên `main`. Quy tắc chung: [Backend API rules](../backend-api-rules.md). Yêu cầu gốc (requirements.md): "Tiến độ = số công việc hoàn thành / tổng số công việc; không đánh giá mức độ hiểu bài. Chưa có việc thì hiển thị 0/0."

## 1. Phạm vi

| Có trong story này | Chưa có (story khác) |
| --- | --- |
| Tiến độ của **một mục tiêu**: số đã hoàn thành, còn lại, tổng, tỷ lệ | Tiến độ gộp mọi mục tiêu của người dùng (không có trong đặc tả) |
| Hiển thị trong danh sách mục tiêu và trong khung chi tiết | Đánh dấu hoàn thành/mở lại công việc (ASP-63 → ASP-65, nhánh `dev2-sprint3`) |
| 0/0 khi chưa có việc | Tạo, sửa, xóa công việc (ASP-10) |

Tiến độ đọc từ cột `study_tasks.completed`. Trên nhánh này chưa có API đổi trạng thái, nên test và demo đặt `completed` bằng SQL; khi nhánh đánh dấu hoàn thành được gộp, hai tính năng khớp nhau mà không cần sửa thêm.

## 2. API

`GET /api/v1/goals/{goalId}/progress` — chỉ đọc, cần session (401 `UNAUTHORIZED`). Mục tiêu không tồn tại, của người khác hoặc ID sai định dạng đều trả cùng một 404 `NOT_FOUND`. Phương thức khác (ví dụ `POST`) trả 405 `METHOD_NOT_ALLOWED`.

```json
{ "data": { "goalId": "…", "totalTasks": 3, "completedTasks": 1, "remainingTasks": 2,
            "completionRate": 0.3333333333333333 } }
```

- `totalTasks` là số công việc đã lưu của mục tiêu, tính trên **toàn bộ** tập dữ liệu, không phải trang hiện tại.
- `completedTasks` là số công việc có `completed = true`; `remainingTasks = totalTasks - completedTasks`.
- `completionRate = completedTasks / totalTasks`, từ 0 đến 1; **bằng 0 khi chưa có công việc** (không chia cho 0).
- Bản nháp AI (`plan_drafts`) không phải công việc nên không bao giờ được đếm.
- Hai con số lấy bằng một truy vấn duy nhất có lọc theo chủ sở hữu, nên cùng một thời điểm. Đọc không làm tăng `version` của mục tiêu.
- Không có phần nào suy ra điểm thi hay mức độ hiểu bài.

## 3. Giao diện

- Thành phần `GoalProgress` (`frontend/src/components/GoalProgress.jsx`) trong mỗi mục tiêu của danh sách và trong khung chi tiết; thanh tiến độ theo shadcn/ui (`components/ui/progress.jsx`, `role="progressbar"` có `aria-valuenow`).
- Hiển thị "x/y việc", phần trăm làm tròn trên thanh, "n hoàn thành" và "m còn lại". Chưa có việc: "0/0 việc" và dòng "Chưa có công việc."
- Trạng thái "Đang tải tiến độ…" và thông báo lỗi riêng cho từng mục tiêu (401, 404, lỗi khác); lỗi của một mục tiêu không làm hỏng danh sách.
- Tự tải lại khi `version` của mục tiêu đổi (mọi thay đổi công việc làm tăng version, nên tiến độ không bị cũ sau khi danh sách tải lại).
- Mỗi mục tiêu gọi một yêu cầu riêng; với danh sách rất dài sẽ là nhiều yêu cầu nhỏ (chưa có API gộp).

## 4. Điểm cần PO xác nhận

1. Phần trăm hiển thị làm tròn đến số nguyên gần nhất (ví dụ 1/3 là 33%); API trả tỷ lệ chính xác.
2. Chưa có tiến độ tổng của tất cả mục tiêu; đặc tả hiện chỉ có tiến độ theo mục tiêu.

## 5. Ánh xạ tiêu chí → mã → bằng chứng

| Tiêu chí | Hiện thực | Bằng chứng |
| --- | --- | --- |
| Tiến độ = hoàn thành / tổng | `ProgressService`, `ProgressJdbcRepository` | `ProgressServiceTest`, `ProgressApiPostgresIntegrationTest.countsTheWholeGoalAndTracksChanges` |
| Chưa có việc hiển thị 0/0, không chia cho 0 | `completionRate = 0` khi tổng bằng 0; `describeProgress` | `emptyGoalIsZeroOfZero`, `ProgressServiceTest.noTasksIsZeroOfZero…`, `progress.test.js` |
| Tính trên toàn bộ công việc, không theo trang | Một truy vấn `count` | Test 25 công việc (hơn một trang 20) |
| Không đếm bản nháp, chỉ đếm công việc của mục tiêu | `LEFT JOIN study_tasks` theo `goal_id` | `countsOnlyThisGoalsTasksNotDrafts`, `ProgressJdbcRepositoryTest.neverTouchesPlanDrafts` |
| Chỉ chủ sở hữu | Lọc `g.user_id` trong cùng truy vấn | `ownershipAndAuthentication`, `ProgressControllerTest` |
| Bám theo thay đổi (hoàn thành, mở lại, xóa việc, xóa mục tiêu) | Đọc trực tiếp từ DB mỗi lần | Cùng các test PostgreSQL, `deletedGoalHasNoProgress` |
| Không đánh giá mức độ hiểu bài | Chỉ có số đếm | Mục 2 và giao diện chỉ hiện số đếm |

## 6. Kết quả kiểm chứng

Xem mục "Kiểm chứng ASP-67 → ASP-70" ở cuối [ASP-9 verification](ASP-9-verification.md).
