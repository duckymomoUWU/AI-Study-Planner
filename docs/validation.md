# Xác minh bộ khung — 31/10/2026

- npm install và npm ci từ lockfile: thành công.
- TypeScript: thành công.
- Domain tests: 6 pass, 0 fail, 0 skipped; có kiểm tra nhiều tổ hợp ngân sách/thời lượng.
- Production build Next.js: thành công; trang chính và hai API route được tạo.
- npm start: server khởi động thành công.
- node scripts/smoke.mjs: trang trả 200; SQLite ghi/đọc schema marker thành công; 180 phút/3 ngày hợp lệ; 135 phút quỹ giờ báo thiếu 45; ngày thi không hợp lệ trả 400.
- GitHub import: dry-run 6 Epic + 21 story thành công; đối chiếu CLI flags bằng gh help. Chưa kiểm chứng --apply vì GitHub API trả Forbidden.
- Chưa chạy browser E2E, live AI, auth/ownership hoặc phục hồi dữ liệu người dùng vì các chức năng này chưa được triển khai.
- CI YAML đã tạo, chưa chạy trên GitHub vì mã nguồn chưa push.

Một lượt build trong sandbox hạn chế bị lỗi đọc output TypeScript subprocess; chạy bằng cơ chế quyền thực thi được hỗ trợ thì build thành công. Test runner trong sandbox từng chỉ báo enclosing file; xác minh lại trực tiếp và bằng lượt chạy có đủ quyền cho thấy cả 6 case thực sự chạy.

Đã lưu draft install_script, start_skill và bổ sung api.github.com vào danh sách miền tùy chỉnh (giữ preset package managers). Draft chưa được publish hoặc xác minh khôi phục trong task mới. Người dùng xem/lưu trong environment settings rồi Publish để kích hoạt snapshot. Không lưu secret hoặc giả lập credentials.
