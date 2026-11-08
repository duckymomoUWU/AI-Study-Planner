# Xem tiến độ theo tuần (US-17 / ASP-33: ASP-99 → ASP-102, ASP-112)

**Trạng thái (2026-11-08):** API và giao diện đã hiện thực trên nhánh `sprint4/weekly-progress` (tách từ nhánh sprint 3 của dev1), được kiểm bằng test MockMvc, PostgreSQL thật và test client frontend (mục 7). Chưa có trên `main`.

User story: "Là sinh viên, tôi muốn xem số việc đã xong theo từng tuần để đánh giá nhịp học." Yêu cầu gốc (requirements.md): "Tiến độ = số công việc hoàn thành / tổng số công việc; không đánh giá mức độ hiểu bài. Chưa có việc thì hiển thị 0/0."

## 1. Tiêu chí chấp nhận (nháp, chờ PO duyệt) và cách đáp ứng

| AC | Cách đáp ứng |
| --- | --- |
| AC1: Hiển thị số việc hoàn thành và tổng số việc của từng tuần | Trang "Tiến độ tuần": "x/y việc", thanh tiến độ, "n hoàn thành · m còn lại", kèm phần trăm và bảng 7 ngày |
| AC2: Chọn được tuần trước hoặc tuần sau | Nút "← Tuần trước", "Tuần này", "Tuần sau →"; mỗi lần chọn gọi lại API với tuần mới |
| AC3: Tuần không có việc hiện 0/0, không báo lỗi | API trả `0/0`, tỷ lệ 0 và 7 ngày bằng 0; giao diện hiện "0/0 việc" và "Chưa có công việc nào trong tuần này." |

## 2. Quy ước về tuần

- Tuần chạy từ **thứ Hai đến Chủ Nhật** (ngày thứ Hai là `weekStart`, Chủ Nhật là `weekEnd`, cả hai đều tính). Chủ Nhật thuộc tuần kết thúc vào chính ngày đó; thứ Hai kế tiếp là tuần mới.
- Một công việc thuộc tuần chứa `scheduled_date` của nó. Ngày là **ngày lịch địa phương** của mục tiêu, không đổi sang UTC.
- "Tuần hiện tại" lấy theo ngày hôm nay của máy chủ (múi giờ mặc định `Asia/Ho_Chi_Minh`, cấu hình `app.default-timezone`).
- Quy ước thứ Hai là đầu tuần là lựa chọn của nhóm kỹ thuật, requirements chưa nêu; cần PO xác nhận (mục 6).

## 3. API

`GET /api/v1/progress/weekly?date=YYYY-MM-DD` — chỉ đọc, cần đăng nhập. `date` là một ngày bất kỳ của tuần cần xem; bỏ trống là tuần hiện tại.

```json
{ "data": {
    "weekStart": "2026-11-02", "weekEnd": "2026-11-08",
    "totalTasks": 7, "completedTasks": 3, "remainingTasks": 4, "completionRate": 0.42857142857142855,
    "days": [ { "date": "2026-11-02", "totalTasks": 2, "completedTasks": 1 }, … 7 phần tử, thứ Hai đến Chủ Nhật … ]
} }
```

- Tính trên **mọi mục tiêu** của người dùng; công việc của người khác không bao giờ được đếm (truy vấn nối `study_goals` và lọc `user_id`).
- `completionRate` = `completedTasks / totalTasks`, từ 0 đến 1; **bằng 0 khi tuần không có việc** (không chia cho 0). `days` luôn đủ 7 ngày, ngày không có việc là 0.
- Bản nháp AI (`plan_drafts`) không phải công việc nên không được đếm. Đọc không ghi gì và không đổi `version` mục tiêu.
- Lỗi theo khuôn chung `{ "error": { "code", "message", "details": [ { "field", "issue", "message" } ] } }`:

| HTTP | `code` | Khi nào |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR` | `date` sai định dạng (`details[0].issue = invalid_date`) hoặc ngoài khoảng năm 2000 đến 2100 (`out_of_range`) |
| 401 | `UNAUTHORIZED` | Chưa đăng nhập |
| 500 | `INTERNAL_SERVER_ERROR` | Lỗi bất ngờ; không lộ chi tiết, log chỉ ghi tên lớp lỗi |

## 4. Giao diện

- Trang `WeeklyProgressPage` (`#weekly` hoặc `#progress`), mục "Tiến độ tuần" trong thanh bên; thành phần Card và Progress theo shadcn/ui (`components/ui/progress.jsx` có `role="progressbar"` và `aria-valuenow`).
- Hiển thị khoảng ngày của tuần ("02/11 – 08/11/2026"), nhãn "Tuần hiện tại" khi đang xem tuần này, thẻ tổng kết, và danh sách 7 ngày (T2 đến CN) mỗi ngày một thanh nhỏ với "x/y".
- Trạng thái: đang tải; lỗi có nút "Thử lại"; chưa đăng nhập có nút "Đăng nhập ngay"; tuần trống là trạng thái bình thường.
- Chỉ hiển thị số đếm và phần trăm, kèm dòng ghi chú rằng tiến độ không đánh giá mức độ hiểu bài.
- Phép tính tuần ở frontend dùng ngày lịch (UTC midnight) nên không lệch khi đổi giờ.

## 5. Phạm vi và phụ thuộc

| Có trong story này | Chưa có (story khác) |
| --- | --- |
| Tiến độ theo tuần của toàn bộ công việc của người dùng | Tiến độ theo từng mục tiêu, tiến độ tổng (US-07 / nhánh khác) |
| Chọn tuần trước, sau, hiện tại | Lọc theo môn học, thống kê dài hạn (US-18) |

Tiến độ đọc cột `study_tasks.completed`. Trên nhánh `sprint4/weekly-progress` chưa có API đánh dấu hoàn thành, nên test và demo ở đó đặt `completed` bằng SQL. Sau khi gộp vào `dev2` (2026-11-08), API `PATCH /api/v1/goals/{goalId}/tasks/{taskId}` và trang "Việc cần làm" (`#checklist`) có sẵn: đánh dấu xong ở đó thì tiến độ tuần đổi ngay mà không cần sửa gì (đã kiểm, xem [weekly-progress-verification.md](weekly-progress-verification.md) mục 6). Công việc vẫn chỉ xuất hiện nhờ xác nhận bản nháp AI (`confirm`) hoặc SQL, vì API tạo công việc thủ công chưa có.

## 6. Điểm cần PO xác nhận

1. Thứ Hai là ngày đầu tuần (cách tính theo lịch Việt Nam).
2. Công việc của mọi mục tiêu được gộp chung trong một tuần (ngân sách vẫn tính riêng từng mục tiêu theo requirements).
3. Phần trăm hiển thị làm tròn số nguyên (1/3 là 33%); API trả tỷ lệ chính xác.

## 7. Ánh xạ tiêu chí → mã → bằng chứng

| Tiêu chí | Hiện thực | Bằng chứng |
| --- | --- | --- |
| Số hoàn thành và tổng của tuần | `WeeklyProgressService`, `WeeklyProgressJdbcRepository` | `WeeklyProgressApiPostgresIntegrationTest.countsTheWeekAcrossGoalsWithInclusiveBoundaries`, `WeeklyProgressServiceTest` |
| Thứ Hai và Chủ Nhật tính trong tuần, các ngày kề không tính | `BETWEEN :from AND :to` | Cùng test PostgreSQL; `mondayStartsANewWeekAndSundayEndsTheOldOne` |
| Chọn tuần trước, sau | `date` ±7 ngày ở client, API độc lập theo tuần | Test PostgreSQL (tuần trước, tuần sau), `weekly-progress.test.js` (`shiftWeek`) |
| Tuần không có việc là 0/0, không lỗi | Rate 0, 7 ngày bằng 0 | `emptyWeekIsZeroOfZero`, `aWeekWithoutTasksIsZeroOfZeroWithRateZeroNotNaN`, `weekly-progress.test.js` |
| Một ngày bất kỳ cho cùng một tuần; mặc định là tuần hiện tại | `previousOrSame(MONDAY)`, `Clock` | `anyDayOfTheWeekAndTheDefaultWeek`, `anyDateOfTheWeekGivesTheSameMondayToSundayRange` |
| Ranh giới năm, năm nhuận | Lịch Java | `yearBoundaryWeek`, `weeksAcrossAYearOrLeapDayBoundaryAreCalendarWeeks` |
| Chỉ đếm công việc của chính người dùng, không đếm bản nháp | Lọc `user_id`, không đụng `plan_drafts` | `ownershipAndAuthentication`, `draftsAreIgnoredAndReadsAreSideEffectFree`, `WeeklyProgressJdbcRepositoryTest` |
| Ngày sai bị từ chối | Phân tích và kiểm tra khoảng | `invalidAndOutOfRangeDates`, `WeeklyProgressControllerTest` |
| Không đánh giá mức độ hiểu bài | Chỉ có số đếm | Mục 3 và giao diện |

## 8. Kết quả kiểm chứng

Xem [weekly-progress-verification.md](weekly-progress-verification.md).
