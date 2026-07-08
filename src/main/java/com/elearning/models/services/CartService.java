package com.elearning.models.services;

import com.elearning.exceptions.BusinessException;
import com.elearning.models.dto.CartPricingResponse;
import com.elearning.models.entities.Cart;
import com.elearning.models.entities.CartItem;
import com.elearning.models.entities.DiscountType;
import com.elearning.models.entities.User;
import com.elearning.models.entities.Voucher;
import com.elearning.models.repositories.CartRepository;
import com.elearning.models.repositories.UserRepository;
import com.elearning.models.repositories.VoucherRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRepository cartRepository;
    private final VoucherRepository voucherRepository;
    private final UserRepository userRepository;

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    /**
     * Áp dụng mã giảm giá cho học viên đang đăng nhập (xác định qua email trong JWT).
     */
    @Transactional
    public CartPricingResponse applyVoucherByEmail(String email, String voucherCode) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(404, "Không tìm thấy người dùng"));
        return applyVoucher(user.getId(), voucherCode);
    }

    /**
     * Áp dụng mã giảm giá vào giỏ hàng của học viên và tính số tiền cuối cùng.
     * Thực hiện đúng 8 bước trong SRS: lấy giỏ, tính tổng gốc, validate mã,
     * kiểm tra điều kiện số khóa học, tính giảm theo %, áp mức chặn trần, ra số cuối.
     */
    @Transactional
    public CartPricingResponse applyVoucher(Long userId, String voucherCode) {

        // BƯỚC 1: Lấy giỏ hàng của học viên
        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(404, "Không tìm thấy giỏ hàng của người dùng"));

        List<CartItem> items = cart.getItems();
        if (items == null || items.isEmpty()) {
            throw new BusinessException(400, "Giỏ hàng trống");
        }

        // BƯỚC 2: Tính tổng gốc (originalTotal)
        BigDecimal originalTotal = BigDecimal.ZERO;
        for (CartItem item : items) {
            BigDecimal linePrice = item.getPriceAtAdded() != null ? item.getPriceAtAdded() : BigDecimal.ZERO;
            originalTotal = originalTotal.add(linePrice);
        }

        // BƯỚC 3: Lấy & kiểm tra tính hợp lệ của mã
        Voucher voucher = voucherRepository.findByCode(voucherCode)
                .orElseThrow(() -> new BusinessException(404, "Mã giảm giá không tồn tại"));

        if (!voucher.isActive()) {
            throw new BusinessException(400, "Mã giảm giá đã bị vô hiệu hóa");
        }

        LocalDateTime now = LocalDateTime.now();
        if (voucher.getStartDate() != null && now.isBefore(voucher.getStartDate())) {
            throw new BusinessException(400, "Mã giảm giá chưa đến thời gian hiệu lực");
        }
        if (voucher.getEndDate() != null && now.isAfter(voucher.getEndDate())) {
            throw new BusinessException(400, "Mã giảm giá đã hết hạn");
        }

        // BƯỚC 4: Kiểm tra điều kiện số lượng khóa học
        int courseCount = items.size();
        int minCourseCount = voucher.getMinCourseCount() != null ? voucher.getMinCourseCount() : 1;
        if (courseCount < minCourseCount) {
            throw new BusinessException(400,
                    "Mã chỉ áp dụng cho đơn hàng từ " + minCourseCount + " khóa học trở lên");
        }

        // BƯỚC 5: Tính số tiền giảm thô theo phần trăm (hoặc cố định)
        BigDecimal rawDiscount;
        if (voucher.getDiscountType() == DiscountType.PERCENTAGE) {
            rawDiscount = originalTotal
                    .multiply(voucher.getDiscountValue())
                    .divide(HUNDRED, 0, RoundingMode.HALF_UP);
        } else { // FIXED
            rawDiscount = voucher.getDiscountValue();
        }

        // BƯỚC 6: Áp mức chặn trần (cap)
        BigDecimal discountAmount = rawDiscount;
        if (voucher.getMaxDiscountAmount() != null
                && rawDiscount.compareTo(voucher.getMaxDiscountAmount()) > 0) {
            discountAmount = voucher.getMaxDiscountAmount();
        }

        // BƯỚC 7: Tính số tiền cuối cùng (không cho âm)
        BigDecimal finalTotal = originalTotal.subtract(discountAmount);
        if (finalTotal.compareTo(BigDecimal.ZERO) < 0) {
            finalTotal = BigDecimal.ZERO;
        }

        // BƯỚC 8: Lưu mã đã áp vào giỏ & trả kết quả
        cart.setAppliedVoucher(voucher);
        cartRepository.save(cart);

        return new CartPricingResponse(originalTotal, discountAmount, finalTotal, voucher.getCode());
    }
}
