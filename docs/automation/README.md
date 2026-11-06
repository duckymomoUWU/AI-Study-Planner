# Jira ↔ GitHub requirements traceability — triển khai giai đoạn 1

> **Trạng thái:** Đề xuất trong draft PR; chưa merge vào `main` và chưa cấu hình đồng bộ nội dung hai chiều. Kiểm tra này **không tự sửa code hoặc Jira** và không chứng minh tính năng đáp ứng AC.

## Mục tiêu
Giúp PO nhìn thấy các thay đổi nghiệp vụ có liên quan giữa Jira → `requirements.md` → code → Pull Request. Không để một thay đổi behavior được merge mà người review hoàn toàn không thấy tác động đến requirements.

## Đã có thể thực hiện không cần API token
- `.github/PULL_REQUEST_TEMPLATE.md`: yêu cầu link Jira issue, khai báo **Requirements impact**, **Code impact**, lý do và bằng chứng kiểm thử.
- `scripts/check-pr-traceability.mjs`: đọc danh sách file thay đổi và PR title/body, kiểm tra các khai báo bắt buộc khi `requirements.md`, `docs/product/*.md` hoặc code ở `backend/`/`frontend/` thay đổi.
- `.github/workflows/traceability.yml`: GitHub Actions chạy kiểm tra trên Pull Request, chỉ yêu cầu `contents: read`, không dùng secret, không tự viết Jira.
- `scripts/check-pr-traceability.test.mjs`: test cho checker, bao gồm trường hợp thiếu mã Jira, thiếu thông tin tác động và giải thích rõ không đổi requirements.

**Giới hạn quan trọng:** Bộ kiểm tra chỉ xác thực tính đầy đủ/tính nhất quán sơ bộ của *khai báo trong PR*, **không tự đọc AC từ Jira**, không đối chiếu ý nghĩa code, không tự phát hiện mọi thay đổi hành vi. Một PR có thể khai báo đúng định dạng nhưng nghiệp vụ vẫn sai.

## Quy tắc khai báo trong PR

Ví dụ code thay đổi nhưng không đổi behavior:

```text
Requirements impact: none
Requirements rationale: Refactor nội bộ, không thay đổi hành vi sinh viên hoặc Acceptance Criteria.
Code impact: no-behavior-change
Test evidence: npm test và npm run build đều pass trên CI [liên kết].
```

Ví dụ PO chốt AC mới nhưng code chưa triển khai:

```text
Requirements impact: updated
Code impact: implementation-needed
Test evidence: Not run — thay đổi tài liệu, cần triển khai theo ASP-9 trước khi test nghiệp vụ.
```

Nếu đổi requirements mà không cần sửa code, phải ghi `Code rationale:` với giải thích rõ. Nếu đổi code và khai báo requirements không ảnh hưởng, phải ghi `Requirements rationale:`. Tiêu đề PR liên quan phải chứa Jira key thật (ví dụ `[ASP-9]`).

## Bước 2 — kết nối development activity trên Jira

**Yêu cầu quyền của quản trị viên Jira/GitHub nếu chưa có tích hợp:**

1. Cài/kết nối **GitHub for Atlassian** với Jira Cloud `ai-study-planner-nhom.atlassian.net`.
2. Cấp quyền đọc development activity cho repository `duckymomoUWU/AI-Study-Planner`.
3. Dùng Jira key trong branch/commit/PR title, ví dụ `ASP-23-create-goal`.
4. Mở Jira issue, kiểm tra phần Development có hiện branch/PR hay không. Nếu không có, chưa được coi là tích hợp thành công.

Tích hợp này chủ yếu đồng bộ **development links và trạng thái**, không tự biến requirements thành code.

## Bước 3 — đồng bộ có AI (chưa cấu hình)

Để tự phát hiện Jira Description/AC thay đổi và tạo phân tích tác động/draft PR cần:

- Một **trình nhận sự kiện tin cậy** (Jira Automation/webhook) chỉ nhận sự kiện project ASP với các trường được phép.
- Một **agent/worker có thể chạy** và có quyền đọc Jira + GitHub; nêu rõ trigger, chi phí/quota, giới hạn tần suất và dấu vết hoạt động.
- Credential/token giới hạn quyền, lưu vào **secrets** của GitHub hoặc dịch vụ tích hợp. **Không gửi token hoặc mật khẩu qua chat, commit hay issue.**
- Quy tắc chống vòng lặp: thay đổi do automation tạo không được kích hoạt lại vô hạn; dedupe theo issue key + revision/PR.
- Agent chỉ gửi **impact analysis và draft PR/comment**; mọi thay đổi nghiệp vụ phải qua PO, code qua review/CI. Không tự merge, không thay đổi `main`.

Các hành động trên chưa được tạo và cũng chưa có token/URL webhook. Không thể gọi workflow giai đoạn 1 là đồng bộ tự động hai chiều.

## Bước 4 — quản lý merge/branch protection (chưa cấu hình)
- PR checker có thể chạy thành công/thất bại; **chỉ chặn merge thực sự nếu GitHub branch protection/ruleset yêu cầu check đó** và quyền repository phù hợp.
- Chỉ thay đổi protection sau khi PO/nhóm phê duyệt chính sách merge; không kết luận rằng check đang chặn merge dựa vào sự tồn tại của workflow.

## Chứng minh thành công
- PR có code và thiếu mã Jira → checker báo lỗi rõ.
- PR có thay đổi requirements nhưng không khai báo `Requirements impact` → checker báo lỗi.
- PR khai báo đầy đủ, có giải thích và test evidence → checker pass.
- Sau khi cấu hình tích hợp Atlassian, Jira hiển thị development link thực cho PR/branch.
- Sau khi có worker AI, thay đổi Jira tạo **draft** có thể review, không auto merge và không thay đổi AC bí mật.

## Nguồn chính
Jira ASP sở hữu Product Backlog và AC. `requirements.md` phản ánh các quy tắc được chốt theo version; code/test xác nhận phần đã triển khai, không tự xác định phạm vi sản phẩm.
