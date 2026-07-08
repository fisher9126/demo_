# SRS — Hệ thống Mã Giảm Giá Nâng Cao (Advanced Voucher) cho Chiến dịch Black Friday

**Dự án:** elearning-base
**Phiên bản:** 1.0
**Ngày:** 08/07/2026
**Phạm vi:** Bổ sung tính năng giỏ hàng + mã giảm giá có điều kiện cho hệ thống e-learning hiện có.

---

## 1. Bối cảnh & Mục tiêu

### 1.1. Yêu cầu nghiệp vụ (từ email khách hàng)
Khách hàng cần một hệ thống mã giảm giá nâng cao để chạy chiến dịch Black Friday. Mã giảm giá không chỉ là giảm phần trăm thông thường mà phải hỗ trợ các quy tắc phức tạp:

1. **Giảm theo phần trăm:** giảm 20% trên tổng giá trị đơn hàng.
2. **Mức chặn trần (cap):** số tiền được giảm tối đa không vượt quá 500.000 VNĐ. Nếu 20% vượt quá 500.000 thì chỉ giảm đúng 500.000.
3. **Điều kiện áp dụng:** mã chỉ dùng được khi học viên đăng ký **từ 2 khóa học trở lên** trong giỏ hàng. Nếu chỉ có 1 khóa học thì **báo lỗi** và không cho áp mã.

### 1.2. Mục tiêu kỹ thuật
- Mô hình hóa "giỏ hàng" và "mã giảm giá" thành entity JPA, khớp với kiến trúc phân lớp có sẵn (`entities → repositories → services → controllers`).
- Xây dựng API tính tiền giỏ hàng trả về số tiền cuối cùng sau khi áp mã.
- Tận dụng cơ chế xử lý lỗi tập trung có sẵn (`BusinessException` + `GlobalExceptionHandler` + `ApiResponse`).

---

## 2. Hiện trạng Base Code

### 2.1. Entity đang có
| Entity | Thuộc tính | Ghi chú |
|---|---|---|
| `User` | `id`, `fullName`, `email`, `password`, `role` (STUDENT / INSTRUCTOR) | Học viên và giảng viên |
| `Course` | `id`, `title`, `description`, `instructor` (ManyToOne → User) | **Chưa có trường giá** |

### 2.2. Thành phần dùng lại
- `ApiResponse<T>` — bọc response thống nhất (`success(data, message)`).
- `BusinessException` — ném lỗi nghiệp vụ, được `GlobalExceptionHandler` bắt và trả về response chuẩn.
- `SecurityConfig` — mọi endpoint ngoài `/api/v1/auth/**` đều yêu cầu JWT; giỏ hàng gắn với học viên đăng nhập.

### 2.3. Điều kiện tiên quyết cần bổ sung
`Course` hiện **thiếu trường giá**, nên không thể tính tiền. Bắt buộc thêm:
```java
private java.math.BigDecimal price; // Đơn vị: VNĐ
```
> Dùng `BigDecimal` thay cho `double` để tránh sai số dấu phẩy động khi tính tiền.

---

## 3. Thiết kế Cấu trúc Dữ liệu (Entity mới)

### 3.1. Sơ đồ quan hệ
```
User (1) ────< (n) Cart (1) ────< (n) CartItem (n) >──── (1) Course
                    │
                    └──> (0..1) Voucher   (mã đang áp dụng, có thể null)
```

### 3.2. Entity `Cart` (Giỏ hàng)
Mỗi học viên có một giỏ hàng chứa các khóa học muốn mua.

| Thuộc tính | Kiểu | Mô tả |
|---|---|---|
| `id` | `Long` | Khóa chính, tự tăng |
| `user` | `@ManyToOne User` | Chủ sở hữu giỏ hàng |
| `items` | `@OneToMany List<CartItem>` | Danh sách khóa học trong giỏ |
| `appliedVoucher` | `@ManyToOne Voucher` (nullable) | Mã giảm giá đang áp (nếu có) |
| `createdAt` | `LocalDateTime` | Thời điểm tạo |

```java
@Entity
@Table(name = "carts")
@Data @NoArgsConstructor @AllArgsConstructor
public class Cart {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne @JoinColumn(name = "user_id")
    private User user;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CartItem> items = new ArrayList<>();

    @ManyToOne @JoinColumn(name = "voucher_id")
    private Voucher appliedVoucher;

    private LocalDateTime createdAt;
}
```

### 3.3. Entity `CartItem` (Dòng khóa học trong giỏ)
Tách riêng để đếm số khóa học (kiểm tra điều kiện ≥ 2) và cộng dồn giá.

| Thuộc tính | Kiểu | Mô tả |
|---|---|---|
| `id` | `Long` | Khóa chính |
| `cart` | `@ManyToOne Cart` | Giỏ hàng chứa dòng này |
| `course` | `@ManyToOne Course` | Khóa học được thêm |
| `priceAtAdded` | `BigDecimal` | Giá tại thời điểm thêm (chốt giá, tránh biến động sau này) |

```java
@Entity
@Table(name = "cart_items")
@Data @NoArgsConstructor @AllArgsConstructor
public class CartItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne @JoinColumn(name = "cart_id")
    private Cart cart;

    @ManyToOne @JoinColumn(name = "course_id")
    private Course course;

    private BigDecimal priceAtAdded;
}
```

### 3.4. Entity `Voucher` (Mã giảm giá)
Lưu quy tắc giảm giá. Thiết kế tổng quát để dễ mở rộng ngoài chiến dịch Black Friday.

| Thuộc tính | Kiểu | Mô tả | Giá trị ví dụ (Black Friday) |
|---|---|---|---|
| `id` | `Long` | Khóa chính | |
| `code` | `String` (unique) | Mã nhập vào | `BF2026` |
| `discountType` | `Enum {PERCENTAGE, FIXED}` | Kiểu giảm | `PERCENTAGE` |
| `discountValue` | `BigDecimal` | Trị giảm (% hoặc số tiền) | `20` (nghĩa là 20%) |
| `maxDiscountAmount` | `BigDecimal` (nullable) | Mức chặn trần số tiền giảm | `500000` |
| `minCourseCount` | `Integer` | Số khóa học tối thiểu để áp dụng | `2` |
| `active` | `boolean` | Trạng thái bật/tắt | `true` |
| `startDate` | `LocalDateTime` | Ngày bắt đầu hiệu lực | |
| `endDate` | `LocalDateTime` | Ngày hết hạn | |

```java
public enum DiscountType { PERCENTAGE, FIXED }

@Entity
@Table(name = "vouchers")
@Data @NoArgsConstructor @AllArgsConstructor
public class Voucher {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String code;

    @Enumerated(EnumType.STRING)
    private DiscountType discountType;

    private BigDecimal discountValue;      // 20 => 20%
    private BigDecimal maxDiscountAmount;  // 500000 => trần 500k, null = không giới hạn
    private Integer minCourseCount;        // 2 => cần >= 2 khóa học
    private boolean active;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
}
```

### 3.5. Lý do thiết kế
- **Tách `Cart` và `CartItem`:** đếm `items.size()` để kiểm tra điều kiện "≥ 2 khóa học", đồng thời cộng dồn `priceAtAdded` cho tổng đơn.
- **`priceAtAdded`:** chốt giá lúc thêm vào giỏ để tránh trường hợp giảng viên đổi giá khóa học giữa chừng.
- **`Voucher` tổng quát hóa các quy tắc** thành cột dữ liệu (`discountValue`, `maxDiscountAmount`, `minCourseCount`) thay vì hard-code, giúp tạo nhiều loại mã khác nhau mà không sửa code.
- **Tất cả tiền tệ dùng `BigDecimal`** để đảm bảo chính xác.

---

## 4. Đặc tả Thuật toán Tính Tiền (Pricing Logic)

### 4.1. API
```
POST /api/v1/cart/apply-voucher
Body: { "voucherCode": "BF2026" }
Header: Authorization: Bearer <JWT>
```
Trả về (qua `ApiResponse`): tổng gốc, số tiền giảm, số tiền phải trả cuối cùng.

### 4.2. Dữ liệu đầu ra
```json
{
  "success": true,
  "message": "Áp dụng mã giảm giá thành công",
  "data": {
    "originalTotal": 3000000,
    "discountAmount": 500000,
    "finalTotal": 2500000,
    "appliedVoucherCode": "BF2026"
  }
}
```

### 4.3. Thứ tự tính toán & điều kiện kiểm tra (pseudo-code)

```
FUNCTION applyVoucher(userId, voucherCode):

    // ----- BƯỚC 1: Lấy giỏ hàng của học viên -----
    cart = cartRepository.findByUserId(userId)
    IF cart IS NULL OR cart.items IS EMPTY:
        THROW BusinessException("Giỏ hàng trống")

    // ----- BƯỚC 2: Tính tổng gốc (originalTotal) -----
    originalTotal = 0
    FOR EACH item IN cart.items:
        originalTotal = originalTotal + item.priceAtAdded
    // originalTotal là tổng tiền chưa giảm

    // ----- BƯỚC 3: Lấy & kiểm tra tính hợp lệ của mã -----
    voucher = voucherRepository.findByCode(voucherCode)
    IF voucher IS NULL:
        THROW BusinessException("Mã giảm giá không tồn tại")
    IF voucher.active == false:
        THROW BusinessException("Mã giảm giá đã bị vô hiệu hóa")
    now = currentDateTime()
    IF now < voucher.startDate OR now > voucher.endDate:
        THROW BusinessException("Mã giảm giá không trong thời gian hiệu lực")

    // ----- BƯỚC 4: Kiểm tra điều kiện số lượng khóa học -----
    courseCount = cart.items.size()
    IF courseCount < voucher.minCourseCount:
        THROW BusinessException(
            "Mã chỉ áp dụng cho đơn từ " + voucher.minCourseCount + " khóa học trở lên"
        )
    // => Nếu chỉ mua 1 khóa học, dừng tại đây và báo lỗi

    // ----- BƯỚC 5: Tính số tiền giảm thô theo phần trăm -----
    IF voucher.discountType == PERCENTAGE:
        rawDiscount = originalTotal * (voucher.discountValue / 100)
        // Ví dụ: 3.000.000 * (20 / 100) = 600.000
    ELSE IF voucher.discountType == FIXED:
        rawDiscount = voucher.discountValue

    // ----- BƯỚC 6: Áp mức chặn trần (cap) -----
    IF voucher.maxDiscountAmount IS NOT NULL:
        discountAmount = MIN(rawDiscount, voucher.maxDiscountAmount)
        // Ví dụ: MIN(600.000, 500.000) = 500.000
    ELSE:
        discountAmount = rawDiscount

    // ----- BƯỚC 7: Tính số tiền cuối cùng -----
    finalTotal = originalTotal - discountAmount
    IF finalTotal < 0:
        finalTotal = 0   // Bảo vệ: không cho âm

    // ----- BƯỚC 8: Lưu mã đã áp vào giỏ & trả kết quả -----
    cart.appliedVoucher = voucher
    cartRepository.save(cart)

    RETURN {
        originalTotal:      originalTotal,
        discountAmount:     discountAmount,
        finalTotal:         finalTotal,
        appliedVoucherCode: voucher.code
    }
END FUNCTION
```

### 4.4. Ví dụ minh họa (kịch bản Black Friday)

**Kịch bản A — hợp lệ, chạm trần:**
- Giỏ có 3 khóa học: 1.000.000 + 1.000.000 + 1.000.000 = **3.000.000 VNĐ**
- 20% × 3.000.000 = 600.000 → vượt trần 500.000 → **giảm 500.000**
- Phải trả: 3.000.000 − 500.000 = **2.500.000 VNĐ**

**Kịch bản B — hợp lệ, chưa chạm trần:**
- Giỏ có 2 khóa học: 800.000 + 700.000 = **1.500.000 VNĐ**
- 20% × 1.500.000 = 300.000 → dưới trần 500.000 → **giảm 300.000**
- Phải trả: 1.500.000 − 300.000 = **1.200.000 VNĐ**

**Kịch bản C — không hợp lệ (1 khóa học):**
- Giỏ có 1 khóa học: 1.200.000 VNĐ
- `courseCount (1) < minCourseCount (2)` → **BƯỚC 4 ném lỗi**
- Trả về lỗi: *"Mã chỉ áp dụng cho đơn từ 2 khóa học trở lên"* (HTTP 400 qua `GlobalExceptionHandler`).

### 4.5. Bảng tổng hợp quy tắc
| Bước | Kiểm tra / Tính toán | Hành động khi vi phạm |
|---|---|---|
| 1 | Giỏ hàng tồn tại & không rỗng | Báo lỗi "Giỏ hàng trống" |
| 3 | Mã tồn tại, đang active, còn hiệu lực | Báo lỗi tương ứng |
| 4 | `số khóa học ≥ minCourseCount` | Báo lỗi điều kiện số lượng |
| 5 | `rawDiscount = total × %` | — |
| 6 | `discount = MIN(raw, cap)` | Chặn trần |
| 7 | `final = total − discount` (≥ 0) | — |

---

## 5. Thành phần cần bổ sung vào code

| Lớp | File mới | Trách nhiệm |
|---|---|---|
| Entity | `Cart`, `CartItem`, `Voucher`, enum `DiscountType` | Mô hình dữ liệu |
| Entity (sửa) | `Course` (+ trường `price`) | Cho phép tính tiền |
| Repository | `CartRepository`, `VoucherRepository` | Truy vấn dữ liệu |
| DTO | `ApplyVoucherRequest`, `CartPricingResponse` | Vào/ra API |
| Service | `CartService` | Chứa thuật toán Mục 4 |
| Controller | `CartController` | Expose `POST /api/v1/cart/apply-voucher` |
| Exception | Dùng lại `BusinessException` có sẵn | Ném lỗi nghiệp vụ |

---

## 6. Ghi chú kỹ thuật
- Toàn bộ phép tính tiền dùng `BigDecimal` với `RoundingMode.HALF_UP` khi cần làm tròn.
- Endpoint nằm ngoài `/api/v1/auth/**` nên **bắt buộc JWT**; `userId` lấy từ token đã xác thực, không nhận từ client.
- Các thông báo lỗi ở Mục 4 được `GlobalExceptionHandler` chuyển thành `ApiResponse` với `success = false`.
