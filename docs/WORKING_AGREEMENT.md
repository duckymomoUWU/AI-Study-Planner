# Working Agreement — AI Study Planner

> **Bản 1.1 — PO đã cập nhật theo các quyết định được đề xuất.** Trạng thái: chờ Scrum Master xác nhận sau khi lấy ý kiến Scrum Team. Những mục ghi **[Cần chốt]** vẫn chưa phải cam kết của nhóm.

## 1. Mục đích
Thống nhất cách trao đổi, quản lý Product Backlog, chia nhỏ công việc, review code, xử lý trở ngại và sử dụng AI. Working Agreement áp dụng cho toàn Scrum Team; PO đề xuất, Scrum Master điều phối việc xác nhận cùng nhóm.

## 2. Vai trò
- **PO:** truyền đạt Product Goal; làm rõ yêu cầu và Acceptance Criteria (AC); quản lý, sắp thứ tự Product Backlog; quyết định các thay đổi về giá trị, phạm vi và hành vi sản phẩm.
- **Scrum Master:** hỗ trợ Scrum Team hiểu và thực hiện Scrum; điều phối việc thảo luận, xác nhận Working Agreement; giúp xử lý trở ngại và cải tiến cách làm việc.
- **Developers:** cùng lập Sprint Backlog, tự quyết định cách chia và thực hiện công việc, thiết kế kỹ thuật, kiểm thử, review và chịu trách nhiệm về chất lượng Increment. PO không tự áp đặt cách triển khai hoặc phân công kỹ thuật.

## 3. Công cụ và nguồn thông tin

| Công cụ / tài liệu | Vai trò đã thống nhất |
| --- | --- |
| **Jira ASP** | Nguồn chính quản lý Product Backlog: Epic, User Story, Sub-task, AC, Priority, Sprint và trạng thái công việc. |
| **GitHub** | Quản lý source code, branch, commit, Pull Request và kết quả CI. GitHub Issues không được dùng làm Product Backlog song song. |
| **`requirements.md` trong repository** | Tài liệu tham chiếu chi tiết cho Developers về các quy tắc sản phẩm đã được PO chấp thuận; không phải Product Backlog thứ hai. Khi yêu cầu/AC được duyệt trên Jira thay đổi, cập nhật tài liệu này qua PR để giữ đồng bộ. |
| **Zalo** | Kênh trao đổi chính của nhóm. Quyết định liên quan đến sản phẩm phải được ghi lại trên Jira; trao đổi miệng/tin nhắn không thay thế việc cập nhật ticket. |

**Thứ tự xử lý khi thông tin không khớp:** Jira là nguồn chính cho Product Backlog và AC đã được duyệt; `requirements.md` là tài liệu tham chiếu trong repository; code thể hiện hành vi đang được triển khai. Nếu phát hiện mâu thuẫn hoặc chưa rõ yêu cầu nào đã được chấp thuận, gắn **Needs PO decision** và hỏi PO, không tự suy đoán.

## 4. Giao tiếp và phối hợp
- **Kênh chính:** Zalo.
- **Daily Scrum:** diễn ra theo lịch cụ thể do nhóm thống nhất. Nếu thực hiện Scrum đầy đủ, tổ chức mỗi ngày làm việc trong Sprint; timebox tối đa 15 phút. **[Cần chốt]** ngày/giờ cố định và cách tham gia khi vắng mặt.
- Khi yêu cầu chưa rõ, Developer bình luận vào Jira Story và thông báo PO qua Zalo; PO làm rõ AC trước khi nhóm triển khai phần bị ảnh hưởng.
- Thành viên thông báo blocker ngay khi nhận ra, nêu ảnh hưởng và điều cần hỗ trợ.
- **Review PR:** người được yêu cầu review phản hồi trong vòng **24 giờ làm việc**. Nếu không thể đáp ứng, cần báo nhóm để thống nhất người review hoặc thời điểm phù hợp.

## 5. Làm việc với User Story và Sub-task
1. PO cung cấp mục tiêu, giá trị, User Story, AC, phạm vi, độ ưu tiên và phụ thuộc trên Jira.
2. Developers cùng PO/SM refinement để làm rõ story. AI có thể đề xuất sub-task, thứ tự và cách kiểm chứng; AI không tự quyết định phạm vi hoặc tự phân công.
3. Developers review đề xuất, điều chỉnh cách chia, xác định phụ thuộc, estimate và người thực hiện.
4. Mỗi sub-task cần có đầu ra kiểm tra được (ví dụ: hành vi UI, API, validation, migration hoặc test), tránh mô tả quá chung như “làm backend”.
5. Không chuyển một đề xuất của AI thành yêu cầu đã được PO chấp thuận nếu chưa được review.

## 6. Branch, Pull Request và chất lượng
- **[Cần chốt]** Quy ước branch đề xuất: `ASP-<ID>-mo-ta-ngan`, ví dụ `ASP-123-goal-crud`. Chỉ sử dụng sau khi nhóm thống nhất.
- PR title/commit nên ghi mã Jira thật để liên kết với ticket, ví dụ `[ASP-123] Implement goal creation`.
- PR mô tả: Jira Story/AC liên quan; hành vi thay đổi; tác động tới `requirements.md`; các lệnh kiểm tra **đã thực sự chạy** và kết quả; phần chưa kiểm chứng; bước demo.
- **Code Review:** trước khi merge, cần ít nhất **1 Developer khác tác giả PR** review.
- Không merge code nghiệp vụ khi chưa đáp ứng AC và quy trình review của nhóm. Không đưa secret, mật khẩu hoặc API key lên repository.
- Definition of Done (DoD) phải được Developers thống nhất và áp dụng cho Increment. Checklist DoD trong báo cáo hiện là bản đề xuất, cần Scrum Team xác nhận; không coi việc ghi checklist là bằng chứng test đã pass.
- CI hiện có kiểm tra frontend; backend và end-to-end cần được kiểm tra riêng theo verification harness. Không coi build thành công là bằng chứng đầy đủ cho hành vi nghiệp vụ.

## 7. Thay đổi yêu cầu và xử lý sai lệch
- Khi PO thay đổi yêu cầu: cập nhật Jira trước (lý do, AC cũ/mới, phạm vi và ảnh hưởng Sprint); sau khi được làm rõ, Developers đánh giá tác động và thực hiện code/test trong branch/PR.
- Khi code/PR làm thay đổi hành vi sản phẩm: Developer mô tả hành vi trước/sau và đề xuất AC/requirements cần cập nhật; PO quyết định chấp thuận hay không. Không sửa tài liệu để hợp thức hóa một thay đổi chưa được PO duyệt.
- Khi Jira, `requirements.md`, code hoặc test mâu thuẫn, ghi nhận **Needs PO decision**. Không mặc định code hiện tại là yêu cầu đúng.
- Tích hợp Jira–GitHub có thể liên kết ticket với branch/commit/PR và hiển thị thông tin phát triển; không tự động bảo đảm code hoặc requirements được sửa đúng. AI chỉ tạo phân tích/draft; con người review và phê duyệt.

## 8. Xác nhận Working Agreement
- **PO đề xuất và cập nhật** bản Working Agreement.
- **Scrum Master xác nhận** sau khi điều phối để Developers và các thành viên Scrum Team được góp ý, thống nhất khả năng thực hiện.
- Developers xác nhận đã review và hiểu các quy ước ảnh hưởng trực tiếp đến công việc của mình.
- Nhóm xem xét lại thỏa thuận trong Sprint Retrospective khi cần.

| Vai trò | Đại diện | Ngày xác nhận |
| --- | --- | --- |
| PO | [điền tên] | [điền] |
| Scrum Master | Trinh | [điền] |
| Developers | [các thành viên xác nhận] | [điền] |

## 9. Các mục còn cần chốt
- [ ] Ngày/giờ cụ thể của Daily Scrum và cách tham gia khi vắng mặt.
- [ ] Độ dài Sprint và lịch Sprint Planning, Sprint Review, Sprint Retrospective.
- [ ] DoD chính thức của Scrum Team (đối chiếu mục 7 trong báo cáo khởi động).
- [ ] Quy ước branch/commit/PR chính thức (đề xuất ở mục 6).
- [ ] Tên PO và xác nhận của từng thành viên.
- [ ] Xác nhận mọi thành viên có quyền truy cập Jira và thống nhất cách xử lý các GitHub Issues cũ (lưu tham khảo, không duy trì backlog song song).

Sau khi các mục này được thống nhất, Scrum Master xác nhận bản cuối và nhóm dùng nó trong Sprint; điều chỉnh lại qua Retrospective khi có bằng chứng thực tế cần thay đổi.
