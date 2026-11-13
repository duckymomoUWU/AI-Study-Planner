# Hướng dẫn tích hợp và đặc tả API xác thực (ASP-12)

**Dự án:** AI Study Planner · **Mã Jira:** ASP-12 · **Cập nhật:** 2026-11-07 theo [Backend API rules](backend-api-rules.md)
**Tài liệu liên quan:** [requirements.md](../requirements.md), [ASP-9 Goal API contract](product/ASP-9-goals-api.md), `V1__initial_schema.sql`

Tài liệu này mô tả API xác thực đang chạy ở backend. Những thay đổi so với bản đầu:

| Hạng mục | Trước | Hiện tại |
| --- | --- | --- |
| Đường dẫn | `/api/auth/...`, `/api/auth/me` | `/api/v1/auth/...`, riêng `/api/v1/me` |
| Response thành công | `{id, email}` | `{ "data": {id, email, createdAt} }` |
| Response lỗi | `{code, message}` | `{ "error": {code, message, details} }` |
| Mã lỗi | `invalid_credentials`, `unauthenticated`, `forbidden_or_csrf`... | `INVALID_CREDENTIALS`, `UNAUTHORIZED`, `CSRF_INVALID`... |

## 1. Nguyên tắc bảo mật

- Session phía server trong cookie `JSESSIONID` (`HttpOnly`, `SameSite=Lax`; `Secure` bật bằng biến môi trường `SESSION_COOKIE_SECURE=true` khi chạy HTTPS).
- Mọi thao tác ghi (kể cả đăng ký, đăng nhập, đăng xuất) cần header `X-CSRF-TOKEN`. Token lấy từ `GET /api/v1/auth/csrf`, và phải lấy lại khi phiên thay đổi (đăng nhập trả token mới ở header `X-CSRF-TOKEN` và cookie `XSRF-TOKEN`).
- Mật khẩu băm BCrypt; không bao giờ có trong response hay log. Email được cắt khoảng trắng và đổi sang chữ thường trước khi lưu và tra cứu.
- Đăng nhập thay session ID (chống session fixation). Đăng xuất hủy session và xóa cookie.
- Chưa đăng nhập luôn trả JSON 401, không chuyển hướng sang trang HTML.
- Server không bao giờ tin `userId` do client gửi; người dùng hiện tại luôn lấy từ session.
- Đăng ký không tự đăng nhập; frontend gọi login ngay sau khi đăng ký thành công.

## 2. Endpoint

| Method | Path | Cần session | CSRF | Thành công | Lỗi có thể gặp |
| --- | --- | --- | --- | --- | --- |
| GET | `/api/v1/auth/csrf` | Không | Không | 200 | — |
| POST | `/api/v1/auth/register` | Không | Có | 201 | 400, 403, 409 |
| POST | `/api/v1/auth/login` | Không | Có | 200 | 400, 401, 403 |
| POST | `/api/v1/auth/logout` | Không bắt buộc | Có | 204 | 403 |
| GET | `/api/v1/me` | Có | Không | 200 | 401 |

Mọi đường dẫn nghiệp vụ khác nằm dưới `/api/v1/**` và cũng cần session (chưa đăng nhập → 401 `UNAUTHORIZED`). Chỉ `GET /actuator/health` nằm ngoài `/api/v1`.

### 2.1. `GET /api/v1/auth/csrf`

```json
{ "data": { "token": "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d", "headerName": "X-CSRF-TOKEN" } }
```

Đồng thời gán cookie `XSRF-TOKEN`.

### 2.2. `POST /api/v1/auth/register`

Body: `{ "email": "student@example.com", "password": "<mật khẩu>" }`. Email bắt buộc, đúng định dạng, tối đa 254 ký tự; mật khẩu 6–100 ký tự (chưa chốt chính sách mạnh hơn).

201:

```json
{ "data": { "id": "7514b159-2e98-4b41-956e-74b29072a6fc", "email": "student@example.com", "createdAt": "2026-11-07T06:22:54.180032Z" } }
```

Lỗi:

```json
{ "error": { "code": "EMAIL_ALREADY_EXISTS", "message": "Email đã được sử dụng." } }
```

```json
{ "error": { "code": "VALIDATION_ERROR", "message": "Dữ liệu không hợp lệ.",
  "details": [ { "field": "email", "issue": "invalid_email", "message": "Email không đúng định dạng." } ] } }
```

`issue` có thể là `required`, `invalid_email`, `invalid_length`.

### 2.3. `POST /api/v1/auth/login`

Body như đăng ký. 200 trả `{ "data": { id, email, createdAt } }` và đặt cookie session. Sai email hoặc mật khẩu:

```json
{ "error": { "code": "INVALID_CREDENTIALS", "message": "Email hoặc mật khẩu không chính xác." } }
```

(401, không cho biết email hay mật khẩu sai.) Thiếu trường: 400 `VALIDATION_ERROR`.

### 2.4. `GET /api/v1/me`

200 `{ "data": { id, email, createdAt } }`. Không có session:

```json
{ "error": { "code": "UNAUTHORIZED", "message": "Bạn cần đăng nhập." } }
```

`/api/v1/me?userId=...` bỏ qua `userId`; người dùng luôn là người trong session.

### 2.5. `POST /api/v1/auth/logout`

204, không body. Sau đó `GET /api/v1/me` trả 401. Thiếu CSRF: 403 `CSRF_INVALID`.

### 2.6. Mã lỗi dùng chung

| HTTP | `code` | Ý nghĩa |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR` | Dữ liệu sai; `details[]` nêu từng trường |
| 401 | `UNAUTHORIZED` | Chưa đăng nhập hoặc hết phiên |
| 401 | `INVALID_CREDENTIALS` | Đăng nhập sai |
| 403 | `CSRF_INVALID` | Thiếu hoặc sai CSRF token |
| 409 | `EMAIL_ALREADY_EXISTS` | Email đã dùng |

Frontend xử lý theo `code` và `details[].field`/`issue`, không dựa vào chuỗi `message`.

## 3. Hướng dẫn cho backend (lấy người dùng tin cậy)

Controller nhận người dùng hiện tại bằng:

```java
@GetMapping("/something")
public ApiResponse<...> handler(@AuthenticationPrincipal AppUserPrincipal principal) {
    UUID owner = principal.id();   // luôn lấy từ session, không đọc userId từ body, query hay path
    ...
}
```

Mọi truy vấn lọc theo `owner` ngay trong SQL; truy cập tài nguyên của người khác trả 404 `NOT_FOUND` như tài nguyên không tồn tại. Quy tắc đầy đủ: [Backend API rules](backend-api-rules.md).

## 4. Hướng dẫn cho frontend

- Gọi bằng đường dẫn tương đối (Vite proxy `/api` tới backend), `credentials: 'same-origin'`.
- Dùng sẵn `frontend/src/lib/auth.js`: `getCsrfToken`, `register`, `login`, `logout`, `getCurrentUser` (trả `null` khi 401). Các hàm thành công trả phần `data`; lỗi ném `AuthApiError` có `status`, `code`, `fieldErrors` (`[{field, issue, message}]`).
- Token CSRF được lưu tạm trong bộ nhớ và cập nhật khi header `X-CSRF-TOKEN` đổi (sau đăng nhập).
- Giao diện đăng nhập/đăng ký dùng các thành phần shadcn/ui trong `frontend/src/components/ui/` (`Button`, `Card`, `Input`, `Label`, `Alert`, `Tabs`) và `frontend/src/pages/AuthPage.jsx`.

## 5. Demo bằng curl (đã chạy thật 2026-11-07)

PowerShell hoặc Git Bash; ví dụ dưới là Bash (cần `-c`/`-b` để giữ cookie):

```bash
B=http://localhost:8080; J=cookies.txt; H='Content-Type: application/json'
T=$(curl -s -c $J -b $J $B/api/v1/auth/csrf | sed -E 's/.*"token":"([^"]+)".*/\1/')
curl -s -c $J -b $J -H "X-CSRF-TOKEN: $T" -H "$H" -d '{"email":"sv1@example.test","password":"Pass123456"}' $B/api/v1/auth/register
curl -s -c $J -b $J -H "X-CSRF-TOKEN: $T" -H "$H" -d '{"email":"sv1@example.test","password":"Pass123456"}' $B/api/v1/auth/login
curl -s -b $J $B/api/v1/me
curl -s -c $J -b $J -H "X-CSRF-TOKEN: $T" -X POST -w "%{http_code}\n" $B/api/v1/auth/logout
curl -s -b $J -w " %{http_code}\n" $B/api/v1/me
```

Kết quả: đăng ký 201 và `{"data":{...}}`; đăng ký lại cùng email 409 `EMAIL_ALREADY_EXISTS`; đăng nhập sai 401 `INVALID_CREDENTIALS`; đăng nhập đúng 200; `/me` 200; đăng xuất 204; `/me` sau đó 401 `UNAUTHORIZED`.

## 6. Kiểm thử

| Lớp test | Nội dung |
| --- | --- |
| `AuthControllerIntegrationTest` (MockMvc, 17 ca) | CSRF, đăng ký/đăng nhập/`/me`/đăng xuất, mã lỗi, `userId` giả bị bỏ qua |
| `AuthServiceTest` (6 ca) | Băm mật khẩu, chuẩn hóa email, trùng email, `getUser` |
| `AuthUserRepositoryTest` (7 ca) | Truy vấn tài khoản |
| `AuthPostgresIntegrationTest` (2 ca, PostgreSQL thật) | Luồng đăng ký → đăng nhập → `/me` → đăng xuất → 401; hai tài khoản cô lập |
| Frontend `auth.test.js` (8 ca) | Client dùng đường dẫn `/api/v1`, bóc `data`, đọc `error` |

Chạy:

```bash
mvn -f backend/pom.xml verify                                          # test không cần DB
mvn -f backend/pom.xml -Dgoal.integration=true -Dauth.integration=true verify   # thêm test PostgreSQL (chỉ trên DB thử nghiệm)
cd frontend && npm test
```

Test PostgreSQL xóa dữ liệu bảng `app_users`, chỉ chạy trên cơ sở dữ liệu dùng một lần. Trên máy Windows có múi giờ `Asia/Saigon`, đặt `JAVA_TOOL_OPTIONS=-Duser.timezone=Asia/Ho_Chi_Minh` vì PostgreSQL 17 từ chối tên cũ.

## 7. Chưa làm

Chính sách mật khẩu mạnh, giới hạn tần suất đăng nhập, thời hạn session và nơi lưu session (mặc định bộ nhớ), quên mật khẩu, OAuth. Đây là các mục mở trong mục 14 của đặc tả backend.
