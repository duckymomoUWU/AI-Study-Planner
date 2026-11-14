# ASP-57: kiểm thử duyệt, chỉnh sửa và xác nhận kế hoạch AI

Ngày chạy: 2026-11-14. Lần chạy đầu: commit `67b74f5`. Sau sửa lỗi: nhánh `fix/plan-draft-review` (từ `dev2`). Phụ thuộc: ASP-14.

## 1. Môi trường và cách phân biệt nguồn kết quả

| Nguồn | Là gì | Dùng cho |
| --- | --- | --- |
| **DB thật** | Backend Spring Boot chạy thật, PostgreSQL 17 trong container dùng riêng cho kiểm thử, database `study_planner_test` (không phải database demo hoặc của người dùng), Flyway V1–V9 | Mọi ca ở mục 3 và 4 |
| **Provider thật** | Gemini bằng khóa trong `.env` (giá trị không in ra). Backend không còn kế hoạch dự phòng: thiếu khóa trả 503, nên mọi bản nháp thành công đều do Gemini tạo | Các ca ghi `provider+DB` |
| **Mock / test đơn vị** | `PlanDraftControllerTest` (MockMvc) và các test PostgreSQL tích hợp thay Gemini bằng bản giả | Suite tự động ở mục 5 |
| **Giao diện** | Trình duyệt tích hợp, frontend Vite thật nói chuyện với backend thật | Mục 4 |

Gemini không tất định: mỗi lần có thể ra 1 đến 3 việc, hoặc để việc quá 60 phút thành "chưa xếp". Script kiểm thử sinh lại (tối đa 6 lần) cho tới khi bản nháp đủ điều kiện của ca, và ghi rõ ca nào cần điều đó.

## 2. Ánh xạ AC → dữ liệu / thao tác / kỳ vọng

| AC | Dữ liệu | Thao tác | Kỳ vọng |
| --- | --- | --- | --- |
| AC-01 | Mục tiêu 8 ngày, 3 chủ đề, quỹ 60 phút/ngày | Sinh bản nháp; sửa tiêu đề, phút, ngày; thêm/bỏ/đổi chủ đề việc; gửi dữ liệu sai | Bản nháp không tạo task; chỉnh sửa chỉ lưu khi xác nhận; dữ liệu sai bị 400 và không lưu gì |
| AC-02 | Cùng mục tiêu | Sau khi sinh: sửa mục tiêu, hạ quỹ chung, môn khác chiếm quỹ ngày đó, làm bản nháp hết hạn | 409 / 422 / 410, không lưu gì |
| AC-03 | Mục tiêu 2 ngày × 60 phút (thiếu thời gian) | Xác nhận đủ, xác nhận một phần có/không `deferredTasks`, id lạ, lặp lại cùng key, đổi payload | Xác nhận đủ bị từ chối; lưu một phần giữ phần còn lại; lặp cùng key trả kết quả cũ; đổi payload 409 |
| AC-04 | Sau lưu một phần | Đăng nhập lại; tăng quỹ; xếp lại không AI; xác nhận phần còn lại | Lịch và phần chưa xếp còn nguyên; xếp lại không gọi AI; vượt mức mong muốn cần đồng ý rõ |

## 3. Kết quả API + DB + provider thật (49 ca)

**49 đạt, 0 lỗi, 0 bỏ qua.**

| Nhóm | Ca | Kết quả |
| --- | --- | --- |
| AC-01 | Sinh bản nháp: trạng thái DRAFT và 0 task (Gemini thật) | Đạt |
| AC-01 / AC-04 | Tải lại: GET latest trả đúng bản nháp, vẫn 0 task | Đạt |
| AC-01 | Tiêu đề rỗng, 201 ký tự; phút 0, 481, 1,5; ngày trước ngày bắt đầu, sau ngày thi, sai định dạng; trường lạ; trùng id → 400 hoặc 4xx, không lưu gì (10 ca) | Đạt |
| AC-01 | Bỏ một việc không `allowPartial`; thêm việc không có trong bản nháp; đổi chủ đề của việc → 4xx, không lưu gì (3 ca) | Đạt |
| AC-01 | Thiếu `expectedGoalVersion`; `Idempotency-Key` sai hoặc thiếu → 400 (3 ca) | Đạt |
| AC-01 | Xác nhận với tiêu đề/phút/ngày đã sửa: 3 task, giá trị đã sửa được lưu | Đạt |
| AC-03 | Cùng key và payload: 200, cùng id, không nhân đôi | Đạt |
| AC-03 | Cùng key, payload khác: 409 `IDEMPOTENCY_CONFLICT` | Đạt |
| AC-03 | Key mới trên bản nháp đã xác nhận: 409 | Đạt |
| AC-04 | Bản nháp đã xác nhận vẫn đọc được, trạng thái CONFIRMED | Đạt |
| AC-02 | Mục tiêu đổi sau khi sinh: 409 `GOAL_VERSION_CONFLICT`, không lưu | Đạt |
| AC-02 | Hạ quỹ chung sau khi sinh: 422 `SHARED_DAILY_BUDGET_EXCEEDED`, không lưu | Đạt |
| AC-02 | Môn khác chiếm quỹ ngày đó sau khi sinh: 422 vượt quỹ, không lưu | Đạt |
| AC-02 | Bản nháp hết hạn: 410 `DRAFT_EXPIRED`, vẫn đọc được với trạng thái EXPIRED (2 ca) | Đạt |
| AC-03 | Thiếu thời gian: có việc chưa xếp và `canConfirm=false` (Gemini thật, 2 ngày × 60 phút) | Đạt |
| AC-03 | Xác nhận đủ khi còn việc chưa xếp: bị từ chối, không lưu | Đạt |
| AC-03 | Lưu một phần không `deferredTasks` (dạng client cũ): lưu phần đã xếp, phần thiếu giữ lại | Đạt |
| AC-03 | `allowPartial` không phải boolean: 400 | Đạt |
| AC-03 | `deferredTasks` có id lạ: bị từ chối, không lưu | Đạt |
| AC-03 | Lưu một phần có `deferredTasks`: `createdCount` và `remainingCount` đúng, có `remainingDraftId` | Đạt |
| AC-03 | Lặp lại cùng key: 200, không nhân đôi | Đạt |
| AC-03 / AC-04 | `pending` liệt kê phần giữ lại | Đạt |
| AC-04 | Sau đăng nhập lại: task đã lưu và phần giữ lại vẫn còn | Đạt |
| AC-04 | Tăng quỹ rồi xếp lại phần còn lại: 200, dưới 1 giây, không việc nào chưa xếp, không gọi AI | Đạt |
| AC-04 | Phần xếp lại vượt mức mong muốn của môn: 422 `PREFERENCE_EXCEEDANCE_REQUIRES_CONFIRMATION`, không lưu | Đạt |
| AC-04 | Xác nhận phần còn lại có đồng ý rõ (`allowPreferenceExceedance`): lưu đủ | Đạt |
| Quyền dữ liệu | Người khác GET draft, latest, pending, reschedule, generate, confirm → 404 (6 ca) | Đạt |
| Xác thực | Chưa đăng nhập → 401; thiếu CSRF → 403 | Đạt |

## 4. Kết quả trên giao diện (trình duyệt, backend và Gemini thật)

| Bước | Kết quả thực tế |
| --- | --- |
| Lịch học → Lên lịch từ mục tiêu → Tạo bản nháp AI | Bản nháp hiện với ô sửa Tiêu đề / Ngày học / Số phút cho từng việc; khung thiếu thời gian: "Đã xếp 1/3 việc. Còn 2 việc chưa có ngày học. Cần thêm khoảng 120 phút" |
| Sửa tiêu đề và phút của việc 1 (60 → 45) trước khi xác nhận | Lịch đã lưu vẫn 0 việc (AC-01: chưa đổi lịch) |
| Nút xác nhận | Ghi "Lưu 1 việc vào lịch. 2 việc còn lại được giữ để xếp tiếp" và "kế hoạch chưa bao phủ hết nội dung" (AC-03) |
| Bấm "Lưu phần đã xếp" | "Đã thêm 1 buổi học vào lịch."; task lưu đúng giá trị đã sửa (45 phút) |
| Tải lại trang, mở lại bộ lập kế hoạch | Việc đã lưu có trong lịch; cảnh báo "Kế hoạch chưa bao phủ hết nội dung"; nút "Mở phần còn lại · 2 việc" (AC-04) |
| Tăng quỹ chung, mở phần còn lại, "Xếp lại theo quỹ phút đã lưu" | Chỉ có một lời gọi `POST …/reschedule`, không có lời gọi sinh bản nháp AI; giao diện hiện phần vượt mức mong muốn kèm ô "Tôi đồng ý phần vượt mức" |

## 5. Test tự động

Chạy lại trong phiên này, cùng commit:

- Backend: `mvn -f backend/pom.xml -Dgoal.integration=true -Dauth.integration=true -Dplanning.integration=true -Dcalendar.integration=true -Dplanning.test-db-url=<url> -Dcalendar.test-db-url=<url> clean verify -B -ntp` với database kiểm thử riêng tên `study_planner_test` (các test từ chối tên khác). **443 chạy, 0 lỗi, 3 bỏ qua** (440 trước khi sửa, thêm 3 test cho việc người dùng thêm vào bản nháp) (3 test PDF riêng tư tùy chọn, không thuộc phạm vi này). Test liên quan: `PlanDraftControllerTest` (5), `SyllabusPlanPostgresIntegrationTest` (9), `PersistentPlanningPostgresTest` (5), `SharedBudgetPostgresTest` (9), `CombinedFeaturesPostgresIntegrationTest` (9). Các test này dùng bộ sinh giả thay Gemini.
- Frontend: `npm run lint`, `npm test` (152 đạt, 0 lỗi), `npm run build` đều đạt.

## 6. Lỗi và khoảng trống

Cả hai điểm tìm thấy lần chạy đầu đã được sửa trên nhánh `fix/plan-draft-review` (hợp đồng ghi ở [syllabus-revision-plan.md](../product/syllabus-revision-plan.md)):

| # | Mô tả ban đầu | Sửa | Kiểm tra sau sửa |
| --- | --- | --- | --- |
| 1 | AC-01 "thêm hoặc bỏ việc": bản nháp có scope chỉ có ô sửa, không có nút bỏ hay thêm | Nút "Bỏ khỏi lần lưu này" (việc đi vào phần chưa xếp, không bị xóa) và mục "Việc bạn thêm" (chủ đề, tiêu đề, ngày, phút). Backend nhận `addedTasks` ở confirm | 3 test PostgreSQL mới; trên giao diện thật với Gemini thật: bỏ 1 việc, thêm 1 việc, lưu được 2 việc AI và 1 việc nguồn `manual` đúng chủ đề, còn 1 việc `DEFERRED_BY_USER` ở phần chưa xếp |
| 2 | Sau khi tăng quỹ chung, phần còn lại vẫn hiện "Đã xếp 0/2" tới khi bấm "Xếp lại" | Lưu quỹ chung phát sự kiện; bản nháp đang mở tự xếp lại (không AI, không lưu) nếu chưa sửa, còn nếu đã sửa thì giữ chỉnh sửa và hiện cảnh báo | Giao diện thật: "Đã xếp 0/1" thành "Đã xếp đủ 1 việc" ngay sau khi lưu quỹ; ca đã sửa giữ nguyên tiêu đề và hiện cảnh báo |

Không có lỗi nào làm lưu sai dữ liệu trong cả hai lần chạy.

## 7. Chưa kiểm tra

- Hai tab xác nhận đồng thời (có test đồng thời ở các test tự động khác, chưa chạy ca riêng với bản nháp AI).
- Gemini trả 429 hoặc hết thời gian chờ, bản nháp hết hạn trên giao diện, thiết bị di động thật, trình đọc màn hình.
- Chưa có link CI hoặc PR có mã ASP-57.
