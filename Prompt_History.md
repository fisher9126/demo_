# Prompt History — Dự án elearning-base 

Lịch sử prompt trong quá trình phát triển tính năng **Advanced Voucher** (mã giảm giá nâng cao) cho chiến dịch Black Friday. Mỗi chức năng gói gọn trong 1–2 prompt đúng trọng tâm.

---

## 1. Khảo sát codebase

> Phân tích repo `fisher9126/demo_`: stack công nghệ, kiến trúc phân lớp, entity và API hiện có. Đọc trực tiếp source, không phỏng đoán.

*→ Spring Boot 3.2.4 / Java 17 / JPA + MySQL / Security + JWT. Entity: `User`, `Course`. API: `/api/v1/auth`, `/api/v1/courses`.*

---

## 2. Đặc tả yêu cầu (SRS)

> Viết `SRS.md` cho tính năng voucher Black Friday: giảm 20% tổng đơn, trần 500.000đ, chỉ áp dụng khi giỏ có ≥ 2 khóa học. Dựa trên entity sẵn có, tự thiết kế data model cho "giỏ hàng" + "mã giảm giá" và đặc tả thuật toán tính tiền bằng pseudo-code (thứ tự tính, điều kiện, cách chặn trần).

*→ Sinh `SRS.md`: 3 entity mới (`Cart`, `CartItem`, `Voucher`) + enum `DiscountType`, bổ sung `price` cho `Course`, pseudo-code 8 bước + kịch bản chặn trần.*

---

## 3. Hiện thực Data Layer

> Code entity, repository, DTO theo SRS, chuẩn JPA Annotation.

*→ `Voucher/Cart/CartItem/DiscountType`, `Course.price`, `CartRepository`/`VoucherRepository`, `ApplyVoucherRequest`/`CartPricingResponse`.*

---

## 4. Hiện thực Business Logic

> Viết `CartService` hiện thực chính xác thuật toán tính tiền: cộng tổng giỏ, validate voucher, chặn điều kiện ≥ 2 khóa học, giảm theo %, áp trần 500.000, ra số tiền cuối. Toàn bộ dùng `BigDecimal`.

*→ `CartService.applyVoucher()` 8 bước; chặn trần `MIN(rawDiscount, maxDiscountAmount)`.*

---

## 5. Expose API

> Tạo `CartController` cho endpoint tính tiền, trả JSON gồm `originalTotal`, `discountAmount`, `finalTotal`. Lấy `userId` từ JWT.

*→ `POST /api/v1/cart/apply-voucher`, bọc qua `ApiResponse`.*

---

## 6. Kiểm thử

> Viết unit test cho `CartService` phủ các kịch bản: chặn trần 500.000, giảm dưới trần, 1 khóa học (reject), mã không tồn tại.

*→ `CartServiceTest` (Mockito, 4 case) + `TESTING.md` (hướng dẫn chạy, curl, seeder).*

---

## 7. Hardening & Exception Handling

> Rà soát toàn bộ logic (syntax, import, tránh stack trace ra console). Cập nhật `GlobalExceptionHandler` xử lý nghiệp vụ thất bại chuẩn mực — voucher sai/không đủ điều kiện phải throw `BusinessException` kèm message rõ ràng.

*→ Bổ sung handler `AccessDeniedException` (403), `HttpMessageNotReadableException` (400); handler chung ẩn chi tiết nội bộ.*

---

## Tổng kết file đã tạo/sửa

| File | Trạng thái |
|---|---|
| `SRS.md` | Mới |
| `models/entities/DiscountType.java` | Mới |
| `models/entities/Voucher.java` | Mới |
| `models/entities/Cart.java` | Mới |
| `models/entities/CartItem.java` | Mới |
| `models/entities/Course.java` | Sửa (thêm `price`) |
| `models/repositories/CartRepository.java` | Mới |
| `models/repositories/VoucherRepository.java` | Mới |
| `models/dto/ApplyVoucherRequest.java` | Mới |
| `models/dto/CartPricingResponse.java` | Mới |
| `models/services/CartService.java` | Mới |
| `controllers/CartController.java` | Mới |
| `advice/GlobalExceptionHandler.java` | Sửa (bổ sung handler) |
| `test/.../CartServiceTest.java` | Mới |
