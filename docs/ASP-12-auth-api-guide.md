# TÀI LIỆU HƯỚNG DẪN TÍCH HỢP VÀ ĐẶC TẢ BỘ API AUTHENTICATION (DEV 1 — ASP-12)

**Dự án:** AI Study Planner  
**Thành viên:** DEV 1 — Backend Authentication  
**Mã Jira:** ASP-12  
**Phiên bản:** 1.0.0 (Release-ready)  
**Tài liệu tham chiếu:** Phân công 4 Dev, `requirements.md`, `V1__initial_schema.sql`, `ASP-9-goals-api.md`

---

## 1. TỔNG QUAN HỆ THỐNG VÀ NGUYÊN TẮC BẢO MẬT

Hệ thống xác thực và phân quyền (Authentication & Authorization) được xây dựng trên nền tảng **Spring Boot 3.5**, **Spring Security 6**, **BCrypt** và **PostgreSQL** nhằm bảo đảm dữ liệu người dùng được cách ly an toàn tuyệt đối.

### Các nguyên tắc bảo mật cốt lõi (Security Invariants):
1. **Xác thực qua Session Cookie HttpOnly:**
   - Server quản lý phiên thông qua `HttpSession`. Cookie phiên `JSESSIONID` được cấu hình nghiêm ngặt: `HttpOnly = true`, `SameSite = Lax`, `Path = /`.
   - JavaScript phía client (browser) **không thể** đọc cookie này, ngăn chặn hoàn toàn rủi ro đánh cắp phiên qua tấn công XSS.
2. **Mã hóa mật khẩu bằng BCrypt (Cost factor 12):**
   - Mật khẩu người dùng tuyệt đối không lưu dạng plain-text và không bao giờ xuất hiện trong log hoặc response JSON.
   - Sử dụng `BCryptPasswordEncoder(12)` để băm mật khẩu một chiều có muối (salted hash).
3. **Bảo vệ chống giả mạo request (CSRF Protection):**
   - Mọi request làm thay đổi trạng thái hệ thống (`POST`, `PUT`, `DELETE`, `PATCH`) bắt buộc phải gửi kèm CSRF Token hợp lệ qua header `X-CSRF-TOKEN` (hoặc `X-XSRF-TOKEN`).
   - Nếu thiếu hoặc sai token CSRF, server lập tức từ chối với mã **403 Forbidden**.
4. **Nhận diện người dùng từ Session máy chủ (Trusted Server Principal):**
   - Mọi nghiệp vụ lấy dữ liệu người dùng (như `/api/auth/me`, `/api/goals`) được trích xuất trực tiếp từ `SecurityContext` của Session hiện tại.
   - Server **tuyệt đối không nhận, không đọc và không tin cậy** bất kỳ tham số `userId` nào do client gửi lên (dù ở query param hay body).
   - Nếu client chưa đăng nhập hoặc phiên hết hạn, server trả về **401 Unauthorized**.

---

## 2. DANH SÁCH ENDPOINT API AUTH THEO CHUẨN JSON

Tất cả các response lỗi hoặc thành công đều tuân thủ chuẩn JSON đồng nhất, có mã lỗi máy đọc (`code`) và thông báo tiếng Việt thân thiện với người dùng.

### Bảng tóm tắt Endpoint

| Phương thức | Đường dẫn (Path) | Quyền truy cập | Mục đích | Mã HTTP trả về |
| :--- | :--- | :--- | :--- | :--- |
| **GET** | `/api/auth/csrf` | Công khai (`PermitAll`) | Khởi tạo phiên & nhận CSRF token | **200 OK** |
| **POST** | `/api/auth/register` | Công khai (cần CSRF) | Đăng ký tài khoản sinh viên mới | **201 Created** / 400 / 409 |
| **POST** | `/api/auth/login` | Công khai (cần CSRF) | Đăng nhập, thiết lập session | **200 OK** / 400 / 401 |
| **GET** | `/api/auth/me` | Cần đăng nhập (`Authenticated`) | Lấy thông tin tài khoản hiện tại | **200 OK** / **401 Unauthorized** |
| **POST** | `/api/auth/logout` | Cần đăng nhập (cần CSRF) | Hủy session, xóa cookie | **204 No Content** |

---

### Chi tiết từng Endpoint

#### 2.1. `GET /api/auth/csrf`
- **Mục đích:** Cung cấp CSRF token cho client trước khi thực hiện các thao tác ghi (đăng ký, đăng nhập, tạo mục tiêu). Đồng thời gán cookie `XSRF-TOKEN` để hỗ trợ các thư viện client như Axios.
- **Header gửi lên:** Không bắt buộc.
- **Response mẫu (200 OK):**
```json
{
  "headerName": "X-CSRF-TOKEN",
  "parameterName": "_csrf",
  "token": "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d"
}
```

---

#### 2.2. `POST /api/auth/register`
- **Mục đích:** Đăng ký tài khoản mới cho sinh viên.
- **Headers bắt buộc:**
  - `Content-Type: application/json`
  - `X-CSRF-TOKEN: <csrf-token>`
  - `Cookie: JSESSIONID=<id>` (nếu có từ bước lấy CSRF)
- **Request Body mẫu:**
```json
{
  "email": "sinhvien@hcmut.edu.vn",
  "password": "MatKhauBaoMat123@"
}
```
- **Quy tắc kiểm tra hợp lệ (Validation):**
  - `email`: Bắt buộc, đúng định dạng email, tối đa 254 ký tự, được tự động chuẩn hóa về chữ thường (lowercase) và cắt khoảng trắng thừa.
  - `password`: Bắt buộc, độ dài từ 6 đến 100 ký tự.
- **Response thành công (201 Created):**
```json
{
  "id": "e3a89047-975d-439f-b98a-a53ec8675d04",
  "email": "sinhvien@hcmut.edu.vn"
}
```
*(Lưu ý: Không bao giờ trả về mật khẩu hoặc chuỗi băm passwordHash).*

- **Response lỗi trùng email (409 Conflict):**
```json
{
  "code": "email_already_exists",
  "message": "Email đã được sử dụng."
}
```

- **Response lỗi định dạng dữ liệu (400 Bad Request):**
```json
{
  "code": "validation_failed",
  "message": "Dữ liệu yêu cầu không hợp lệ.",
  "fieldErrors": [
    {
      "field": "email",
      "code": "email",
      "message": "Email không đúng định dạng."
    }
  ]
}
```

---

#### 2.3. `POST /api/auth/login`
- **Mục đích:** Xác thực thông tin sinh viên, khởi tạo phiên bảo mật và cấp cookie `JSESSIONID`.
- **Headers bắt buộc:**
  - `Content-Type: application/json`
  - `X-CSRF-TOKEN: <csrf-token>`
- **Request Body mẫu:**
```json
{
  "email": "sinhvien@hcmut.edu.vn",
  "password": "MatKhauBaoMat123@"
}
```
- **Response thành công (200 OK):**
  - **Set-Cookie:** `JSESSIONID=...; Path=/; HttpOnly; SameSite=Lax`
  - **Header trả về:** `X-CSRF-TOKEN: <token-mới>`
  - **Body JSON:**
```json
{
  "id": "e3a89047-975d-439f-b98a-a53ec8675d04",
  "email": "sinhvien@hcmut.edu.vn"
}
```

- **Response lỗi sai tài khoản / mật khẩu (401 Unauthorized):**
```json
{
  "code": "invalid_credentials",
  "message": "Email hoặc mật khẩu không chính xác."
}
```

- **Response lỗi để trống trường (400 Bad Request):**
```json
{
  "code": "validation_failed",
  "message": "Dữ liệu yêu cầu không hợp lệ.",
  "fieldErrors": [
    {
      "field": "password",
      "code": "notblank",
      "message": "Mật khẩu không được để trống."
    }
  ]
}
```

---

#### 2.4. `GET /api/auth/me`
- **Mục đích:** Lấy thông tin tài khoản đang đăng nhập trong phiên làm việc hiện tại.
- **Headers gửi lên:**
  - `Cookie: JSESSIONID=...` (trình duyệt tự gửi khi dùng `credentials: 'same-origin'`)
- **Response khi đã đăng nhập (200 OK):**
```json
{
  "id": "e3a89047-975d-439f-b98a-a53ec8675d04",
  "email": "sinhvien@hcmut.edu.vn"
}
```
- **Response khi chưa đăng nhập / phiên hết hạn (401 Unauthorized):**
```json
{
  "code": "unauthenticated",
  "message": "Bạn cần đăng nhập."
}
```

---

#### 2.5. `POST /api/auth/logout`
- **Mục đích:** Hủy phiên làm việc trên server, vô hiệu hóa session context và xóa cookie `JSESSIONID`.
- **Headers bắt buộc:**
  - `X-CSRF-TOKEN: <csrf-token>`
  - `Cookie: JSESSIONID=...`
- **Response thành công (204 No Content):** Không có nội dung trả về, cookie `JSESSIONID` bị xóa (`Max-Age=0`).

---

#### 2.6. Xử lý lỗi CSRF chung (403 Forbidden)
Nếu client gửi request `POST`, `PUT`, `DELETE` mà quên gửi `X-CSRF-TOKEN` hoặc token không khớp:
```json
{
  "code": "forbidden_or_csrf",
  "message": "Yêu cầu không hợp lệ hoặc phiên đã hết hạn."
}
```

---

## 3. HƯỚNG DẪN TÍCH HỢP CHO DEV 2 (BACKEND GOAL API)

DEV 2 (Jira: ASP-23 / ASP-24 / ASP-26) thực hiện các API Goal (`/api/goals`). `SecurityConfig` của DEV 1 đã tự động bảo vệ tất cả các URL `/api/**` yêu cầu xác thực.

### Cách DEV 2 lấy User ID tin cậy từ Session

DEV 2 **không được** nhận `userId` từ body hay query param. DEV 2 inject trực tiếp `AppUserPrincipal` do DEV 1 cung cấp vào Controller method:

```java
package com.studyplanner.goals;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.studyplanner.auth.AppUserPrincipal;

@RestController
@RequestMapping("/api/goals")
public class GoalController {

    private final GoalService goalService;

    public GoalController(GoalService goalService) {
        this.goalService = goalService;
    }

    @GetMapping
    public ResponseEntity<List<GoalResponse>> listGoals(@AuthenticationPrincipal AppUserPrincipal principal) {
        // Lấy User ID tin cậy 100% từ Session máy chủ
        UUID currentUserId = principal.id();
        List<GoalResponse> goals = goalService.listGoals(currentUserId);
        return ResponseEntity.ok(goals);
    }

    @PostMapping
    public ResponseEntity<GoalResponse> createGoal(@AuthenticationPrincipal AppUserPrincipal principal,
                                                   @RequestBody GoalCreateRequest request) {
        UUID currentUserId = principal.id();
        GoalResponse created = goalService.createGoal(currentUserId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @DeleteMapping("/{goalId}")
    public ResponseEntity<Void> deleteGoal(@AuthenticationPrincipal AppUserPrincipal principal,
                                           @PathVariable UUID goalId) {
        UUID currentUserId = principal.id();
        goalService.deleteGoal(currentUserId, goalId);
        return ResponseEntity.noContent().build();
    }
}
```

> **Ghi chú quan trọng cho DEV 2:**
> - `principal.id()` trả về `java.util.UUID` của người dùng tương ứng trong bảng `app_users`.
> - `principal.getUsername()` trả về `String email` đã chuẩn hóa.
> - Nếu một người dùng chưa đăng nhập gọi tới `/api/goals`, Spring Security sẽ chặn ngay từ bộ lọc và trả về HTTP 401 `{ "code": "unauthenticated", "message": "Bạn cần đăng nhập." }` mà không chạm vào controller của DEV 2.

---

## 4. HƯỚNG DẪN TÍCH HỢP CHO DEV 3 (FRONTEND AUTH) & DEV 4 (FRONTEND GOAL)

Frontend (React + Vite) chạy tại cổng `5173`, Vite proxy tự động chuyển tiếp `/api` sang backend tại `http://localhost:8080`.

### 4.1. Quy tắc cấu hình Fetch / Axios
1. Luôn khai báo `credentials: 'same-origin'` trong `fetch()` (hoặc `withCredentials: true` trong Axios) để cookie `JSESSIONID` được tự động gửi kèm và lưu trữ.
2. Với mọi request ghi (`POST`, `PUT`, `DELETE`), luôn gửi header `X-CSRF-TOKEN`.

### 4.2. File mẫu `apiClient.js` dùng chung cho Frontend

DEV 3 và DEV 4 có thể dùng trực tiếp module sau để giao tiếp với backend:

```javascript
// frontend/src/lib/authClient.js

let cachedCsrfToken = null;

/**
 * 1. Lấy CSRF token (chỉ gọi lại khi chưa có hoặc khi phiên bị làm mới)
 */
export async function getCsrfToken() {
  if (cachedCsrfToken) return cachedCsrfToken;

  const res = await fetch('/api/auth/csrf', {
    method: 'GET',
    credentials: 'same-origin',
  });

  if (!res.ok) {
    throw new Error('Không thể lấy CSRF token');
  }

  const data = await res.json();
  cachedCsrfToken = data.token;
  return cachedCsrfToken;
}

/**
 * 2. Đăng ký tài khoản
 */
export async function register(email, password) {
  const token = await getCsrfToken();
  const res = await fetch('/api/auth/register', {
    method: 'POST',
    credentials: 'same-origin',
    headers: {
      'Content-Type': 'application/json',
      'X-CSRF-TOKEN': token,
    },
    body: JSON.stringify({ email, password }),
  });

  const data = await res.json();
  if (!res.ok) {
    throw data; // Chứa { code, message, fieldErrors }
  }
  return data;
}

/**
 * 3. Đăng nhập
 */
export async function login(email, password) {
  const token = await getCsrfToken();
  const res = await fetch('/api/auth/login', {
    method: 'POST',
    credentials: 'same-origin',
    headers: {
      'Content-Type': 'application/json',
      'X-CSRF-TOKEN': token,
    },
    body: JSON.stringify({ email, password }),
  });

  // Cập nhật lại CSRF token nếu backend gửi token mới sau khi login
  const newCsrf = res.headers.get('X-CSRF-TOKEN');
  if (newCsrf) cachedCsrfToken = newCsrf;

  const data = await res.json();
  if (!res.ok) {
    throw data; // Chứa { code: 'invalid_credentials', message: ... }
  }
  return data;
}

/**
 * 4. Lấy thông tin tài khoản hiện tại (/me)
 */
export async function getCurrentUser() {
  const res = await fetch('/api/auth/me', {
    method: 'GET',
    credentials: 'same-origin',
  });

  if (res.status === 401) {
    return null; // Chưa đăng nhập
  }

  if (!res.ok) {
    throw new Error('Lỗi kiểm tra phiên');
  }

  return await res.json();
}

/**
 * 5. Đăng xuất
 */
export async function logout() {
  const token = await getCsrfToken();
  const res = await fetch('/api/auth/logout', {
    method: 'POST',
    credentials: 'same-origin',
    headers: {
      'X-CSRF-TOKEN': token,
    },
  });

  cachedCsrfToken = null; // Xóa cache token
  if (!res.ok && res.status !== 204) {
    throw new Error('Đăng xuất thất bại');
  }
  return true;
}
```

### 4.3. Thư viện giao diện shadcn/ui & Màn hình AuthPage

Dựa trên triết lý component composable của [shadcn/ui](https://github.com/shadcn-ui/ui), giao diện xác thực được thiết kế theo tiêu chuẩn accessible, sạch và có thể tùy biến mở rộng:

#### Các component shadcn/ui được xây dựng (`frontend/src/components/ui/`):
- **`Button` (`button.jsx`):** Nút bấm chuẩn với các variants (`default`, `secondary`, `outline`, `ghost`, `link`), hỗ trợ trạng thái `isLoading` kèm spinner SVG.
- **`Card` (`card.jsx`):** Hệ thống thẻ `Card`, `CardHeader`, `CardTitle`, `CardDescription`, `CardContent`, `CardFooter`.
- **`Input` (`input.jsx`):** Ô nhập liệu chuẩn accessibility, focus ring và trạng thái viền đỏ khi có lỗi `hasError`.
- **`Label` (`label.jsx`):** Nhãn liên kết chặt chẽ với ô nhập qua `htmlFor`.
- **`Alert` (`alert.jsx`):** Thông báo lỗi (`variant="destructive"`) hoặc thành công (`variant="success"`) có icon tương ứng.
- **`Tabs` (`tabs.jsx`):** Điều hướng tab giữa "Đăng nhập" và "Tạo tài khoản" với `role="tablist"` và `aria-selected`.
- **`shadcn.css`:** Chứa toàn bộ Design Tokens theo hệ màu HSL của shadcn/ui.

#### Màn hình `AuthPage.jsx` (`frontend/src/pages/AuthPage.jsx`):
- Chuyển đổi tab linh hoạt giữa Đăng nhập và Đăng ký.
- Kiểm tra tính hợp lệ của dữ liệu ngay tại client (email định dạng hợp lệ, mật khẩu >= 6 ký tự, mật khẩu xác nhận trùng khớp).
- Hiển thị lỗi máy chủ chi tiết: `invalid_credentials`, `email_already_exists`, lỗi từng field (`fieldErrors`).
- Tự động đăng nhập sau khi tạo tài khoản thành công.
- Kết nối trực tiếp với `App.jsx`, lưu trữ phiên làm việc của sinh viên trong ứng dụng.

---

## 5. DEMO LUỒNG XÁC THỰC HOÀN CHỈNH BẰNG CURL

Các lập trình viên có thể mở PowerShell hoặc Bash để chạy demo kiểm thử trọn vẹn kịch bản:  
**Lấy CSRF → Đăng ký → Đăng nhập → Gọi /me → Gọi API Goal → Đăng xuất → Gọi lại /me (nhận 401)**.

### Bước 1: Lấy CSRF token và lưu cookie jar
```powershell
curl -c cookies.txt -s http://localhost:8080/api/auth/csrf
```
*Kết quả:* Nhận được JSON chứa `"token": "..."` và file `cookies.txt` được tạo.

### Bước 2: Đăng ký tài khoản mới (POST /api/auth/register)
```powershell
curl -b cookies.txt -c cookies.txt -H "Content-Type: application/json" -H "X-CSRF-TOKEN: <TOKEN_Ở_BƯỚC_1>" -d '{\"email\":\"sinhvien1@test.com\",\"password\":\"Pass123456\"}' http://localhost:8080/api/auth/register
```
*Kết quả:* HTTP 201 Created `{"id":"...","email":"sinhvien1@test.com"}`.

### Bước 3: Đăng nhập (POST /api/auth/login)
```powershell
curl -b cookies.txt -c cookies.txt -H "Content-Type: application/json" -H "X-CSRF-TOKEN: <TOKEN_Ở_BƯỚC_1>" -d '{\"email\":\"sinhvien1@test.com\",\"password\":\"Pass123456\"}' http://localhost:8080/api/auth/login
```
*Kết quả:* HTTP 200 OK `{"id":"...","email":"sinhvien1@test.com"}`. File `cookies.txt` cập nhật session cookie `JSESSIONID`.

### Bước 4: Lấy thông tin user hiện tại (GET /api/auth/me)
```powershell
curl -b cookies.txt http://localhost:8080/api/auth/me
```
*Kết quả:* HTTP 200 OK `{"id":"...","email":"sinhvien1@test.com"}`. Server tự động nhận diện đúng sinh viên từ session!

### Bước 5: Đăng xuất (POST /api/auth/logout)
```powershell
curl -b cookies.txt -c cookies.txt -H "X-CSRF-TOKEN: <TOKEN>" -X POST http://localhost:8080/api/auth/logout
```
*Kết quả:* HTTP 204 No Content. Session bị hủy trên server.

### Bước 6: Kiểm tra lại /me sau khi đăng xuất
```powershell
curl -b cookies.txt http://localhost:8080/api/auth/me
```
*Kết quả:* HTTP 401 Unauthorized `{"code":"unauthenticated","message":"Bạn cần đăng nhập."}`.

---

## 6. DANH SÁCH KIỂM THỬ (TEST SUITE) VÀ KẾT QUẢ BÀN GIAO

Hệ thống đã được kiểm thử toàn diện với **32 test cases** bao gồm Unit Test, Spring MVC MockMvc Integration Test và PostgreSQL Integration Test:

```text
Results:
Tests run: 32, Failures: 0, Errors: 0, Skipped: 2 (Postgres integration tests opt-in via -Dauth.integration=true)
Total time: 18.664 s
BUILD SUCCESS
```

### Chi tiết các ca kiểm thử:
1. **`AuthControllerIntegrationTest` (17 tests):**
   - `getCsrfReturnsTokenAndSetsCookie`: Kiểm tra lấy CSRF và cấp cookie XSRF-TOKEN.
   - `registerSucceeds`: Đăng ký thành công trả 201 Created và không để lộ hash mật khẩu.
   - `registerRejectsMissingCsrf`: Từ chối request đăng ký thiếu CSRF với mã 403.
   - `registerRejectsInvalidCsrf`: Từ chối CSRF sai với mã 403.
   - `registerRejectsDuplicateEmail`: Bắt lỗi trùng email trả 409 Conflict (`email_already_exists`).
   - `registerRejectsInvalidEmailFormat`: Kiểm tra định dạng email không hợp lệ trả 400 Bad Request.
   - `registerRejectsShortPassword`: Kiểm tra mật khẩu dưới 6 ký tự trả 400 Bad Request.
   - `loginSucceeds`: Đăng nhập đúng trả 200 OK, cấp session và token mới.
   - `loginRejectsWrongPassword`: Mật khẩu sai trả 401 Unauthorized (`invalid_credentials`).
   - `loginRejectsBlankFields`: Để trống email/mật khẩu trả 400 Bad Request.
   - `loginRejectsMissingCsrf`: Từ chối đăng nhập thiếu CSRF với 403 Forbidden.
   - `meReturnsCurrentUser`: Gọi `/me` trả đúng `id` và `email` từ session.
   - `meRejectsUnauthenticated`: Gọi `/me` khi chưa đăng nhập trả 401 Unauthorized (`unauthenticated`).
   - `meIgnoresSpoofedUserIdParameter`: Client cố tình truyền `userId` giả mạo qua query param bị server bỏ qua hoàn toàn, chỉ lấy ID thực từ session.
   - `logoutSucceedsWithCsrf`: Đăng xuất thành công trả 204 No Content.
   - `logoutRejectsMissingCsrf`: Đăng xuất thiếu CSRF trả 403.
   - `protectedApiRequiresAuthentication`: Kiểm tra bảo vệ biên, gọi `/api/goals` khi chưa đăng nhập trả 401.
2. **`AuthServiceTest` (6 tests):**
   - Đăng ký băm mật khẩu bằng BCrypt, chuẩn hóa email, lưu vào DB và trả DTO không chứa mật khẩu.
   - Bắt lỗi và ném `DuplicateEmailException` khi trùng email.
   - Ủy quyền xác thực đúng qua `AuthenticationManager`.
   - Bắt lỗi sai thông tin đăng nhập và ném `BadCredentialsException`.
   - Trích xuất người dùng hiện tại an toàn từ `AppUserPrincipal`.
   - Trả null khi authentication không hợp lệ.
3. **`AuthUserRepositoryTest` (7 tests):**
   - Chuẩn hóa email chữ thường và câu lệnh SQL tìm kiếm trong `app_users`.
   - Xử lý email rỗng hoặc null an toàn.
   - Tìm kiếm theo `UUID id`.
   - Kiểm tra sự tồn tại của email (`EXISTS`).
   - Thêm người dùng mới (`RETURNING id, email, password_hash, created_at`).
   - Từ chối giá trị null với `NullPointerException`.
4. **`AuthPostgresIntegrationTest` (2 tests):**
   - Luồng trọn vẹn trên DB PostgreSQL thật (Register -> Login -> /me -> Logout -> /me 401).
   - Kiểm tra hai tài khoản A và B đăng nhập độc lập, không đọc được dữ liệu phiên của nhau.

---

## 7. LỆNH CHẠY KIỂM THỬ VÀ BUILD

```powershell
# Chạy toàn bộ Unit & MockMvc Integration Tests
mvn -f backend/pom.xml test

# Chạy đóng gói và kiểm chứng toàn bộ ứng dụng
mvn -f backend/pom.xml verify

# Chạy integration test trên PostgreSQL thật (yêu cầu docker compose up -d)
mvn -f backend/pom.xml test "-Dauth.integration=true"
```

---
*Bàn giao bởi DEV 1 — Sẵn sàng tích hợp cùng DEV 2 (Goal API) và DEV 3 (Frontend Auth).*
