# AI Study Planner

Bộ khung khởi đầu và bộ tài liệu làm việc cho nhóm 6 người: 1 PO, 1 Scrum Master, 4 Developers. Mục tiêu gần nhất: refinement User Stories và khởi tạo công nghệ trong tuần 02–08/11/2026.

## Bắt đầu

Cần **Node 24.x**, npm và Python 3 (chỉ cho công cụ import GitHub). Không cần database server, Docker hoặc AI key để chạy bộ khung.

```bash
cd /workspace/AI-Study-Planner
npm ci
npm run check
npm run dev
```

Trên máy cá nhân, thay đường dẫn bằng nơi clone repo. `npm run dev` dùng cổng 3000; đổi cổng bằng `npm run dev -- --port 3001`. Kiểm tra nội bộ `curl --fail http://127.0.0.1:3000/api/health`. Build production: `npm run build`, rồi `npm start`.

Database SQLite được tạo tự động ở `.data/study.sqlite` khi kiểm tra health. Có thể đặt `STUDY_DATABASE_PATH`; xem `.env.example`. Không cần tạo key giả. Node có thể in cảnh báo node:sqlite experimental. App hiện chỉ dùng bảng kiểm tra DB, chưa lưu dữ liệu cá nhân.

## Đã có / chưa có

Đã có: UI tiếng Việt thử lập lịch từ công việc tự nhập; API stateless; scheduler kiểm tra ngày, ngân sách và báo thiếu giờ; smoke DB; unit tests; TypeScript; build; CI config; bộ import GitHub chạy dry-run.

Chưa có: tài khoản, ownership, CRUD mục tiêu/công việc, lưu nháp/kế hoạch, AI provider thật, dashboard tiến độ, triển khai production. Các phần này là backlog, không được quảng cáo là đã hoạt động. Bộ khung chỉ dùng dữ liệu mẫu; chưa dùng cho người dùng thật. CI chưa chạy trên GitHub cho đến khi code được push.

## Tài liệu làm việc

- [Lịch bàn giao tuần tới](docs/next-week.md)
- [Team board, vai trò, DoD và Scrum](docs/team-board.md)
- [6 Epic và 21 User Story với AC](docs/backlog.md)
- [Backlog JSON](docs/backlog.json) · [CSV](docs/backlog.csv)
- [Business rules, pilot, metrics và chi phí](docs/business.md)
- [Công nghệ, kiến trúc và dữ liệu](docs/architecture.md)
- [AI agent ở từng bước phát triển](docs/ai-workflow.md)

```bash
python scripts/setup_github.py          # chỉ xem kế hoạch
python scripts/setup_github.py --apply  # tạo Project/Issues, cần quyền GitHub
```

GitHub API hiện bị Forbidden trong môi trường onboarding; chưa có board online, chưa push. Script đã kiểm tra dry-run và đối chiếu gh CLI flags; đường tạo thật chưa được kiểm chứng do quyền truy cập. Xem bước UI và quyền cần thiết trong team-board.md.

## Nguồn và giả định

Dựa trên vision/phạm vi và ảnh tiêu chí môn học người dùng cung cấp. Đối chiếu bản tiếng Việt Scrum Guide 2020 đính kèm: Scrum Team/roles trang 5–6; events trang 7–10; artifacts và commitments trang 10–12. Epic, story points, GitHub board, WIP, Ready checklist và thời lượng sự kiện đề xuất là thực hành của nhóm, không phải quy định bắt buộc từ Scrum Guide.

Mỗi cloud task đã cô lập; dùng checkout hiện có, không tạo Git worktree trừ khi được yêu cầu. Tiến trình server không tồn tại qua snapshot/khởi động mới: chạy lại theo hướng dẫn. Không lưu secrets vào repo hoặc gửi chúng trong chat.
